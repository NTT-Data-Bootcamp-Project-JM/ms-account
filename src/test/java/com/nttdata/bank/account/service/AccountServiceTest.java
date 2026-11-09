package com.nttdata.bank.account.service;

import com.nttdata.bank.account.config.BankRulesProperties;
import com.nttdata.bank.account.infrastructure.redis.ProductCatalog;
import com.nttdata.bank.account.domain.model.AccountDocument;
import com.nttdata.bank.account.domain.repository.AccountRepository;
import com.nttdata.bank.account.domain.model.DebitCardDocument;
import com.nttdata.bank.account.domain.repository.DebitCardRepository;
import com.nttdata.bank.account.domain.model.MovementDocument;
import com.nttdata.bank.account.domain.repository.MovementRepository;
import com.nttdata.bank.account.infrastructure.webclient.CreditWebClient;
import com.nttdata.bank.account.infrastructure.webclient.CustomerWebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Account use cases with mocked repositories and a real fee configuration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private MovementRepository movements;

    @Mock
    private DebitCardRepository cards;

    @Mock
    private CustomerWebClient customers;

    @Mock
    private CreditWebClient credits;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    @Mock
    private HashOperations<String, Object, Object> hashes;

    private final Map<String, AccountDocument> accountStore = new LinkedHashMap<>();
    private final Map<String, MovementDocument> movementStore = new LinkedHashMap<>();
    private final Map<String, DebitCardDocument> cardStore = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountStore.clear();
        movementStore.clear();
        cardStore.clear();
        BankRulesProperties rules = new BankRulesProperties();
        rules.getChecking().setMaintenanceFee(new BigDecimal("12.00"));
        rules.getSavings().setMaxMovementsPerMonth(10);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForHash()).thenReturn(hashes);
        service = new AccountService(accounts, movements, cards, customers, credits, new ProductCatalog(redis, rules));

        when(accounts.save(any())).thenAnswer(invocation -> keepAccount(invocation.getArgument(0)));
        when(accounts.findById(any())).thenAnswer(invocation -> Optional.ofNullable(accountStore.get(invocation.getArgument(0))));
        when(accounts.findAll()).thenAnswer(invocation -> new ArrayList<>(accountStore.values()));
        when(accounts.countByPrimaryCustomerIdAndAccountTypeAndStatus(any(), any(), any())).thenReturn(0L);
        doAnswer(invocation -> {
            accountStore.remove(((AccountDocument) invocation.getArgument(0)).getId());
            return null;
        }).when(accounts).delete(any());

        when(movements.save(any())).thenAnswer(invocation -> {
            MovementDocument movement = invocation.getArgument(0);
            if (movement.getId() == null) {
                movement.setId("mov-" + sequence.incrementAndGet());
            }
            movementStore.put(movement.getId(), movement);
            return movement;
        });
        when(movements.findById(any())).thenAnswer(invocation -> Optional.ofNullable(movementStore.get(invocation.getArgument(0))));
        when(movements.findByOperationId(any())).thenAnswer(invocation -> movementStore.values().stream()
                .filter(movement -> invocation.getArgument(0).equals(movement.getOperationId()))
                .findFirst());
        when(movements.findByAccountIdOrderByCreatedAtAsc(any())).thenAnswer(invocation -> movementStore.values().stream()
                .filter(movement -> invocation.getArgument(0).equals(movement.getAccountId()))
                .toList());
        when(movements.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any())).thenAnswer(invocation ->
                movementStore.values().stream()
                        .filter(movement -> invocation.getArgument(0).equals(movement.getAccountId()))
                        .toList());
        when(movements.countByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any())).thenReturn(0L);
        doAnswer(invocation -> {
            movementStore.remove(((MovementDocument) invocation.getArgument(0)).getId());
            return null;
        }).when(movements).delete(any());

        when(cards.save(any())).thenAnswer(invocation -> {
            DebitCardDocument card = invocation.getArgument(0);
            if (card.getId() == null) {
                card.setId("card-" + sequence.incrementAndGet());
            }
            cardStore.put(card.getId(), card);
            return card;
        });
        when(cards.findById(any())).thenAnswer(invocation -> Optional.ofNullable(cardStore.get(invocation.getArgument(0))));
        when(cards.findAll()).thenAnswer(invocation -> new ArrayList<>(cardStore.values()));
        when(cards.findByCardNumber(any())).thenAnswer(invocation -> cardStore.values().stream()
                .filter(card -> invocation.getArgument(0).equals(card.getCardNumber()))
                .findFirst());
        when(cards.findByMainAccountId(any())).thenAnswer(invocation -> cardStore.values().stream()
                .filter(card -> invocation.getArgument(0).equals(card.getMainAccountId()))
                .toList());
        doAnswer(invocation -> {
            cardStore.remove(((DebitCardDocument) invocation.getArgument(0)).getId());
            return null;
        }).when(cards).delete(any());

        customer("ana", "PERSONAL", "STANDARD", false, false);
        customer("luis", "PERSONAL", "STANDARD", false, false);
        customer("vip", "PERSONAL", "VIP", false, true);
        customer("biz", "BUSINESS", "STANDARD", false, false);
        customer("late", "PERSONAL", "STANDARD", true, false);
    }

    @Test
    void opensDepositsWithdrawsAndReportsASavingsAccount() {
        AccountDocument opened = service.open("ana", "SAVINGS", List.of(), List.of(), null, new BigDecimal("20")).blockingGet();

        assertEquals(0, new BigDecimal("20.00").compareTo(opened.getBalance()));
        assertEquals(1, service.findAll().blockingGet().size());
        assertEquals(opened.getId(), service.find(opened.getId()).blockingGet().getId());

        AccountDocument deposited = service.deposit(opened.getId(), new BigDecimal("5")).blockingGet();
        AccountDocument withdrawn = service.withdraw(deposited.getId(), new BigDecimal("10")).blockingGet();
        assertEquals(0, new BigDecimal("15.00").compareTo(withdrawn.getBalance()));

        List<MovementDocument> ledger = service.movements(opened.getId()).blockingGet();
        assertEquals(3, ledger.size());
        MovementDocument updated = service.updateMovement(ledger.get(0).getId(), "Opening note").blockingGet();
        assertEquals("Opening note", updated.getDescription());
        assertEquals(updated.getId(), service.findMovement(updated.getId()).blockingGet().getId());
        assertEquals(3, service.report(opened.getId(), Instant.EPOCH, Instant.now()).blockingGet().size());

        MovementDocument withdrawal = ledger.get(2);
        assertTrue(service.deleteMovement(withdrawal.getId()).blockingGet());
        assertEquals(0, new BigDecimal("25.00").compareTo(service.find(opened.getId()).blockingGet().getBalance()));
    }

    @Test
    void transfersBetweenTwoCustomers() {
        AccountDocument source = service.open("ana", "SAVINGS", List.of(), List.of(), null, new BigDecimal("50")).blockingGet();
        AccountDocument target = service.open("luis", "SAVINGS", List.of(), List.of(), null, new BigDecimal("50")).blockingGet();

        AccountDocument after = service.transfer(source.getId(), target.getId(), new BigDecimal("10")).blockingGet();

        assertEquals(0, new BigDecimal("40.00").compareTo(after.getBalance()));
        assertEquals(0, new BigDecimal("60.00").compareTo(service.find(target.getId()).blockingGet().getBalance()));
    }

    @Test
    void rejectsTheSameAccountAsSourceAndTarget() {
        AccountDocument account = service.open("ana", "SAVINGS", List.of(), List.of(), null, new BigDecimal("10")).blockingGet();

        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(account.getId(), account.getId(), BigDecimal.ONE).blockingGet());
    }

    @Test
    void rejectsAnOpeningWhenTheCustomerIsOverdue() {
        assertThrows(IllegalArgumentException.class,
                () -> service.open("late", "SAVINGS", List.of(), List.of(), null, BigDecimal.TEN).blockingGet());
    }

    @Test
    void chargesCheckingMaintenanceAndUpdatesBusinessHolders() {
        AccountDocument account = service.open("biz", "CHECKING", List.of("biz"), List.of("biz"), null, new BigDecimal("100")).blockingGet();
        AccountDocument updated = service.update(account.getId(), List.of("biz", "ana"), List.of("biz")).blockingGet();
        AccountDocument charged = service.chargeMaintenance(updated.getId()).blockingGet();

        assertEquals(List.of("biz", "ana"), charged.getHolderIds());
        assertEquals(0, new BigDecimal("88.00").compareTo(charged.getBalance()));
    }

    @Test
    void chargesTheVipShortfallAndLeavesAStandardAccountUntouched() {
        AccountDocument vip = service.open("vip", "SAVINGS", List.of(), List.of(), null, new BigDecimal("20")).blockingGet();
        AccountDocument penalized = service.evaluateAverage(vip.getId()).blockingGet();
        AccountDocument standard = service.open("ana", "SAVINGS", List.of(), List.of(), null, new BigDecimal("20")).blockingGet();

        assertEquals(0, new BigDecimal("10.00").compareTo(penalized.getBalance()));
        assertEquals(0, new BigDecimal("20.00").compareTo(service.evaluateAverage(standard.getId()).blockingGet().getBalance()));
    }

    @Test
    void debitCardPaysTheMainAccountAndCanBeReplaced() {
        AccountDocument main = service.open("ana", "SAVINGS", List.of(), List.of(), null, new BigDecimal("40")).blockingGet();
        AccountDocument other = service.open("ana", "FIXED_TERM", List.of(), List.of(), 15, null).blockingGet();
        DebitCardDocument card = service.createCard("ana", main.getId()).blockingGet();

        assertEquals(1, service.cards().blockingGet().size());
        AccountDocument paid = service.payWithCard(card.getId(), new BigDecimal("8")).blockingGet();
        assertEquals(0, new BigDecimal("32.00").compareTo(paid.getBalance()));
        assertEquals(1, service.lastCardMovements(card.getId()).blockingGet().size());

        AccountDocument debited = service.debitForCreditPayment(card.getId(), new BigDecimal("2"), "op-credit");
        assertEquals(0, new BigDecimal("30.00").compareTo(debited.getBalance()));

        DebitCardDocument moved = service.updateCard(card.getId(), other.getId()).blockingGet();
        assertEquals(other.getId(), service.card(moved.getId()).blockingGet().getMainAccountId());
        assertTrue(service.deleteCard(moved.getId()).blockingGet());
    }

    @Test
    void walletDebitIsAppliedOnce() {
        AccountDocument main = service.open("ana", "SAVINGS", List.of(), List.of(), null, new BigDecimal("40")).blockingGet();
        DebitCardDocument card = service.createCard("ana", main.getId()).blockingGet();

        service.applyWalletOperation(card.getCardNumber(), new BigDecimal("5"), "DEBIT", "op-1");
        service.applyWalletOperation(card.getCardNumber(), new BigDecimal("5"), "DEBIT", "op-1");

        assertEquals(0, new BigDecimal("35.00").compareTo(service.find(main.getId()).blockingGet().getBalance()));
    }

    @Test
    void deleteRequiresAZeroBalanceAndNoCard() {
        AccountDocument funded = service.open("ana", "SAVINGS", List.of(), List.of(), null, new BigDecimal("10")).blockingGet();
        assertThrows(IllegalArgumentException.class, () -> service.delete(funded.getId()).blockingGet());

        AccountDocument empty = service.open("ana", "FIXED_TERM", List.of(), List.of(), 15, null).blockingGet();
        service.createCard("ana", empty.getId()).blockingGet();
        assertThrows(IllegalArgumentException.class, () -> service.delete(empty.getId()).blockingGet());
    }

    @Test
    void deletesAnEmptyFixedTermAccount() {
        AccountDocument account = service.open("ana", "FIXED_TERM", List.of(), List.of(), 15, null).blockingGet();

        assertTrue(service.delete(account.getId()).blockingGet());
    }

    private AccountDocument keepAccount(AccountDocument account) {
        if (account.getId() == null) {
            account.setId("acc-" + sequence.incrementAndGet());
        }
        accountStore.put(account.getId(), account);
        return account;
    }

    private void customer(String id, String type, String profile, boolean overdue, boolean card) {
        when(customers.customer(id)).thenReturn(new CustomerWebClient.CustomerView(id, type, profile));
        when(credits.overdue(id)).thenReturn(overdue);
        when(credits.hasCreditCard(id)).thenReturn(card);
    }
}

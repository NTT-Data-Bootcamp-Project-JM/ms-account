package com.nttdata.bank.account.application;

import com.nttdata.bank.account.config.BankRules;
import com.nttdata.bank.account.config.ProductCatalog;
import com.nttdata.bank.account.domain.AccountDocument;
import com.nttdata.bank.account.domain.AccountRepository;
import com.nttdata.bank.account.domain.AccountRules;
import com.nttdata.bank.account.domain.DebitCardDocument;
import com.nttdata.bank.account.domain.DebitCardRepository;
import com.nttdata.bank.account.domain.MovementDocument;
import com.nttdata.bank.account.domain.MovementRepository;
import com.nttdata.bank.account.infrastructure.BankDirectory;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Account lifecycle, movements, same-bank transfers and debit cards.
 * Strategy selection for savings, checking and fixed-term lives in {@link AccountRules}.
 */
@Service
public class AccountService {

    private static final String ACTIVE = "ACTIVE";
    private static final Set<String> COUNTED = Set.of("DEPOSIT", "WITHDRAWAL", "TRANSFER_IN", "TRANSFER_OUT", "DEBIT_CARD");

    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final DebitCardRepository cards;
    private final BankDirectory directory;
    private final ProductCatalog catalog;

    /**
     * @param accounts account collection
     * @param movements ledger
     * @param cards debit cards
     * @param directory customer and credit lookups
     * @param catalog account types and product rules stored in Redis
     */
    public AccountService(AccountRepository accounts, MovementRepository movements, DebitCardRepository cards,
                          BankDirectory directory, ProductCatalog catalog) {
        this.accounts = accounts;
        this.movements = movements;
        this.cards = cards;
        this.directory = directory;
        this.catalog = catalog;
    }

    /**
     * Opens an account and applies the initial deposit when it meets the minimum opening amount.
     *
     * @param customerId primary owner
     * @param accountType SAVINGS, CHECKING or FIXED_TERM
     * @param holderIds holders; a business account needs one or more
     * @param signerIds authorized signers; may be empty
     * @param fixedTermDay day of month for a fixed-term account
     * @param initialDeposit opening deposit
     * @return created account
     */
    public Single<AccountDocument> open(String customerId, String accountType, List<String> holderIds,
                                        List<String> signerIds, Integer fixedTermDay, BigDecimal initialDeposit) {
        return Single.fromCallable(() -> {
            BankDirectory.CustomerView customer = directory.customer(customerId);
            boolean overdue = directory.overdue(customerId);
            boolean hasCard = directory.hasCreditCard(customerId);
            long existing = accounts.countByPrimaryCustomerIdAndAccountTypeAndStatus(customerId, accountType, ACTIVE);
            AccountRules.checkOpening(customer.customerType(), customer.profile(), accountType, existing, hasCard, overdue);
            BankRules.Product product = product(accountType);
            BigDecimal deposit = initialDeposit == null ? BigDecimal.ZERO : initialDeposit;
            if (deposit.compareTo(product.getMinimumOpeningAmount()) < 0) {
                throw new IllegalArgumentException("The opening amount is below the configured minimum");
            }
            if ("FIXED_TERM".equals(accountType) && (fixedTermDay == null || fixedTermDay < 1 || fixedTermDay > 28)) {
                throw new IllegalArgumentException("A fixed-term account needs a movement day between 1 and 28");
            }
            AccountDocument account = new AccountDocument();
            account.setAccountNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 14));
            account.setAccountType(accountType);
            account.setPrimaryCustomerId(customerId);
            account.setCustomerType(customer.customerType());
            account.setProfile(customer.profile());
            account.setHolderIds(holders(customer, holderIds));
            account.setSignerIds("BUSINESS".equals(customer.customerType()) ? signerIds : List.of());
            account.setMaxMovementsPerMonth(product.getMaxMovementsPerMonth());
            account.setFixedTermDay(fixedTermDay);
            account.setMaintenanceFee(maintenance(accountType, customer.profile(), product));
            if ("SAVINGS".equals(accountType) && "VIP".equals(customer.profile())) {
                account.setMinimumDailyAverage(catalog.minimumDailyAverage());
            }
            account.setStatus(ACTIVE);
            account.setBalance(BigDecimal.ZERO);
            accounts.save(account);
            if (deposit.signum() > 0) {
                apply(account, "DEPOSIT", deposit, null, "Opening deposit");
            }
            return accounts.findById(account.getId()).orElseThrow();
        }).subscribeOn(Schedulers.io());
    }

    /**
     * @return all accounts
     */
    public Single<List<AccountDocument>> findAll() {
        return Single.fromCallable(accounts::findAll).subscribeOn(Schedulers.io());
    }

    /**
     * @param id account identifier
     * @return the account, including its current balance
     */
    public Single<AccountDocument> find(String id) {
        return Single.fromCallable(() -> load(id)).subscribeOn(Schedulers.io());
    }

    /**
     * Updates holders and signers. The account type stays unchanged.
     *
     * @param id account identifier
     * @param holderIds holders
     * @param signerIds signers
     * @return updated account
     */
    public Single<AccountDocument> update(String id, List<String> holderIds, List<String> signerIds) {
        return Single.fromCallable(() -> {
            AccountDocument account = load(id);
            if ("BUSINESS".equals(account.getCustomerType())) {
                if (holderIds == null || holderIds.isEmpty() || !holderIds.contains(account.getPrimaryCustomerId())) {
                    throw new IllegalArgumentException("A business account needs its primary holder among the holders");
                }
                account.setHolderIds(holderIds);
                account.setSignerIds(signerIds == null ? List.of() : signerIds);
            }
            return accounts.save(account);
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Deletes an account only when its balance is zero and no debit card uses it.
     *
     * @param id account identifier
     * @return completion
     */
    public Single<Boolean> delete(String id) {
        return Single.fromCallable(() -> {
            AccountDocument account = load(id);
            if (account.getBalance().signum() != 0 || !cards.findByMainAccountId(id).isEmpty()) {
                throw new IllegalArgumentException("The account still has a balance or a debit card");
            }
            accounts.delete(account);
            return true;
        }).subscribeOn(Schedulers.io());
    }

    /**
     * @param id account identifier
     * @param amount deposit
     * @return updated account
     */
    public Single<AccountDocument> deposit(String id, BigDecimal amount) {
        return Single.fromCallable(() -> apply(load(id), "DEPOSIT", amount, null, "Deposit")).subscribeOn(Schedulers.io());
    }

    /**
     * @param id account identifier
     * @param amount withdrawal
     * @return updated account
     */
    public Single<AccountDocument> withdraw(String id, BigDecimal amount) {
        return Single.fromCallable(() -> apply(load(id), "WITHDRAWAL", amount, null, "Withdrawal")).subscribeOn(Schedulers.io());
    }

    /**
     * Moves money between two accounts of this bank, whether or not they share a customer.
     *
     * @param sourceId source account
     * @param targetId target account
     * @param amount transferred amount
     * @return source account after the transfer
     */
    public Single<AccountDocument> transfer(String sourceId, String targetId, BigDecimal amount) {
        return Single.fromCallable(() -> {
            if (sourceId.equals(targetId)) {
                throw new IllegalArgumentException("The source and the target must be different accounts");
            }
            AccountDocument source = load(sourceId);
            AccountDocument target = load(targetId);
            apply(source, "TRANSFER_OUT", amount, null, "Transfer to " + target.getAccountNumber());
            apply(load(targetId), "TRANSFER_IN", amount, null, "Transfer from " + source.getAccountNumber());
            return load(sourceId);
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Charges the configured maintenance fee.
     *
     * @param id account identifier
     * @return updated account
     */
    public Single<AccountDocument> chargeMaintenance(String id) {
        return Single.fromCallable(() -> {
            AccountDocument account = load(id);
            if (account.getMaintenanceFee().signum() == 0) {
                return account;
            }
            return apply(account, "MAINTENANCE_FEE", account.getMaintenanceFee(), null, "Maintenance fee");
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Charges the VIP penalty when the current balance is below the required daily average.
     * The balance is used as the available daily sample for the review.
     *
     * @param id account identifier
     * @return updated account
     */
    public Single<AccountDocument> evaluateAverage(String id) {
        return Single.fromCallable(() -> {
            AccountDocument account = load(id);
            if (account.getMinimumDailyAverage() == null) {
                return account;
            }
            if (account.getBalance().compareTo(account.getMinimumDailyAverage()) < 0) {
                return apply(account, "COMMISSION", catalog.shortfallPenalty(), null, "VIP average shortfall");
            }
            return account;
        }).subscribeOn(Schedulers.io());
    }

    /**
     * @param customerId card owner
     * @param mainAccountId main account
     * @return created debit card
     */
    public Single<DebitCardDocument> createCard(String customerId, String mainAccountId) {
        return Single.fromCallable(() -> {
            AccountDocument account = load(mainAccountId);
            if (!account.getPrimaryCustomerId().equals(customerId)) {
                throw new IllegalArgumentException("The debit card must belong to the account owner");
            }
            DebitCardDocument card = new DebitCardDocument();
            card.setCardNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            card.setCustomerId(customerId);
            card.setMainAccountId(mainAccountId);
            card.setStatus(ACTIVE);
            return cards.save(card);
        }).subscribeOn(Schedulers.io());
    }

    /**
     * @return all debit cards
     */
    public Single<List<DebitCardDocument>> cards() {
        return Single.fromCallable(cards::findAll).subscribeOn(Schedulers.io());
    }

    /**
     * @param id card identifier
     * @return one card
     */
    public Single<DebitCardDocument> card(String id) {
        return Single.fromCallable(() -> loadCard(id)).subscribeOn(Schedulers.io());
    }

    /**
     * @param id card identifier
     * @param mainAccountId replacement main account
     * @return updated card
     */
    public Single<DebitCardDocument> updateCard(String id, String mainAccountId) {
        return Single.fromCallable(() -> {
            DebitCardDocument card = loadCard(id);
            load(mainAccountId);
            card.setMainAccountId(mainAccountId);
            return cards.save(card);
        }).subscribeOn(Schedulers.io());
    }

    /**
     * @param id card identifier
     * @return completion
     */
    public Single<Boolean> deleteCard(String id) {
        return Single.fromCallable(() -> {
            cards.delete(loadCard(id));
            return true;
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Pays with the debit card. The amount leaves the main account.
     *
     * @param id card identifier
     * @param amount payment
     * @return updated account
     */
    public Single<AccountDocument> payWithCard(String id, BigDecimal amount) {
        return Single.fromCallable(() -> {
            DebitCardDocument card = loadCard(id);
            return apply(load(card.getMainAccountId()), "DEBIT_CARD", amount, null, "Debit card payment");
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Debits the main account and returns the data the caller needs to publish a credit-payment event.
     *
     * @param cardId debit card
     * @param amount amount
     * @param operationId idempotency key
     * @return account after the debit
     */
    public AccountDocument debitForCreditPayment(String cardId, BigDecimal amount, String operationId) {
        DebitCardDocument card = loadCard(cardId);
        return apply(load(card.getMainAccountId()), "DEBIT_CARD", amount, operationId, "Debit card credit payment");
    }

    /**
     * Moves the main account of a debit card for a Yanki operation. DEBIT takes money, CREDIT returns it.
     *
     * @param cardNumber debit card number
     * @param amount amount
     * @param direction DEBIT or CREDIT
     * @param operationId idempotency key
     */
    public void applyWalletOperation(String cardNumber, BigDecimal amount, String direction, String operationId) {
        if (movements.findByOperationId(operationId).isPresent()) {
            return;
        }
        DebitCardDocument card = cards.findByCardNumber(cardNumber)
                .orElseThrow(() -> new IllegalArgumentException("Debit card was not found"));
        String type = "CREDIT".equals(direction) ? "DEPOSIT" : "DEBIT_CARD";
        apply(load(card.getMainAccountId()), type, amount, operationId, "Yanki " + direction);
    }

    /**
     * @param id movement identifier
     * @return one ledger line
     */
    public Single<MovementDocument> findMovement(String id) {
        return Single.fromCallable(() -> loadMovement(id)).subscribeOn(Schedulers.io());
    }

    /**
     * Updates the description of a ledger line. Amount and type stay unchanged.
     *
     * @param id movement identifier
     * @param description new description
     * @return updated movement
     */
    public Single<MovementDocument> updateMovement(String id, String description) {
        return Single.fromCallable(() -> {
            MovementDocument movement = loadMovement(id);
            movement.setDescription(description);
            return movements.save(movement);
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Deletes a ledger line and rebuilds the account balance from the remaining lines.
     *
     * @param id movement identifier
     * @return completion
     */
    public Single<Boolean> deleteMovement(String id) {
        return Single.fromCallable(() -> {
            MovementDocument movement = loadMovement(id);
            movements.delete(movement);
            rebuildBalance(movement.getAccountId());
            return true;
        }).subscribeOn(Schedulers.io());
    }

    /**
     * @param accountId account identifier
     * @return ledger of that account
     */
    public Single<List<MovementDocument>> movements(String accountId) {
        return Single.fromCallable(() -> movements.findByAccountIdOrderByCreatedAtAsc(accountId)).subscribeOn(Schedulers.io());
    }

    /**
     * @param accountId account identifier
     * @param from interval start
     * @param to interval end
     * @return movements in the interval
     */
    public Single<List<MovementDocument>> report(String accountId, Instant from, Instant to) {
        return Single.fromCallable(() -> movements.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountId, from, to))
                .subscribeOn(Schedulers.io());
    }

    /**
     * @param cardId debit card
     * @return ten newest card movements, which are the DEBIT_CARD lines of its main account
     */
    public Single<List<MovementDocument>> lastCardMovements(String cardId) {
        return Single.fromCallable(() -> {
            DebitCardDocument card = loadCard(cardId);
            List<MovementDocument> matched = movements.findByAccountIdOrderByCreatedAtAsc(card.getMainAccountId()).stream()
                    .filter(movement -> "DEBIT_CARD".equals(movement.getType()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.reverse(matched);
            return matched.stream().limit(10).toList();
        }).subscribeOn(Schedulers.io());
    }

    private AccountDocument apply(AccountDocument account, String type, BigDecimal amount, String operationId, String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("The amount must be greater than zero");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant from = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = today.plusMonths(1).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long counted = movements.countByAccountIdAndTypeInAndCreatedAtBetween(account.getId(), COUNTED, from, to);
        if (COUNTED.contains(type)) {
            if ("SAVINGS".equals(account.getAccountType())) {
                AccountRules.checkSavingsCap(account.getMaxMovementsPerMonth(), counted);
            }
            if ("FIXED_TERM".equals(account.getAccountType())) {
                Instant dayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC);
                Instant dayEnd = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                long todayCount = movements.countByAccountIdAndTypeInAndCreatedAtBetween(account.getId(), COUNTED, dayStart, dayEnd);
                AccountRules.checkFixedTerm(account.getFixedTermDay(), today, todayCount);
            }
        }
        BigDecimal commission = COUNTED.contains(type)
                ? AccountRules.commission(counted, catalog.freePerMonth(), catalog.commissionAmount())
                : BigDecimal.ZERO;
        BigDecimal signed = signed(type, amount);
        BigDecimal next = account.getBalance().add(signed).subtract(commission);
        if (next.signum() < 0) {
            throw new IllegalArgumentException("The account does not have enough balance");
        }
        saveMovement(account.getId(), type, amount, operationId, description);
        if (commission.signum() > 0) {
            saveMovement(account.getId(), "COMMISSION", commission, null, "Transaction commission");
        }
        account.setBalance(next);
        return accounts.save(account);
    }

    private void saveMovement(String accountId, String type, BigDecimal amount, String operationId, String description) {
        MovementDocument movement = new MovementDocument();
        movement.setAccountId(accountId);
        movement.setType(type);
        movement.setAmount(amount);
        movement.setCreatedAt(Instant.now());
        movement.setOperationId(operationId);
        movement.setDescription(description);
        movements.save(movement);
    }

    private BigDecimal signed(String type, BigDecimal amount) {
        if ("DEPOSIT".equals(type) || "TRANSFER_IN".equals(type)) {
            return amount;
        }
        return amount.negate();
    }

    private List<String> holders(BankDirectory.CustomerView customer, List<String> requested) {
        if (!"BUSINESS".equals(customer.customerType())) {
            return List.of(customer.id());
        }
        if (requested == null || requested.isEmpty() || !requested.contains(customer.id())) {
            throw new IllegalArgumentException("A business account needs one or more holders, including the primary customer");
        }
        return requested;
    }

    private BigDecimal maintenance(String accountType, String profile, BankRules.Product product) {
        if ("CHECKING".equals(accountType) && "PYME".equals(profile)) {
            return catalog.pymeMaintenanceFee();
        }
        return product.getMaintenanceFee();
    }

    private BankRules.Product product(String accountType) {
        return catalog.product(accountType);
    }

    private void rebuildBalance(String accountId) {
        BigDecimal balance = BigDecimal.ZERO;
        for (MovementDocument movement : movements.findByAccountIdOrderByCreatedAtAsc(accountId)) {
            balance = balance.add(signed(movement.getType(), movement.getAmount()));
        }
        AccountDocument account = load(accountId);
        account.setBalance(balance);
        accounts.save(account);
    }

    private MovementDocument loadMovement(String id) {
        return movements.findById(id).orElseThrow(() -> new IllegalArgumentException("Movement was not found"));
    }

    private AccountDocument load(String id) {
        return accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Account was not found"));
    }

    private DebitCardDocument loadCard(String id) {
        return cards.findById(id).orElseThrow(() -> new IllegalArgumentException("Debit card was not found"));
    }
}

package com.nttdata.bank.account.api;

import com.nttdata.bank.account.api.contract.AccountApi;
import com.nttdata.bank.account.application.AccountService;
import com.nttdata.bank.account.domain.AccountDocument;
import com.nttdata.bank.account.domain.DebitCardDocument;
import com.nttdata.bank.account.domain.MovementDocument;
import com.nttdata.bank.account.infrastructure.AccountEvents;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implements the account contract generated from {@code openapi/openapi.yaml}.
 */
@RestController
public class AccountController implements AccountApi {

    private final AccountService accounts;
    private final AccountEvents events;

    /**
     * @param accounts account use cases
     * @param events Kafka publisher for credit payments
     */
    public AccountController(AccountService accounts, AccountEvents events) {
        this.accounts = accounts;
        this.events = events;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> open(OpenAccountRequest request) {
        return accounts.open(request.customerId(), request.accountType(), request.holderIds(), request.signerIds(),
                        request.fixedTermDay(), request.initialDeposit())
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body))
                .blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<AccountDocument>> all() {
        return accounts.findAll().map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> one(String id) {
        return accounts.find(id).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> update(String id, HoldersRequest request) {
        return accounts.update(id, request.holderIds(), request.signerIds()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<Void> delete(String id) {
        return accounts.delete(id).map(done -> ResponseEntity.noContent().<Void>build()).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> deposit(String id, AmountRequest request) {
        return accounts.deposit(id, request.amount()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> withdraw(String id, AmountRequest request) {
        return accounts.withdraw(id, request.amount()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> fee(String id) {
        return accounts.chargeMaintenance(id).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> average(String id) {
        return accounts.evaluateAverage(id).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> transfer(TransferRequest request) {
        return accounts.transfer(request.sourceAccountId(), request.targetAccountId(), request.amount())
                .map(ResponseEntity::ok)
                .blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<MovementDocument>> movements(String accountId) {
        return accounts.movements(accountId).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<MovementDocument> movement(String id) {
        return accounts.findMovement(id).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<MovementDocument> updateMovement(String id, DescriptionRequest request) {
        return accounts.updateMovement(id, request.description()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<Void> deleteMovement(String id) {
        return accounts.deleteMovement(id).map(done -> ResponseEntity.noContent().<Void>build()).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<DebitCardDocument> createCard(CardRequest request) {
        return accounts.createCard(request.customerId(), request.mainAccountId())
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body))
                .blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<DebitCardDocument>> cards() {
        return accounts.cards().map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<DebitCardDocument> card(String id) {
        return accounts.card(id).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<DebitCardDocument> updateCard(String id, CardUpdateRequest request) {
        return accounts.updateCard(id, request.mainAccountId()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<Void> deleteCard(String id) {
        return accounts.deleteCard(id).map(done -> ResponseEntity.noContent().<Void>build()).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> pay(String id, AmountRequest request) {
        return accounts.payWithCard(id, request.amount()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountDocument> payCredit(String id, CreditPaymentRequest request) {
        return Single.fromCallable(() -> {
            String operationId = UUID.randomUUID().toString();
            AccountDocument account = accounts.debitForCreditPayment(id, request.amount(), operationId);
            events.requestCreditPayment(operationId, request.productId(), request.productKind(), request.amount(),
                    request.payerCustomerId());
            return account;
        }).subscribeOn(Schedulers.io()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<MovementDocument>> report(String accountId, Instant from, Instant to) {
        return accounts.report(accountId, from, to).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<MovementDocument>> last(String id) {
        return accounts.lastCardMovements(id).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * @param customerId primary owner
     * @param accountType SAVINGS, CHECKING or FIXED_TERM
     * @param holderIds business holders
     * @param signerIds authorized signers
     * @param fixedTermDay day of month for a fixed-term account
     * @param initialDeposit opening deposit, which may be zero
     */
    public record OpenAccountRequest(@NotBlank String customerId, @NotBlank String accountType, List<String> holderIds,
                                     List<String> signerIds, Integer fixedTermDay, BigDecimal initialDeposit) {
    }

    /**
     * @param holderIds holders
     * @param signerIds signers
     */
    public record HoldersRequest(List<String> holderIds, List<String> signerIds) {
    }

    /**
     * @param amount money amount
     */
    public record AmountRequest(@NotNull BigDecimal amount) {
    }

    /**
     * @param description replacement description
     */
    public record DescriptionRequest(String description) {
    }

    /**
     * @param sourceAccountId account that sends the money
     * @param targetAccountId account that receives the money
     * @param amount transferred amount
     */
    public record TransferRequest(@NotBlank String sourceAccountId, @NotBlank String targetAccountId,
                                  @NotNull BigDecimal amount) {
    }

    /**
     * @param customerId owner
     * @param mainAccountId account charged by the card
     */
    public record CardRequest(@NotBlank String customerId, @NotBlank String mainAccountId) {
    }

    /**
     * @param mainAccountId replacement main account
     */
    public record CardUpdateRequest(@NotBlank String mainAccountId) {
    }

    /**
     * @param amount payment
     * @param productId loan or credit card
     * @param productKind LOAN or CARD
     * @param payerCustomerId customer who pays
     */
    public record CreditPaymentRequest(@NotNull BigDecimal amount, @NotBlank String productId,
                                       @NotBlank String productKind, @NotBlank String payerCustomerId) {
    }
}

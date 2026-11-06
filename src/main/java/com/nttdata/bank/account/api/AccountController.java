package com.nttdata.bank.account.api;

import com.nttdata.bank.account.application.AccountService;
import com.nttdata.bank.account.domain.AccountDocument;
import com.nttdata.bank.account.domain.DebitCardDocument;
import com.nttdata.bank.account.domain.MovementDocument;
import com.nttdata.bank.account.infrastructure.AccountEvents;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reactive API for accounts, transfers, debit cards and account reports.
 */
@RestController
public class AccountController {

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
     * @param request opening data
     * @return created account
     */
    @PostMapping("/api/v1/accounts")
    public Single<ResponseEntity<AccountDocument>> open(@Valid @RequestBody OpenAccountRequest request) {
        return accounts.open(request.customerId(), request.accountType(), request.holderIds(), request.signerIds(),
                        request.fixedTermDay(), request.initialDeposit())
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body));
    }

    /**
     * @return all accounts
     */
    @GetMapping("/api/v1/accounts")
    public Single<ResponseEntity<List<AccountDocument>>> all() {
        return accounts.findAll().map(ResponseEntity::ok);
    }

    /**
     * @param id account identifier
     * @return one account and its balance
     */
    @GetMapping("/api/v1/accounts/{id}")
    public Single<ResponseEntity<AccountDocument>> one(@PathVariable String id) {
        return accounts.find(id).map(ResponseEntity::ok);
    }

    /**
     * @param id account identifier
     * @param request holders and signers
     * @return updated account
     */
    @PutMapping("/api/v1/accounts/{id}")
    public Single<ResponseEntity<AccountDocument>> update(@PathVariable String id, @RequestBody HoldersRequest request) {
        return accounts.update(id, request.holderIds(), request.signerIds()).map(ResponseEntity::ok);
    }

    /**
     * @param id account identifier
     * @return empty response
     */
    @DeleteMapping("/api/v1/accounts/{id}")
    public Single<ResponseEntity<Void>> delete(@PathVariable String id) {
        return accounts.delete(id).map(done -> ResponseEntity.noContent().build());
    }

    /**
     * @param id account identifier
     * @param request amount
     * @return updated account
     */
    @PostMapping("/api/v1/accounts/{id}/deposits")
    public Single<ResponseEntity<AccountDocument>> deposit(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        return accounts.deposit(id, request.amount()).map(ResponseEntity::ok);
    }

    /**
     * @param id account identifier
     * @param request amount
     * @return updated account
     */
    @PostMapping("/api/v1/accounts/{id}/withdrawals")
    public Single<ResponseEntity<AccountDocument>> withdraw(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        return accounts.withdraw(id, request.amount()).map(ResponseEntity::ok);
    }

    /**
     * @param id account identifier
     * @return updated account
     */
    @PostMapping("/api/v1/accounts/{id}/maintenance-fee")
    public Single<ResponseEntity<AccountDocument>> fee(@PathVariable String id) {
        return accounts.chargeMaintenance(id).map(ResponseEntity::ok);
    }

    /**
     * @param id account identifier
     * @return updated account
     */
    @PostMapping("/api/v1/accounts/{id}/evaluate-average")
    public Single<ResponseEntity<AccountDocument>> average(@PathVariable String id) {
        return accounts.evaluateAverage(id).map(ResponseEntity::ok);
    }

    /**
     * @param request transfer between two accounts of this bank
     * @return source account
     */
    @PostMapping("/api/v1/transfers")
    public Single<ResponseEntity<AccountDocument>> transfer(@Valid @RequestBody TransferRequest request) {
        return accounts.transfer(request.sourceAccountId(), request.targetAccountId(), request.amount()).map(ResponseEntity::ok);
    }

    /**
     * @param accountId account identifier
     * @return full ledger
     */
    @GetMapping("/api/v1/movements")
    public Single<ResponseEntity<List<MovementDocument>>> movements(@RequestParam String accountId) {
        return accounts.movements(accountId).map(ResponseEntity::ok);
    }

    /**
     * @param id movement identifier
     * @return one ledger line
     */
    @GetMapping("/api/v1/movements/{id}")
    public Single<ResponseEntity<MovementDocument>> movement(@PathVariable String id) {
        return accounts.findMovement(id).map(ResponseEntity::ok);
    }

    /**
     * @param id movement identifier
     * @param request new description
     * @return updated movement
     */
    @PutMapping("/api/v1/movements/{id}")
    public Single<ResponseEntity<MovementDocument>> updateMovement(@PathVariable String id, @RequestBody DescriptionRequest request) {
        return accounts.updateMovement(id, request.description()).map(ResponseEntity::ok);
    }

    /**
     * @param id movement identifier
     * @return empty response
     */
    @DeleteMapping("/api/v1/movements/{id}")
    public Single<ResponseEntity<Void>> deleteMovement(@PathVariable String id) {
        return accounts.deleteMovement(id).map(done -> ResponseEntity.noContent().build());
    }

    /**
     * @param request card data
     * @return created debit card
     */
    @PostMapping("/api/v1/debit-cards")
    public Single<ResponseEntity<DebitCardDocument>> createCard(@Valid @RequestBody CardRequest request) {
        return accounts.createCard(request.customerId(), request.mainAccountId())
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body));
    }

    /**
     * @return all debit cards
     */
    @GetMapping("/api/v1/debit-cards")
    public Single<ResponseEntity<List<DebitCardDocument>>> cards() {
        return accounts.cards().map(ResponseEntity::ok);
    }

    /**
     * @param id card identifier
     * @return one card
     */
    @GetMapping("/api/v1/debit-cards/{id}")
    public Single<ResponseEntity<DebitCardDocument>> card(@PathVariable String id) {
        return accounts.card(id).map(ResponseEntity::ok);
    }

    /**
     * @param id card identifier
     * @param request new main account
     * @return updated card
     */
    @PutMapping("/api/v1/debit-cards/{id}")
    public Single<ResponseEntity<DebitCardDocument>> updateCard(@PathVariable String id, @Valid @RequestBody CardUpdateRequest request) {
        return accounts.updateCard(id, request.mainAccountId()).map(ResponseEntity::ok);
    }

    /**
     * @param id card identifier
     * @return empty response
     */
    @DeleteMapping("/api/v1/debit-cards/{id}")
    public Single<ResponseEntity<Void>> deleteCard(@PathVariable String id) {
        return accounts.deleteCard(id).map(done -> ResponseEntity.noContent().build());
    }

    /**
     * @param id card identifier
     * @param request merchant payment
     * @return debited account
     */
    @PostMapping("/api/v1/debit-cards/{id}/payments")
    public Single<ResponseEntity<AccountDocument>> pay(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        return accounts.payWithCard(id, request.amount()).map(ResponseEntity::ok);
    }

    /**
     * Debits the card's main account and emits a Kafka payment for the credit product.
     *
     * @param id card identifier
     * @param request credit product payment
     * @return debited account
     */
    @PostMapping("/api/v1/debit-cards/{id}/credit-payments")
    public Single<ResponseEntity<AccountDocument>> payCredit(@PathVariable String id, @Valid @RequestBody CreditPaymentRequest request) {
        return Single.fromCallable(() -> {
            String operationId = UUID.randomUUID().toString();
            AccountDocument account = accounts.debitForCreditPayment(id, request.amount(), operationId);
            events.requestCreditPayment(operationId, request.productId(), request.productKind(), request.amount(), request.payerCustomerId());
            return account;
        }).subscribeOn(Schedulers.io()).map(ResponseEntity::ok);
    }

    /**
     * @param accountId account identifier
     * @param from interval start
     * @param to interval end
     * @return movements in the interval
     */
    @GetMapping("/api/v1/account-reports")
    public Single<ResponseEntity<List<MovementDocument>>> report(
            @RequestParam String accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return accounts.report(accountId, from, to).map(ResponseEntity::ok);
    }

    /**
     * @param id debit card identifier
     * @return ten newest debit-card movements
     */
    @GetMapping("/api/v1/account-reports/debit-cards/{id}/last")
    public Single<ResponseEntity<List<MovementDocument>>> last(@PathVariable String id) {
        return accounts.lastCardMovements(id).map(ResponseEntity::ok);
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
    public record TransferRequest(@NotBlank String sourceAccountId, @NotBlank String targetAccountId, @NotNull BigDecimal amount) {
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

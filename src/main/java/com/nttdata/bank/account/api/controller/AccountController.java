package com.nttdata.bank.account.api.controller;

import com.nttdata.bank.account.api.contract.AccountApi;
import com.nttdata.bank.account.api.dto.AccountResponse;
import com.nttdata.bank.account.api.dto.AmountRequest;
import com.nttdata.bank.account.api.dto.CardRequest;
import com.nttdata.bank.account.api.dto.CardUpdateRequest;
import com.nttdata.bank.account.api.dto.CreditPaymentRequest;
import com.nttdata.bank.account.api.dto.DebitCardResponse;
import com.nttdata.bank.account.api.dto.DescriptionRequest;
import com.nttdata.bank.account.api.dto.HoldersRequest;
import com.nttdata.bank.account.api.dto.MovementResponse;
import com.nttdata.bank.account.api.dto.OpenAccountRequest;
import com.nttdata.bank.account.api.dto.TransferRequest;
import com.nttdata.bank.account.service.AccountService;
import com.nttdata.bank.account.domain.model.AccountDocument;
import com.nttdata.bank.account.infrastructure.kafka.AccountEvents;
import com.nttdata.bank.account.mapper.AccountMapper;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
    private final AccountMapper mapper;

    /**
     * @param accounts account use cases
     * @param events Kafka publisher for credit payments
     * @param mapper domain-to-DTO mapper
     */
    public AccountController(AccountService accounts, AccountEvents events, AccountMapper mapper) {
        this.accounts = accounts;
        this.events = events;
        this.mapper = mapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> open(OpenAccountRequest request) {
        return accounts.open(request.customerId(), request.accountType(), request.holderIds(), request.signerIds(),
                        request.fixedTermDay(), request.initialDeposit())
                .map(mapper::toResponse)
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body))
                .blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<AccountResponse>> all() {
        return accounts.findAll().map(mapper::toAccountResponseList).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> one(String id) {
        return accounts.find(id).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> update(String id, HoldersRequest request) {
        return accounts.update(id, request.holderIds(), request.signerIds())
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .blockingGet();
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
    public ResponseEntity<AccountResponse> deposit(String id, AmountRequest request) {
        return accounts.deposit(id, request.amount()).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> withdraw(String id, AmountRequest request) {
        return accounts.withdraw(id, request.amount()).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> fee(String id) {
        return accounts.chargeMaintenance(id).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> average(String id) {
        return accounts.evaluateAverage(id).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> transfer(TransferRequest request) {
        return accounts.transfer(request.sourceAccountId(), request.targetAccountId(), request.amount())
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<MovementResponse>> movements(String accountId) {
        return accounts.movements(accountId).map(mapper::toMovementResponseList).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<MovementResponse> movement(String id) {
        return accounts.findMovement(id).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<MovementResponse> updateMovement(String id, DescriptionRequest request) {
        return accounts.updateMovement(id, request.description())
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .blockingGet();
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
    public ResponseEntity<DebitCardResponse> createCard(CardRequest request) {
        return accounts.createCard(request.customerId(), request.mainAccountId())
                .map(mapper::toResponse)
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body))
                .blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<DebitCardResponse>> cards() {
        return accounts.cards().map(mapper::toDebitCardResponseList).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<DebitCardResponse> card(String id) {
        return accounts.card(id).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<DebitCardResponse> updateCard(String id, CardUpdateRequest request) {
        return accounts.updateCard(id, request.mainAccountId())
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .blockingGet();
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
    public ResponseEntity<AccountResponse> pay(String id, AmountRequest request) {
        return accounts.payWithCard(id, request.amount()).map(mapper::toResponse).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<AccountResponse> payCredit(String id, CreditPaymentRequest request) {
        return Single.fromCallable(() -> {
            String operationId = UUID.randomUUID().toString();
            AccountDocument account = accounts.debitForCreditPayment(id, request.amount(), operationId);
            events.requestCreditPayment(operationId, request.productId(), request.productKind(), request.amount(),
                    request.payerCustomerId());
            return mapper.toResponse(account);
        }).subscribeOn(Schedulers.io()).map(ResponseEntity::ok).blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<MovementResponse>> report(String accountId, Instant from, Instant to) {
        return accounts.report(accountId, from, to).map(mapper::toMovementResponseList).map(ResponseEntity::ok)
                .blockingGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<MovementResponse>> last(String id) {
        return accounts.lastCardMovements(id).map(mapper::toMovementResponseList).map(ResponseEntity::ok).blockingGet();
    }
}

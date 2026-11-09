package com.nttdata.bank.account.api.dto;

/**
 * Debit card returned by the HTTP API.
 */
public record DebitCardResponse(
        String id,
        String cardNumber,
        String customerId,
        String mainAccountId,
        String status
) {
}
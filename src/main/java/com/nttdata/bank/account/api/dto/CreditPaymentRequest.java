package com.nttdata.bank.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param amount payment
 * @param productId loan or credit card
 * @param productKind LOAN or CARD
 * @param payerCustomerId customer who pays
 */
public record CreditPaymentRequest(
        @NotNull BigDecimal amount,
        @NotBlank String productId,
        @NotBlank String productKind,
        @NotBlank String payerCustomerId
) {
}
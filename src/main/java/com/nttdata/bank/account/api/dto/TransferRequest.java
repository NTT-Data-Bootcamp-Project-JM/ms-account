package com.nttdata.bank.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param sourceAccountId account that sends the money
 * @param targetAccountId account that receives the money
 * @param amount transferred amount
 */
public record TransferRequest(
        @NotBlank String sourceAccountId,
        @NotBlank String targetAccountId,
        @NotNull BigDecimal amount
) {
}
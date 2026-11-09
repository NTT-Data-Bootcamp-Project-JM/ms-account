package com.nttdata.bank.account.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param customerId primary owner
 * @param accountType SAVINGS, CHECKING or FIXED_TERM
 * @param holderIds business holders
 * @param signerIds authorized signers
 * @param fixedTermDay day of month for a fixed-term account
 * @param initialDeposit opening deposit, which may be zero
 */
public record OpenAccountRequest(
        @NotBlank String customerId,
        @NotBlank String accountType,
        List<String> holderIds,
        List<String> signerIds,
        Integer fixedTermDay,
        BigDecimal initialDeposit
) {
}
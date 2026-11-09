package com.nttdata.bank.account.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param customerId owner
 * @param mainAccountId account charged by the card
 */
public record CardRequest(@NotBlank String customerId, @NotBlank String mainAccountId) {
}
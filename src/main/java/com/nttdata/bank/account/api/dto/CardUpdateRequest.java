package com.nttdata.bank.account.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param mainAccountId replacement main account
 */
public record CardUpdateRequest(@NotBlank String mainAccountId) {
}
package com.nttdata.bank.account.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Account movement returned by the HTTP API.
 */
public record MovementResponse(
        String id,
        String accountId,
        String type,
        BigDecimal amount,
        Instant createdAt,
        String operationId,
        String description
) {
}
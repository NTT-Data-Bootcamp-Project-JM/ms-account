package com.nttdata.bank.account.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Account returned by the HTTP API.
 */
public record AccountResponse(
        String id,
        String accountNumber,
        String accountType,
        String primaryCustomerId,
        String customerType,
        String profile,
        List<String> holderIds,
        List<String> signerIds,
        BigDecimal balance,
        BigDecimal maintenanceFee,
        Integer maxMovementsPerMonth,
        Integer fixedTermDay,
        BigDecimal minimumDailyAverage,
        String status
) {
}
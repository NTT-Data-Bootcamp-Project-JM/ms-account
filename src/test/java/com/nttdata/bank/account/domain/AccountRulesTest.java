package com.nttdata.bank.account.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Personal quotas, business restrictions, VIP and PYME prerequisites, and movement limits.
 */
class AccountRulesTest {

    @Test
    void personalCustomerMayHaveOneSavingsOneCheckingAndManyFixedTerms() {
        AccountRules.checkOpening("PERSONAL", "STANDARD", "SAVINGS", 0, false, false);
        assertThrows(IllegalArgumentException.class,
                () -> AccountRules.checkOpening("PERSONAL", "STANDARD", "SAVINGS", 1, false, false));
        AccountRules.checkOpening("PERSONAL", "STANDARD", "FIXED_TERM", 4, false, false);
    }

    @Test
    void businessCustomerCanOpenOnlyCheckingAccounts() {
        AccountRules.checkOpening("BUSINESS", "STANDARD", "CHECKING", 3, false, false);
        assertThrows(IllegalArgumentException.class,
                () -> AccountRules.checkOpening("BUSINESS", "STANDARD", "SAVINGS", 0, false, false));
    }

    @Test
    void vipSavingsAndPymeCheckingRequireACreditCard() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountRules.checkOpening("PERSONAL", "VIP", "SAVINGS", 0, false, false));
        AccountRules.checkOpening("PERSONAL", "VIP", "SAVINGS", 0, true, false);
        assertThrows(IllegalArgumentException.class,
                () -> AccountRules.checkOpening("BUSINESS", "PYME", "CHECKING", 0, false, false));
    }

    @Test
    void overdueDebtBlocksOpening() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountRules.checkOpening("PERSONAL", "STANDARD", "SAVINGS", 0, false, true));
    }

    @Test
    void savingsCapAndFixedTermDayAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> AccountRules.checkSavingsCap(10, 10));
        AccountRules.checkSavingsCap(10, 9);
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertThrows(IllegalArgumentException.class, () -> AccountRules.checkFixedTerm(15, today, 0));
        AccountRules.checkFixedTerm(5, today, 0);
        assertThrows(IllegalArgumentException.class, () -> AccountRules.checkFixedTerm(5, today, 1));
    }

    @Test
    void commissionStartsAfterTheFreeQuota() {
        assertEquals(new BigDecimal("1.50"), AccountRules.commission(5, 5, new BigDecimal("1.50")));
        assertEquals(BigDecimal.ZERO, AccountRules.commission(4, 5, new BigDecimal("1.50")));
    }
}

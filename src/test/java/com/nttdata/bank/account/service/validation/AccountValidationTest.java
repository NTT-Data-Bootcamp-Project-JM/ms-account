package com.nttdata.bank.account.service.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Personal quotas, business restrictions, VIP and PYME prerequisites, and movement limits.
 */
class AccountValidationTest {

    @Test
    void personalCustomerMayHaveOneSavingsOneCheckingAndManyFixedTerms() {
        AccountValidation.checkOpening("PERSONAL", "STANDARD", "SAVINGS", 0, false, false);
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.checkOpening("PERSONAL", "STANDARD", "SAVINGS", 1, false, false));
        AccountValidation.checkOpening("PERSONAL", "STANDARD", "FIXED_TERM", 4, false, false);
    }

    @Test
    void businessCustomerCanOpenOnlyCheckingAccounts() {
        AccountValidation.checkOpening("BUSINESS", "STANDARD", "CHECKING", 3, false, false);
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.checkOpening("BUSINESS", "STANDARD", "SAVINGS", 0, false, false));
    }

    @Test
    void vipSavingsAndPymeCheckingRequireACreditCard() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.checkOpening("PERSONAL", "VIP", "SAVINGS", 0, false, false));
        AccountValidation.checkOpening("PERSONAL", "VIP", "SAVINGS", 0, true, false);
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.checkOpening("BUSINESS", "PYME", "CHECKING", 0, false, false));
    }

    @Test
    void overdueDebtBlocksOpening() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountValidation.checkOpening("PERSONAL", "STANDARD", "SAVINGS", 0, false, true));
    }

    @Test
    void savingsCapAndFixedTermDayAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> AccountValidation.checkSavingsCap(10, 10));
        AccountValidation.checkSavingsCap(10, 9);
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertThrows(IllegalArgumentException.class, () -> AccountValidation.checkFixedTerm(15, today, 0));
        AccountValidation.checkFixedTerm(5, today, 0);
        assertThrows(IllegalArgumentException.class, () -> AccountValidation.checkFixedTerm(5, today, 1));
    }

    @Test
    void commissionStartsAfterTheFreeQuota() {
        assertEquals(new BigDecimal("1.50"), AccountValidation.commission(5, 5, new BigDecimal("1.50")));
        assertEquals(BigDecimal.ZERO, AccountValidation.commission(4, 5, new BigDecimal("1.50")));
    }
}

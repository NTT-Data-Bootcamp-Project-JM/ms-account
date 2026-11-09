package com.nttdata.bank.account.service.validation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Account rules from parts I and II. A personal customer may have one savings account,
 * one checking account and many fixed-term accounts. A credit card is not involved
 * except as a prerequisite for VIP savings and PYME checking.
 */
public final class AccountValidation {

    private AccountValidation() {
    }

    /**
     * Checks whether this customer may open the requested account.
     *
     * @param customerType PERSONAL or BUSINESS
     * @param profile STANDARD, VIP or PYME
     * @param accountType SAVINGS, CHECKING or FIXED_TERM
     * @param existingSameType accounts of this type already open
     * @param hasCreditCard active credit card flag
     * @param overdue overdue credit flag
     */
    public static void checkOpening(String customerType, String profile, String accountType,
                                    long existingSameType, boolean hasCreditCard, boolean overdue) {
        if (overdue) {
            throw new IllegalArgumentException("The customer has overdue debt and cannot acquire a product");
        }
        if ("BUSINESS".equals(customerType) && !"CHECKING".equals(accountType)) {
            throw new IllegalArgumentException("A business customer can only open checking accounts");
        }
        if ("PERSONAL".equals(customerType) && !"FIXED_TERM".equals(accountType) && existingSameType >= 1) {
            throw new IllegalArgumentException("A personal customer can have only one " + accountType.toLowerCase() + " account");
        }
        if ("SAVINGS".equals(accountType) && "VIP".equals(profile) && !hasCreditCard) {
            throw new IllegalArgumentException("A VIP savings account requires an active credit card");
        }
        if ("CHECKING".equals(accountType) && "PYME".equals(profile) && !hasCreditCard) {
            throw new IllegalArgumentException("A PYME checking account requires an active credit card");
        }
    }

    /**
     * Savings accounts stop once the monthly movement limit is reached. A null limit means unlimited.
     *
     * @param maxMovements configured limit
     * @param counted movements already made this month
     */
    public static void checkSavingsCap(Integer maxMovements, long counted) {
        if (maxMovements != null && counted >= maxMovements) {
            throw new IllegalArgumentException("The monthly movement limit of the savings account has been reached");
        }
    }

    /**
     * A fixed-term account accepts one deposit or withdrawal on its configured day.
     *
     * @param allowedDay day of month
     * @param today current date
     * @param movementsThatDay movements already stored for that day
     */
    public static void checkFixedTerm(int allowedDay, LocalDate today, long movementsThatDay) {
        if (today.getDayOfMonth() != allowedDay) {
            throw new IllegalArgumentException("A fixed-term account only moves on day " + allowedDay);
        }
        if (movementsThatDay >= 1) {
            throw new IllegalArgumentException("A fixed-term account allows only one movement on that day");
        }
    }

    /**
     * Commission charged once the free monthly transactions are used up.
     *
     * @param counted movements already made this month
     * @param freePerMonth free quota
     * @param commissionAmount price of an extra movement
     * @return commission for the next movement
     */
    public static BigDecimal commission(long counted, int freePerMonth, BigDecimal commissionAmount) {
        if (counted >= freePerMonth) {
            return commissionAmount;
        }
        return BigDecimal.ZERO;
    }
}

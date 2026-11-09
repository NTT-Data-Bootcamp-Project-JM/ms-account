package com.nttdata.bank.account.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Fees and limits loaded from the config server. Nothing here is hardcoded in the use cases.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank")
public class BankRulesProperties {

    private Product savings = new Product();
    private Product checking = new Product();
    private Product fixedTerm = new Product();
    private Transactions transactions = new Transactions();
    private Vip vip = new Vip();
    private Pyme pyme = new Pyme();

    /**
     * Parameters shared by an account type.
     */
    @Getter
    @Setter
    public static class Product {
        // Fallback if config server does not supply bank.*.maintenance-fee
        private BigDecimal maintenanceFee = BigDecimal.ZERO;
        private Integer maxMovementsPerMonth;
        // Fallback if config server does not supply bank.*.minimum-opening-amount
        private BigDecimal minimumOpeningAmount = BigDecimal.ZERO;
    }

    /**
     * Commission shared by every account type.
     */
    @Getter
    @Setter
    public static class Transactions {
        // Fallback if config server does not supply bank.transactions.free-per-month
        private int freePerMonth = 5;
        // Fallback if config server does not supply bank.transactions.commission-amount
        private BigDecimal commissionAmount = new BigDecimal("1.50");
    }

    /**
     * VIP savings requirements.
     */
    @Getter
    @Setter
    public static class Vip {
        // Fallback if config server does not supply bank.vip.minimum-daily-average
        private BigDecimal minimumDailyAverage = new BigDecimal("500.00");
        // Fallback if config server does not supply bank.vip.shortfall-penalty
        private BigDecimal shortfallPenalty = new BigDecimal("10.00");
    }

    /**
     * PYME checking fee, which the statement sets to zero.
     */
    @Getter
    @Setter
    public static class Pyme {
        // Fallback if config server does not supply bank.pyme.maintenance-fee
        private BigDecimal maintenanceFee = BigDecimal.ZERO;
    }
}

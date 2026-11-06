package com.nttdata.bank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Fees and limits loaded from the config server. Nothing here is hardcoded in the use cases.
 */
@ConfigurationProperties(prefix = "bank")
public class BankRules {

    private Product savings = new Product();
    private Product checking = new Product();
    private Product fixedTerm = new Product();
    private Transactions transactions = new Transactions();
    private Vip vip = new Vip();
    private Pyme pyme = new Pyme();

    /**
     * @return savings parameters
     */
    public Product getSavings() {
        return savings;
    }

    /**
     * @param savings savings parameters
     */
    public void setSavings(Product savings) {
        this.savings = savings;
    }

    /**
     * @return checking parameters
     */
    public Product getChecking() {
        return checking;
    }

    /**
     * @param checking checking parameters
     */
    public void setChecking(Product checking) {
        this.checking = checking;
    }

    /**
     * @return fixed-term parameters
     */
    public Product getFixedTerm() {
        return fixedTerm;
    }

    /**
     * @param fixedTerm fixed-term parameters
     */
    public void setFixedTerm(Product fixedTerm) {
        this.fixedTerm = fixedTerm;
    }

    /**
     * @return free quota and commission
     */
    public Transactions getTransactions() {
        return transactions;
    }

    /**
     * @param transactions free quota and commission
     */
    public void setTransactions(Transactions transactions) {
        this.transactions = transactions;
    }

    /**
     * @return VIP savings parameters
     */
    public Vip getVip() {
        return vip;
    }

    /**
     * @param vip VIP savings parameters
     */
    public void setVip(Vip vip) {
        this.vip = vip;
    }

    /**
     * @return PYME checking parameters
     */
    public Pyme getPyme() {
        return pyme;
    }

    /**
     * @param pyme PYME checking parameters
     */
    public void setPyme(Pyme pyme) {
        this.pyme = pyme;
    }

    /**
     * Parameters shared by an account type.
     */
    public static class Product {
        private BigDecimal maintenanceFee = BigDecimal.ZERO;
        private Integer maxMovementsPerMonth;
        private BigDecimal minimumOpeningAmount = BigDecimal.ZERO;

        /**
         * @return monthly maintenance fee
         */
        public BigDecimal getMaintenanceFee() {
            return maintenanceFee;
        }

        /**
         * @param maintenanceFee monthly maintenance fee
         */
        public void setMaintenanceFee(BigDecimal maintenanceFee) {
            this.maintenanceFee = maintenanceFee;
        }

        /**
         * @return hard monthly cap, or null when the account has no cap
         */
        public Integer getMaxMovementsPerMonth() {
            return maxMovementsPerMonth;
        }

        /**
         * @param maxMovementsPerMonth hard monthly cap
         */
        public void setMaxMovementsPerMonth(Integer maxMovementsPerMonth) {
            this.maxMovementsPerMonth = maxMovementsPerMonth;
        }

        /**
         * @return minimum amount required to open the account
         */
        public BigDecimal getMinimumOpeningAmount() {
            return minimumOpeningAmount;
        }

        /**
         * @param minimumOpeningAmount minimum amount required to open the account
         */
        public void setMinimumOpeningAmount(BigDecimal minimumOpeningAmount) {
            this.minimumOpeningAmount = minimumOpeningAmount;
        }
    }

    /**
     * Commission shared by every account type.
     */
    public static class Transactions {
        private int freePerMonth = 5;
        private BigDecimal commissionAmount = new BigDecimal("1.50");

        /**
         * @return movements that do not pay commission
         */
        public int getFreePerMonth() {
            return freePerMonth;
        }

        /**
         * @param freePerMonth movements that do not pay commission
         */
        public void setFreePerMonth(int freePerMonth) {
            this.freePerMonth = freePerMonth;
        }

        /**
         * @return price of each extra movement
         */
        public BigDecimal getCommissionAmount() {
            return commissionAmount;
        }

        /**
         * @param commissionAmount price of each extra movement
         */
        public void setCommissionAmount(BigDecimal commissionAmount) {
            this.commissionAmount = commissionAmount;
        }
    }

    /**
     * VIP savings requirements.
     */
    public static class Vip {
        private BigDecimal minimumDailyAverage = new BigDecimal("500.00");
        private BigDecimal shortfallPenalty = new BigDecimal("10.00");

        /**
         * @return required average daily balance
         */
        public BigDecimal getMinimumDailyAverage() {
            return minimumDailyAverage;
        }

        /**
         * @param minimumDailyAverage required average daily balance
         */
        public void setMinimumDailyAverage(BigDecimal minimumDailyAverage) {
            this.minimumDailyAverage = minimumDailyAverage;
        }

        /**
         * @return fee charged when the average is missed
         */
        public BigDecimal getShortfallPenalty() {
            return shortfallPenalty;
        }

        /**
         * @param shortfallPenalty fee charged when the average is missed
         */
        public void setShortfallPenalty(BigDecimal shortfallPenalty) {
            this.shortfallPenalty = shortfallPenalty;
        }
    }

    /**
     * PYME checking fee, which the statement sets to zero.
     */
    public static class Pyme {
        private BigDecimal maintenanceFee = BigDecimal.ZERO;

        /**
         * @return maintenance fee
         */
        public BigDecimal getMaintenanceFee() {
            return maintenanceFee;
        }

        /**
         * @param maintenanceFee maintenance fee
         */
        public void setMaintenanceFee(BigDecimal maintenanceFee) {
            this.maintenanceFee = maintenanceFee;
        }
    }
}

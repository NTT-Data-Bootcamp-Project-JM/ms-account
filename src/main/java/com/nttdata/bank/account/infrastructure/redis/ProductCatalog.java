package com.nttdata.bank.account.infrastructure.redis;

import com.nttdata.bank.account.config.BankRulesProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Account types and product rules stored in Redis. The config server is the source copied at startup.
 * A missing key or a Redis outage falls back to that configuration so movements can still be posted.
 */
@Configuration
public class ProductCatalog {

    static final String ACCOUNT_TYPES = "catalog:account-types";
    static final String PRODUCT_RULES = "catalog:product-rules";
    private static final List<String> DEFAULT_TYPES = List.of("SAVINGS", "CHECKING", "FIXED_TERM");

    private static final Logger LOG = LoggerFactory.getLogger(ProductCatalog.class);
    private final StringRedisTemplate redis;
    private final BankRulesProperties rules;

    /**
     * @param redis Redis string and hash operations
     * @param rules fees and limits loaded from the config server
     */
    public ProductCatalog(StringRedisTemplate redis, BankRulesProperties rules) {
        this.redis = redis;
        this.rules = rules;
    }

    /**
     * Seeds account types and product rules when Redis is reachable.
     *
     * @return startup runner
     */
    @Bean
    public CommandLineRunner seedProductCatalog() {
        return args -> seed();
    }

    /**
     * Writes the account-type list and the product-rule hash.
     */
    public void seed() {
        try {
            redis.opsForValue().set(ACCOUNT_TYPES, String.join(",", DEFAULT_TYPES));
            Map<String, String> values = new LinkedHashMap<>();
            putProduct(values, "savings", rules.getSavings());
            putProduct(values, "checking", rules.getChecking());
            putProduct(values, "fixed-term", rules.getFixedTerm());
            values.put("transactions.free-per-month", Integer.toString(rules.getTransactions().getFreePerMonth()));
            values.put("transactions.commission-amount", rules.getTransactions().getCommissionAmount().toPlainString());
            values.put("vip.minimum-daily-average", rules.getVip().getMinimumDailyAverage().toPlainString());
            values.put("vip.shortfall-penalty", rules.getVip().getShortfallPenalty().toPlainString());
            values.put("pyme.maintenance-fee", rules.getPyme().getMaintenanceFee().toPlainString());
            redis.opsForHash().putAll(PRODUCT_RULES, values);
        } catch (RuntimeException ex) {
            LOG.warn("Product catalog was not cached in Redis: {}", ex.getMessage());
        }
    }

    /**
     * @return account types from Redis, or the built-in list when the catalog is missing
     */
    public List<String> accountTypes() {
        try {
            String raw = redis.opsForValue().get(ACCOUNT_TYPES);
            if (raw == null || raw.isBlank()) {
                return DEFAULT_TYPES;
            }
            return Arrays.stream(raw.split(",")).map(String::trim).filter(code -> !code.isEmpty()).toList();
        } catch (RuntimeException ex) {
            LOG.warn("Account type catalog was not read from Redis: {}", ex.getMessage());
            return DEFAULT_TYPES;
        }
    }

    /**
     * @param accountType SAVINGS, CHECKING or FIXED_TERM
     * @return fees and limits, preferring the Redis hash over the config server
     */
    public BankRulesProperties.Product product(String accountType) {
        if (!accountTypes().contains(accountType)) {
            throw new IllegalArgumentException("Unknown account type");
        }
        String prefix = switch (accountType) {
            case "SAVINGS" -> "savings";
            case "CHECKING" -> "checking";
            case "FIXED_TERM" -> "fixed-term";
            default -> throw new IllegalArgumentException("Unknown account type");
        };
        BankRulesProperties.Product fallback = fallback(accountType);
        BankRulesProperties.Product product = new BankRulesProperties.Product();
        product.setMaintenanceFee(decimal(prefix + ".maintenance-fee", fallback.getMaintenanceFee()));
        product.setMinimumOpeningAmount(decimal(prefix + ".minimum-opening-amount", fallback.getMinimumOpeningAmount()));
        product.setMaxMovementsPerMonth(integer(prefix + ".max-movements-per-month", fallback.getMaxMovementsPerMonth()));
        return product;
    }

    /**
     * @return free counted movements per month
     */
    public int freePerMonth() {
        Integer value = integer("transactions.free-per-month", rules.getTransactions().getFreePerMonth());
        return value == null ? rules.getTransactions().getFreePerMonth() : value;
    }

    /**
     * @return commission charged after the free quota
     */
    public BigDecimal commissionAmount() {
        return decimal("transactions.commission-amount", rules.getTransactions().getCommissionAmount());
    }

    /**
     * @return VIP savings average required for the month
     */
    public BigDecimal minimumDailyAverage() {
        return decimal("vip.minimum-daily-average", rules.getVip().getMinimumDailyAverage());
    }

    /**
     * @return fee charged when a VIP savings account misses the average
     */
    public BigDecimal shortfallPenalty() {
        return decimal("vip.shortfall-penalty", rules.getVip().getShortfallPenalty());
    }

    /**
     * @return checking maintenance fee for a PYME customer
     */
    public BigDecimal pymeMaintenanceFee() {
        return decimal("pyme.maintenance-fee", rules.getPyme().getMaintenanceFee());
    }

    private void putProduct(Map<String, String> values, String prefix, BankRulesProperties.Product product) {
        values.put(prefix + ".maintenance-fee", product.getMaintenanceFee().toPlainString());
        values.put(prefix + ".minimum-opening-amount", product.getMinimumOpeningAmount().toPlainString());
        if (product.getMaxMovementsPerMonth() != null) {
            values.put(prefix + ".max-movements-per-month", product.getMaxMovementsPerMonth().toString());
        }
    }

    private BankRulesProperties.Product fallback(String accountType) {
        return switch (accountType) {
            case "SAVINGS" -> rules.getSavings();
            case "CHECKING" -> rules.getChecking();
            case "FIXED_TERM" -> rules.getFixedTerm();
            default -> throw new IllegalArgumentException("Unknown account type");
        };
    }

    private BigDecimal decimal(String field, BigDecimal fallback) {
        String raw = read(field);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return new BigDecimal(raw);
    }

    private Integer integer(String field, Integer fallback) {
        String raw = read(field);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Integer.valueOf(raw);
    }

    private String read(String field) {
        try {
            Object value = redis.opsForHash().get(PRODUCT_RULES, field);
            return value == null ? null : value.toString();
        } catch (RuntimeException ex) {
            LOG.warn("Product rule {} was not read from Redis: {}", field, ex.getMessage());
            return null;
        }
    }
}

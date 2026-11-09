package com.nttdata.bank.account.infrastructure.redis;

import com.nttdata.bank.account.config.BankRulesProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Account types and product rules written to and read from Redis.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCatalogTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    @Mock
    private HashOperations<String, Object, Object> hashes;

    @Test
    void seedWritesAccountTypesAndProductRules() {
        BankRulesProperties rules = new BankRulesProperties();
        rules.getChecking().setMaintenanceFee(new BigDecimal("12.00"));
        rules.getSavings().setMaxMovementsPerMonth(10);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForHash()).thenReturn(hashes);

        new ProductCatalog(redis, rules).seed();

        verify(values).set("catalog:account-types", "SAVINGS,CHECKING,FIXED_TERM");
        ArgumentCaptor<Map<String, String>> stored = ArgumentCaptor.forClass(Map.class);
        verify(hashes).putAll(eq("catalog:product-rules"), stored.capture());
        assertEquals("12.00", stored.getValue().get("checking.maintenance-fee"));
        assertEquals("10", stored.getValue().get("savings.max-movements-per-month"));
        assertEquals("1.50", stored.getValue().get("transactions.commission-amount"));
        assertEquals("500.00", stored.getValue().get("vip.minimum-daily-average"));
        assertEquals("0", stored.getValue().get("pyme.maintenance-fee"));
    }

    @Test
    void productRulesComeFromRedis() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForHash()).thenReturn(hashes);
        when(values.get("catalog:account-types")).thenReturn("SAVINGS,CHECKING,FIXED_TERM");
        when(hashes.get("catalog:product-rules", "checking.maintenance-fee")).thenReturn("7.50");
        when(hashes.get("catalog:product-rules", "transactions.free-per-month")).thenReturn("8");
        when(hashes.get("catalog:product-rules", "transactions.commission-amount")).thenReturn("2.00");
        when(hashes.get("catalog:product-rules", "vip.shortfall-penalty")).thenReturn("15");
        ProductCatalog catalog = new ProductCatalog(redis, new BankRulesProperties());

        assertEquals(0, new BigDecimal("7.50").compareTo(catalog.product("CHECKING").getMaintenanceFee()));
        assertEquals(8, catalog.freePerMonth());
        assertEquals(0, new BigDecimal("2.00").compareTo(catalog.commissionAmount()));
        assertEquals(0, new BigDecimal("15").compareTo(catalog.shortfallPenalty()));
    }

    @Test
    void aMissingCatalogUsesConfiguration() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("catalog:account-types")).thenThrow(new RuntimeException("down"));
        when(redis.opsForHash()).thenThrow(new RuntimeException("down"));
        BankRulesProperties rules = new BankRulesProperties();
        rules.getSavings().setMaintenanceFee(new BigDecimal("3"));
        ProductCatalog catalog = new ProductCatalog(redis, rules);

        assertEquals(List.of("SAVINGS", "CHECKING", "FIXED_TERM"), catalog.accountTypes());
        assertEquals(0, new BigDecimal("3").compareTo(catalog.product("SAVINGS").getMaintenanceFee()));
        assertEquals(5, catalog.freePerMonth());
    }

    @Test
    void anAccountTypeMissingFromRedisIsRejected() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("catalog:account-types")).thenReturn("SAVINGS");
        ProductCatalog catalog = new ProductCatalog(redis, new BankRulesProperties());

        assertThrows(IllegalArgumentException.class, () -> catalog.product("CHECKING"));
    }
}

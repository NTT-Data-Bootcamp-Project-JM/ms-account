package com.nttdata.bank.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The product rules live only in the config server. This binds that file onto {@link BankRules}
 * the same way the account service does at startup.
 */
class ConfigServerRulesBindingTest {

    @Test
    void configServerYamlBindsTheProductRules() throws Exception {
        Path config = Path.of("..", "ms-config-server", "src", "main", "resources", "config", "application.yml")
                .toAbsolutePath()
                .normalize();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", new FileSystemResource(config));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources.get(0)));
        BankRules rules = binder.bind("bank", Bindable.of(BankRules.class)).get();

        assertEquals(0, BigDecimal.ZERO.compareTo(rules.getSavings().getMaintenanceFee()));
        assertEquals(10, rules.getSavings().getMaxMovementsPerMonth());
        assertEquals(0, BigDecimal.ZERO.compareTo(rules.getSavings().getMinimumOpeningAmount()));
        assertEquals(0, new BigDecimal("12.00").compareTo(rules.getChecking().getMaintenanceFee()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rules.getChecking().getMinimumOpeningAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rules.getFixedTerm().getMaintenanceFee()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rules.getFixedTerm().getMinimumOpeningAmount()));
        assertEquals(5, rules.getTransactions().getFreePerMonth());
        assertEquals(0, new BigDecimal("1.50").compareTo(rules.getTransactions().getCommissionAmount()));
        assertEquals(0, new BigDecimal("500.00").compareTo(rules.getVip().getMinimumDailyAverage()));
        assertEquals(0, new BigDecimal("10.00").compareTo(rules.getVip().getShortfallPenalty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rules.getPyme().getMaintenanceFee()));
    }
}

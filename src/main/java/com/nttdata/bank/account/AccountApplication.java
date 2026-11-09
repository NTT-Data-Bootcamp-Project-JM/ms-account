package com.nttdata.bank.account;

import com.nttdata.bank.account.config.BankRulesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Owns bank accounts, their movements and debit cards.
 */
@SpringBootApplication
@EnableConfigurationProperties(BankRulesProperties.class)
public class AccountApplication {

    /**
     * Starts the account service.
     *
     * @param args program arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}

package com.nttdata.bank.account.infrastructure.webclient;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP settings for calls to {@code ms-credit}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.clients.credit")
public class CreditWebClientProperties {

    private String baseUrl;
    private String debtStatusPath;
    private String creditCardFlagPath;
}

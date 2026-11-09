package com.nttdata.bank.account.infrastructure.webclient;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP settings for calls to {@code ms-customer}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.clients.customer")
public class CustomerWebClientProperties {

    private String baseUrl;
    private String customerByIdPath;
}

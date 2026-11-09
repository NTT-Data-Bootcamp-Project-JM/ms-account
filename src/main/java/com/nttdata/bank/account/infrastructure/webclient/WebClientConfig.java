package com.nttdata.bank.account.infrastructure.webclient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Load-balanced WebClient used by outbound HTTP clients.
 */
@Configuration
@EnableConfigurationProperties({CustomerWebClientProperties.class, CreditWebClientProperties.class})
public class WebClientConfig {

    /**
     * @return builder that resolves {@code http://ms-*} hosts through Eureka
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}

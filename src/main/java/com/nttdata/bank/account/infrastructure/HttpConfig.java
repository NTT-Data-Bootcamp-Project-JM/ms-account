package com.nttdata.bank.account.infrastructure;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Eureka-aware HTTP client used to read customers and credit status.
 */
@Configuration
public class HttpConfig {

    /**
     * @return load-balanced RestTemplate
     */
    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }
}

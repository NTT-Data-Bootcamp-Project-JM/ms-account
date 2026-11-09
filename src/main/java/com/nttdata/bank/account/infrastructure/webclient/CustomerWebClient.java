package com.nttdata.bank.account.infrastructure.webclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nttdata.bank.account.config.ServiceTokenProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Outbound WebClient for {@code ms-customer}. Paths come from configuration.
 */
@Component
public class CustomerWebClient {

    private static final String CIRCUIT = "customer";

    private final WebClient webClient;
    private final CustomerWebClientProperties properties;
    private final ServiceTokenProvider tokens;
    private final CircuitBreakerRegistry circuitBreakers;
    private final TimeLimiterRegistry timeLimiters;

    /**
     * @param builder load-balanced WebClient builder
     * @param properties customer client settings
     * @param tokens service JWT issuer
     * @param circuitBreakers Resilience4j circuit breakers
     * @param timeLimiters Resilience4j time limiters
     */
    public CustomerWebClient(@LoadBalanced WebClient.Builder builder,
                          CustomerWebClientProperties properties,
                          ServiceTokenProvider tokens,
                          CircuitBreakerRegistry circuitBreakers,
                          TimeLimiterRegistry timeLimiters) {
        this.properties = properties;
        this.tokens = tokens;
        this.circuitBreakers = circuitBreakers;
        this.timeLimiters = timeLimiters;
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * @param customerId customer identifier
     * @return type and profile
     */
    public CustomerView customer(String customerId) {
        Mono<CustomerView> call = webClient.get()
                .uri(properties.getCustomerByIdPath(), customerId)
                .headers(headers -> headers.setBearerAuth(tokens.create()))
                .retrieve()
                .bodyToMono(CustomerView.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Customer was not found")))
                .map(view -> {
                    if (view.customerType() == null) {
                        throw new IllegalArgumentException("Customer was not found");
                    }
                    return view;
                });
        return execute(call);
    }

    private <T> T execute(Mono<T> call) {
        try {
            return circuitBreakers.circuitBreaker(CIRCUIT).executeCallable(
                    TimeLimiter.decorateFutureSupplier(timeLimiters.timeLimiter(CIRCUIT), call::toFuture));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("customer service is unavailable");
        }
    }

    /**
     * @param id customer identifier
     * @param customerType PERSONAL or BUSINESS
     * @param profile STANDARD, VIP or PYME
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerView(String id, String customerType, String profile) {
    }
}

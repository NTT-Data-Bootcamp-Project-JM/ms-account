package com.nttdata.bank.account.infrastructure.webclient;

import com.nttdata.bank.account.config.ServiceTokenProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Outbound WebClient for {@code ms-credit}. Paths come from configuration.
 */
@Component
public class CreditWebClient {

    private static final String CIRCUIT = "credit";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final CreditWebClientProperties properties;
    private final ServiceTokenProvider tokens;
    private final CircuitBreakerRegistry circuitBreakers;
    private final TimeLimiterRegistry timeLimiters;

    /**
     * @param builder load-balanced WebClient builder
     * @param properties credit client settings
     * @param tokens service JWT issuer
     * @param circuitBreakers Resilience4j circuit breakers
     * @param timeLimiters Resilience4j time limiters
     */
    public CreditWebClient(@LoadBalanced WebClient.Builder builder,
                        CreditWebClientProperties properties,
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
     * @return true when the credit service reports overdue debt
     */
    public boolean overdue(String customerId) {
        Mono<Boolean> call = webClient.get()
                .uri(properties.getDebtStatusPath(), customerId)
                .headers(headers -> headers.setBearerAuth(tokens.create()))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(body -> Boolean.TRUE.equals(body.get("overdue")))
                .defaultIfEmpty(false);
        return execute(call);
    }

    /**
     * @param customerId customer identifier
     * @return true when the customer has an active credit card
     */
    public boolean hasCreditCard(String customerId) {
        Mono<Boolean> call = webClient.get()
                .uri(properties.getCreditCardFlagPath(), customerId)
                .headers(headers -> headers.setBearerAuth(tokens.create()))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(body -> Boolean.TRUE.equals(body.get("exists")))
                .defaultIfEmpty(false);
        return execute(call);
    }

    private <T> T execute(Mono<T> call) {
        try {
            return circuitBreakers.circuitBreaker(CIRCUIT).executeCallable(
                    TimeLimiter.decorateFutureSupplier(timeLimiters.timeLimiter(CIRCUIT), call::toFuture));
        } catch (Exception ex) {
            throw new IllegalStateException("credit service is unavailable");
        }
    }
}

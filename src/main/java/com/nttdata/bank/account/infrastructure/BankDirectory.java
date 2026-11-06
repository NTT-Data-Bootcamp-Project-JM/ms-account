package com.nttdata.bank.account.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Customer and credit lookups. Both calls use Resilience4j with a two-second timeout.
 */
@Component
public class BankDirectory {

    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakers;
    private final TimeLimiterRegistry timeLimiters;
    private final SecretKey secretKey;
    private final long expirationMinutes;

    /**
     * @param restTemplate load-balanced client
     * @param circuitBreakers circuit breakers
     * @param timeLimiters time limiters
     * @param secret shared JWT secret
     * @param expirationMinutes service token lifetime
     */
    public BankDirectory(@LoadBalanced RestTemplate restTemplate,
                         CircuitBreakerRegistry circuitBreakers,
                         TimeLimiterRegistry timeLimiters,
                         @Value("${bank.security.jwt-secret}") String secret,
                         @Value("${bank.security.jwt-expiration-minutes}") long expirationMinutes) {
        this.restTemplate = restTemplate;
        this.circuitBreakers = circuitBreakers;
        this.timeLimiters = timeLimiters;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * @param customerId customer identifier
     * @return type and profile
     */
    public CustomerView customer(String customerId) {
        return call("customer", () -> {
            CustomerView view = exchange("http://ms-customer/api/v1/customers/" + customerId, CustomerView.class);
            if (view == null || view.customerType() == null) {
                throw new IllegalArgumentException("Customer was not found");
            }
            return view;
        });
    }

    /**
     * @param customerId customer identifier
     * @return true when the credit service reports overdue debt
     */
    public boolean overdue(String customerId) {
        return call("credit", () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = exchange("http://ms-credit/api/v1/debt-status/" + customerId, Map.class);
            return body != null && Boolean.TRUE.equals(body.get("overdue"));
        });
    }

    /**
     * @param customerId customer identifier
     * @return true when the customer has an active credit card
     */
    public boolean hasCreditCard(String customerId) {
        return call("credit", () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = exchange("http://ms-credit/api/v1/debt-status/" + customerId + "/credit-card", Map.class);
            return body != null && Boolean.TRUE.equals(body.get("exists"));
        });
    }

    private <T> T exchange(String url, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceToken());
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
    }

    private <T> T call(String name, Supplier<T> supplier) {
        try {
            return circuitBreakers.circuitBreaker(name).executeCallable(
                    TimeLimiter.decorateFutureSupplier(timeLimiters.timeLimiter(name),
                            () -> CompletableFuture.supplyAsync(supplier)));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(name + " service is unavailable");
        }
    }

    private String serviceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("ms-account")
                .claim("roles", List.of("SERVICE"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
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

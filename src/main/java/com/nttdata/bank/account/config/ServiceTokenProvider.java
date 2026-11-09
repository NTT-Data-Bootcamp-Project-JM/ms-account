package com.nttdata.bank.account.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Issues short-lived service JWTs for machine-to-machine calls.
 */
@Component
public class ServiceTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMinutes;
    private final String subject;

    /**
     * @param secret shared JWT secret
     * @param expirationMinutes token lifetime
     * @param applicationName caller service name
     */
    public ServiceTokenProvider(@Value("${bank.security.jwt-secret}") String secret,
                                @Value("${bank.security.jwt-expiration-minutes}") long expirationMinutes,
                                @Value("${spring.application.name}") String applicationName) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
        this.subject = applicationName;
    }

    /**
     * @return Bearer token value without the {@code Bearer} prefix
     */
    public String create() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("roles", List.of("SERVICE"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }
}

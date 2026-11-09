package com.nttdata.bank.account.domain.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One ledger line. The amount is always positive; the type decides the sign.
 */
@Getter
@Setter
@Document("movements")
public class MovementDocument {

    @Id
    private String id;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private Instant createdAt;
    private String operationId;
    private String description;
}

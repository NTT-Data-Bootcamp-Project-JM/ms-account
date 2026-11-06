package com.nttdata.bank.account.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Savings, checking or fixed-term account. This service is the only writer of the collection.
 */
@Getter
@Setter
@Document("accounts")
public class AccountDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String accountNumber;
    private String accountType;
    private String primaryCustomerId;
    private String customerType;
    private String profile;
    private List<String> holderIds = new ArrayList<>();
    private List<String> signerIds = new ArrayList<>();
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal maintenanceFee = BigDecimal.ZERO;
    private Integer maxMovementsPerMonth;
    private Integer fixedTermDay;
    private BigDecimal minimumDailyAverage;
    private String status;
}

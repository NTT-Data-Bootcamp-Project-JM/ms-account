package com.nttdata.bank.account.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Debit card linked to one main account. Yanki may move money only through that account.
 */
@Getter
@Setter
@Document("debit_cards")
public class DebitCardDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String cardNumber;
    private String customerId;
    private String mainAccountId;
    private String status;
}

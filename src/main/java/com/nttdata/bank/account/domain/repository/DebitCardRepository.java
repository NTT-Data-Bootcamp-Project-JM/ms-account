package com.nttdata.bank.account.domain.repository;

import com.nttdata.bank.account.domain.model.DebitCardDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Debit cards owned by the account service.
 */
public interface DebitCardRepository extends MongoRepository<DebitCardDocument, String> {

    /**
     * @param cardNumber card number received from Yanki
     * @return the card
     */
    Optional<DebitCardDocument> findByCardNumber(String cardNumber);

    /**
     * @param mainAccountId account used as the card's main account
     * @return cards pointing at that account
     */
    List<DebitCardDocument> findByMainAccountId(String mainAccountId);
}

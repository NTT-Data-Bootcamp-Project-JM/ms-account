package com.nttdata.bank.account.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Ledger lines of the account database.
 */
public interface MovementRepository extends MongoRepository<MovementDocument, String> {

    /**
     * @param accountId account identifier
     * @return movements of that account
     */
    List<MovementDocument> findByAccountIdOrderByCreatedAtAsc(String accountId);

    /**
     * @param accountId account identifier
     * @param types movement types that count toward limits
     * @param from inclusive start
     * @param to exclusive end
     * @return how many of those movements exist
     */
    long countByAccountIdAndTypeInAndCreatedAtBetween(String accountId, Collection<String> types, Instant from, Instant to);

    /**
     * @param accountId account identifier
     * @param from inclusive start
     * @param to exclusive end
     * @return movements in the interval
     */
    List<MovementDocument> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(String accountId, Instant from, Instant to);

    /**
     * @param operationId idempotency key
     * @return an already applied operation
     */
    Optional<MovementDocument> findByOperationId(String operationId);
}

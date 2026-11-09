package com.nttdata.bank.account.domain.repository;

import com.nttdata.bank.account.domain.model.AccountDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Account collection owned by this service.
 */
public interface AccountRepository extends MongoRepository<AccountDocument, String> {

    /**
     * @param customerId primary owner
     * @param accountType SAVINGS, CHECKING or FIXED_TERM
     * @param status account status
     * @return number of matching accounts
     */
    long countByPrimaryCustomerIdAndAccountTypeAndStatus(String customerId, String accountType, String status);
}

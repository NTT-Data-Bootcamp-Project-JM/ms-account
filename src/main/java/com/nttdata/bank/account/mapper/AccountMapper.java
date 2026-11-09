package com.nttdata.bank.account.mapper;

import com.nttdata.bank.account.api.dto.AccountResponse;
import com.nttdata.bank.account.api.dto.DebitCardResponse;
import com.nttdata.bank.account.api.dto.MovementResponse;
import com.nttdata.bank.account.domain.model.AccountDocument;
import com.nttdata.bank.account.domain.model.DebitCardDocument;
import com.nttdata.bank.account.domain.model.MovementDocument;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Maps account domain models to API DTOs.
 */
@Mapper(componentModel = "spring")
public interface AccountMapper {

    /**
     * @param document account aggregate
     * @return API response
     */
    AccountResponse toResponse(AccountDocument document);

    /**
     * @param documents account aggregates
     * @return API responses
     */
    List<AccountResponse> toAccountResponseList(List<AccountDocument> documents);

    /**
     * @param document movement aggregate
     * @return API response
     */
    MovementResponse toResponse(MovementDocument document);

    /**
     * @param documents movement aggregates
     * @return API responses
     */
    List<MovementResponse> toMovementResponseList(List<MovementDocument> documents);

    /**
     * @param document debit card aggregate
     * @return API response
     */
    DebitCardResponse toResponse(DebitCardDocument document);

    /**
     * @param documents debit card aggregates
     * @return API responses
     */
    List<DebitCardResponse> toDebitCardResponseList(List<DebitCardDocument> documents);
}
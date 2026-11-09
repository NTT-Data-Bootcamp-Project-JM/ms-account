package com.nttdata.bank.account.api.dto;

import java.util.List;

/**
 * @param holderIds holders
 * @param signerIds signers
 */
public record HoldersRequest(List<String> holderIds, List<String> signerIds) {
}
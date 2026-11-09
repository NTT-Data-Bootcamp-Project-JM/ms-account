package com.nttdata.bank.account.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nttdata.bank.account.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Kafka boundary with Yanki and with the credit service. Those flows do not use REST.
 */
@Component
public class AccountEvents {

    private static final Logger LOG = LoggerFactory.getLogger(AccountEvents.class);
    private final AccountService accounts;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param accounts account use cases
     * @param kafka Kafka producer
     */
    public AccountEvents(AccountService accounts, KafkaTemplate<String, String> kafka) {
        this.accounts = accounts;
        this.kafka = kafka;
    }

    /**
     * Applies a Yanki load or unload on the debit card's main account.
     *
     * @param payload JSON operation
     */
    @KafkaListener(topics = "yanki.wallet.operation")
    public void onWallet(String payload) {
        try {
            JsonNode node = json.readTree(payload);
            accounts.applyWalletOperation(
                    node.get("debitCardNumber").asText(),
                    new BigDecimal(node.get("amount").asText()),
                    node.get("direction").asText(),
                    node.get("operationId").asText());
            kafka.send("yanki.wallet.result", node.get("operationId").asText(),
                    "{\"operationId\":\"" + node.get("operationId").asText() + "\",\"status\":\"APPLIED\"}");
        } catch (Exception ex) {
            LOG.warn("Yanki operation was rejected: {}", ex.getMessage());
            try {
                String operationId = json.readTree(payload).get("operationId").asText();
                kafka.send("yanki.wallet.result", operationId,
                        "{\"operationId\":\"" + operationId + "\",\"status\":\"REJECTED\"}");
            } catch (Exception nested) {
                LOG.warn("Yanki rejection could not be published: {}", nested.getMessage());
            }
        }
    }

    /**
     * Asks the credit service to register a payment after the debit card account was debited.
     *
     * @param operationId idempotency key
     * @param productId loan or card
     * @param productKind LOAN or CARD
     * @param amount payment
     * @param payerCustomerId payer
     */
    public void requestCreditPayment(String operationId, String productId, String productKind,
                                     BigDecimal amount, String payerCustomerId) {
        String payload = "{\"operationId\":\"" + operationId + "\",\"productId\":\"" + productId
                + "\",\"productKind\":\"" + productKind + "\",\"amount\":\"" + amount.toPlainString()
                + "\",\"payerCustomerId\":\"" + payerCustomerId + "\"}";
        kafka.send("credit.payment.requested", operationId, payload);
    }
}

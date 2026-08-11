package co.com.yam.funds.api.dto;

import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String clientId,
        String fundId,
        String fundName,
        TransactionType type,
        BigDecimal amount,
        Instant timestamp
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getClientId(),
                transaction.getFundId(),
                transaction.getFundName(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTimestamp()
        );
    }
}

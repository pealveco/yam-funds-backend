package co.com.yam.funds.api.dto;

import co.com.yam.funds.model.subscription.Subscription;

import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionResponse(
        String clientId,
        String fundId,
        String fundName,
        BigDecimal amount,
        Instant subscribedAt
) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getClientId(),
                subscription.getFundId(),
                subscription.getFundName(),
                subscription.getAmount(),
                subscription.getSubscribedAt()
        );
    }
}

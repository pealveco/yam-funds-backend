package co.com.yam.funds.api.dto;

import co.com.yam.funds.model.subscription.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Fund subscription created for a client.")
public record SubscriptionResponse(
        @Schema(description = "Client identifier.", example = "client-001")
        String clientId,
        @Schema(description = "Fund identifier.", example = "1")
        String fundId,
        @Schema(description = "Fund display name.", example = "FPV_YAM_PACTUAL_RECAUDADORA")
        String fundName,
        @Schema(description = "Amount debited from the client balance.", example = "75000")
        BigDecimal amount,
        @Schema(description = "Subscription creation timestamp.", example = "2026-08-11T00:00:00Z", format = "date-time")
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

package co.com.yam.funds.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body used to subscribe a client to an investment fund.")
public record SubscribeToFundRequest(
        @Schema(description = "Fund identifier.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        String fundId
) {
}

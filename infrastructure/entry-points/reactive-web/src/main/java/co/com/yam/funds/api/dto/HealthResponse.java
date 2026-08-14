package co.com.yam.funds.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Health check response.")
public record HealthResponse(
        @Schema(description = "Current API status.", example = "UP")
        String status
) {
}

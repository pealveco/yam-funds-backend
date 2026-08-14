package co.com.yam.funds.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Standard API error response.")
public record ErrorResponse(
        @Schema(description = "Safe error message.", example = "Fund not found: 99")
        String error,
        @Schema(description = "HTTP status code.", example = "404")
        int status,
        @Schema(description = "Error timestamp.", example = "2026-08-11T00:00:00Z", format = "date-time")
        Instant timestamp
) {
}

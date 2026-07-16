package com.schwab.eventledger.gateway.dto;

import com.schwab.eventledger.gateway.domain.EventType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record CreateEventRequest(
        @NotBlank String eventId,
        @NotBlank String accountId,
        @NotNull EventType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code") String currency,
        @NotNull Instant eventTimestamp,
        Map<String, Object> metadata
) {
}

package com.schwab.eventledger.account.dto;

import com.schwab.eventledger.account.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public record ApplyTransactionRequest(
        @NotBlank String eventId,
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code") String currency,
        @NotNull Instant eventTimestamp
) {
}

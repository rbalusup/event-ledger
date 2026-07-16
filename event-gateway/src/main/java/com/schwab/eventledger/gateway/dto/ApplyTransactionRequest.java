package com.schwab.eventledger.gateway.dto;

import com.schwab.eventledger.gateway.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;

public record ApplyTransactionRequest(
        String eventId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp
) {
}

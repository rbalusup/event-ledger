package com.schwab.eventledger.gateway.dto;

import com.schwab.eventledger.gateway.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResult(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant appliedAt,
        boolean alreadyApplied
) {
}

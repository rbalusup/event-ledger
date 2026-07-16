package com.schwab.eventledger.gateway.dto;

import com.schwab.eventledger.gateway.domain.Event;
import com.schwab.eventledger.gateway.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EventResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant receivedAt,
        Map<String, Object> metadata,
        boolean duplicate
) {
    public static EventResponse from(Event event, Map<String, Object> metadata, boolean duplicate) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                event.getReceivedAt(),
                metadata,
                duplicate
        );
    }
}

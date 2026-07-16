package com.schwab.eventledger.gateway.dto;

public record ErrorResponse(
        String error,
        String message,
        String traceId
) {
}

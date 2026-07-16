package com.schwab.eventledger.account.dto;

public record ErrorResponse(
        String error,
        String message,
        String traceId
) {
}

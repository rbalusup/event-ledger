package com.schwab.eventledger.account.dto;

import com.schwab.eventledger.account.domain.Transaction;
import com.schwab.eventledger.account.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant appliedAt,
        boolean alreadyApplied
) {
    public static TransactionResponse from(Transaction transaction, boolean alreadyApplied) {
        return new TransactionResponse(
                transaction.getEventId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getEventTimestamp(),
                transaction.getAppliedAt(),
                alreadyApplied
        );
    }
}

package com.schwab.eventledger.account.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountDetailResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        List<TransactionResponse> recentTransactions
) {
}

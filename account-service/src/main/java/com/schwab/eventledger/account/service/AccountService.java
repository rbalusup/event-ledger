package com.schwab.eventledger.account.service;

import com.schwab.eventledger.account.domain.Transaction;
import com.schwab.eventledger.account.domain.TransactionType;
import com.schwab.eventledger.account.dto.AccountDetailResponse;
import com.schwab.eventledger.account.dto.ApplyTransactionRequest;
import com.schwab.eventledger.account.dto.BalanceResponse;
import com.schwab.eventledger.account.dto.TransactionResponse;
import com.schwab.eventledger.account.exception.AccountNotFoundException;
import com.schwab.eventledger.account.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AccountService {

    private final TransactionRepository transactionRepository;

    public AccountService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse applyTransaction(String accountId, ApplyTransactionRequest request) {
        var existing = transactionRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return TransactionResponse.from(existing.get(), true);
        }

        Transaction transaction = new Transaction(
                request.eventId(),
                accountId,
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                Instant.now()
        );

        try {
            Transaction saved = transactionRepository.save(transaction);
            return TransactionResponse.from(saved, false);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request applied the same eventId first; unique constraint on
            // event_id is the actual idempotency guard, this is just the race-loser path.
            Transaction winner = transactionRepository.findByEventId(request.eventId())
                    .orElseThrow(() -> e);
            return TransactionResponse.from(winner, true);
        }
    }

    public BalanceResponse getBalance(String accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
        if (transactions.isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        return new BalanceResponse(accountId, computeBalance(transactions), transactions.get(0).getCurrency());
    }

    public AccountDetailResponse getAccountDetails(String accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
        if (transactions.isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        List<TransactionResponse> recent = transactions.stream()
                .map(t -> TransactionResponse.from(t, false))
                .toList();
        return new AccountDetailResponse(accountId, computeBalance(transactions), transactions.get(0).getCurrency(), recent);
    }

    private BigDecimal computeBalance(List<Transaction> transactions) {
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction t : transactions) {
            balance = t.getType() == TransactionType.CREDIT
                    ? balance.add(t.getAmount())
                    : balance.subtract(t.getAmount());
        }
        return balance;
    }
}

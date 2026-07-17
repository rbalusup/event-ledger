package com.schwab.eventledger.account.service;

import com.schwab.eventledger.account.domain.TransactionType;
import com.schwab.eventledger.account.dto.ApplyTransactionRequest;
import com.schwab.eventledger.account.dto.TransactionResponse;
import com.schwab.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real race window in {@link AccountService#applyTransaction}: several
 * threads submit the same eventId at (as close to) the same instant, so more than one
 * can pass the initial findByEventId check before any of them has committed an insert.
 * The event_id unique constraint is the actual safety net here - this test asserts the
 * outcome it's supposed to guarantee, not which specific thread hits which code path.
 */
@SpringBootTest
class AccountServiceConcurrencyTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void concurrentSubmissionsOfTheSameEventIdApplyExactlyOnce() throws Exception {
        String eventId = "evt-race-" + UUID.randomUUID();
        String accountId = "acct-race";
        ApplyTransactionRequest request = new ApplyTransactionRequest(
                eventId, TransactionType.CREDIT, new BigDecimal("100.00"), "USD", Instant.now());

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<TransactionResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return accountService.applyTransaction(accountId, request);
            }));
        }

        List<TransactionResponse> responses = new ArrayList<>();
        for (Future<TransactionResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Every caller gets a normal response back - none see an unhandled exception
        // from losing the race.
        assertThat(responses).hasSize(threadCount);

        // Exactly one caller actually created the transaction; every other caller
        // observed it as already applied.
        long newlyAppliedCount = responses.stream().filter(r -> !r.alreadyApplied()).count();
        assertThat(newlyAppliedCount).isEqualTo(1);

        // Exactly one row was ever persisted for this eventId, so the balance reflects
        // a single application, not eight.
        List<String> matchingEventIds = transactionRepository.findAll().stream()
                .filter(t -> t.getEventId().equals(eventId))
                .map(t -> t.getEventId())
                .toList();
        assertThat(matchingEventIds).hasSize(1);

        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("100.00");
    }
}

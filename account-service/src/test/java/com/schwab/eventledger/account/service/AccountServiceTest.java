package com.schwab.eventledger.account.service;

import com.schwab.eventledger.account.domain.TransactionType;
import com.schwab.eventledger.account.dto.AccountDetailResponse;
import com.schwab.eventledger.account.dto.ApplyTransactionRequest;
import com.schwab.eventledger.account.dto.BalanceResponse;
import com.schwab.eventledger.account.dto.TransactionResponse;
import com.schwab.eventledger.account.exception.AccountNotFoundException;
import com.schwab.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AccountServiceTest {

    @Autowired
    private TransactionRepository transactionRepository;

    private AccountService accountService;

    @Test
    void balanceIsSumOfCreditsMinusDebits() {
        accountService = new AccountService(transactionRepository);
        Instant now = Instant.now();

        accountService.applyTransaction("acct-1", request("evt-1", TransactionType.CREDIT, "150.00", now));
        accountService.applyTransaction("acct-1", request("evt-2", TransactionType.DEBIT, "40.00", now.plusSeconds(1)));
        accountService.applyTransaction("acct-1", request("evt-3", TransactionType.CREDIT, "10.50", now.plusSeconds(2)));

        BalanceResponse balance = accountService.getBalance("acct-1");

        assertThat(balance.balance()).isEqualByComparingTo("120.50");
    }

    @Test
    void balanceIsOrderIndependentRegardlessOfArrivalOrder() {
        accountService = new AccountService(transactionRepository);
        Instant base = Instant.now();

        // Arrives out of chronological order: event timestamped "later" is applied first.
        accountService.applyTransaction("acct-2", request("evt-late", TransactionType.CREDIT, "100.00", base.plusSeconds(10)));
        accountService.applyTransaction("acct-2", request("evt-early", TransactionType.DEBIT, "30.00", base));

        BalanceResponse balance = accountService.getBalance("acct-2");

        assertThat(balance.balance()).isEqualByComparingTo("70.00");
    }

    @Test
    void recentTransactionsAreOrderedByEventTimestampAscendingRegardlessOfArrivalOrder() {
        accountService = new AccountService(transactionRepository);
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        accountService.applyTransaction("acct-3", request("evt-b", TransactionType.CREDIT, "50.00", base.plusSeconds(5)));
        accountService.applyTransaction("acct-3", request("evt-a", TransactionType.CREDIT, "20.00", base));
        accountService.applyTransaction("acct-3", request("evt-c", TransactionType.CREDIT, "30.00", base.plusSeconds(10)));

        AccountDetailResponse details = accountService.getAccountDetails("acct-3");

        assertThat(details.recentTransactions())
                .extracting(TransactionResponse::eventId)
                .containsExactly("evt-a", "evt-b", "evt-c");
    }

    @Test
    void resubmittingSameEventIdDoesNotAlterBalance() {
        accountService = new AccountService(transactionRepository);
        Instant now = Instant.now();
        ApplyTransactionRequest request = request("evt-dup", TransactionType.CREDIT, "150.00", now);

        TransactionResponse first = accountService.applyTransaction("acct-4", request);
        TransactionResponse second = accountService.applyTransaction("acct-4", request);

        assertThat(first.alreadyApplied()).isFalse();
        assertThat(second.alreadyApplied()).isTrue();
        assertThat(accountService.getBalance("acct-4").balance()).isEqualByComparingTo("150.00");
    }

    @Test
    void balanceQueryForUnknownAccountThrowsNotFound() {
        accountService = new AccountService(transactionRepository);

        assertThatThrownBy(() -> accountService.getBalance("does-not-exist"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private ApplyTransactionRequest request(String eventId, TransactionType type, String amount, Instant eventTimestamp) {
        return new ApplyTransactionRequest(eventId, type, new BigDecimal(amount), "USD", eventTimestamp);
    }
}

package com.schwab.eventledger.account.web;

import com.schwab.eventledger.account.dto.AccountDetailResponse;
import com.schwab.eventledger.account.dto.ApplyTransactionRequest;
import com.schwab.eventledger.account.dto.BalanceResponse;
import com.schwab.eventledger.account.dto.TransactionResponse;
import com.schwab.eventledger.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Apply a transaction to an account",
            description = "Idempotent by eventId: resubmitting the same eventId returns the original transaction "
                    + "with alreadyApplied=true and 200 OK instead of applying it again.")
    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody ApplyTransactionRequest request) {
        TransactionResponse response = accountService.applyTransaction(accountId, request);
        HttpStatus status = response.alreadyApplied() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "Get the current balance for an account",
            description = "Balance = sum(CREDIT amounts) - sum(DEBIT amounts), recomputed fresh on every call.")
    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        return accountService.getBalance(accountId);
    }

    @Operation(summary = "Get account details and transaction history",
            description = "Transactions are ordered by eventTimestamp ascending, regardless of the order they were applied in.")
    @GetMapping("/{accountId}")
    public AccountDetailResponse getAccountDetails(@PathVariable String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}

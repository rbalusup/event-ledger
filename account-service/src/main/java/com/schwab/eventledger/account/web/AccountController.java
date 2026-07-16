package com.schwab.eventledger.account.web;

import com.schwab.eventledger.account.dto.AccountDetailResponse;
import com.schwab.eventledger.account.dto.ApplyTransactionRequest;
import com.schwab.eventledger.account.dto.BalanceResponse;
import com.schwab.eventledger.account.dto.TransactionResponse;
import com.schwab.eventledger.account.service.AccountService;
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

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody ApplyTransactionRequest request) {
        TransactionResponse response = accountService.applyTransaction(accountId, request);
        HttpStatus status = response.alreadyApplied() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountDetailResponse getAccountDetails(@PathVariable String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}

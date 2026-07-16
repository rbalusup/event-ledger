package com.schwab.eventledger.gateway.service;

import com.schwab.eventledger.gateway.dto.ApplyTransactionRequest;
import com.schwab.eventledger.gateway.dto.TransactionResult;
import com.schwab.eventledger.gateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AccountServiceClient(RestTemplate accountServiceRestTemplate,
                                 @Value("${account-service.base-url}") String baseUrl) {
        this.restTemplate = accountServiceRestTemplate;
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    public TransactionResult applyTransaction(String accountId, ApplyTransactionRequest request) {
        try {
            return restTemplate.postForObject(
                    baseUrl + "/accounts/{accountId}/transactions",
                    request,
                    TransactionResult.class,
                    accountId);
        } catch (RestClientException e) {
            throw new AccountServiceUnavailableException(
                    "Account service is unavailable, please retry later", e);
        }
    }

    @SuppressWarnings("unused")
    private TransactionResult applyTransactionFallback(String accountId, ApplyTransactionRequest request, Throwable t) {
        if (t instanceof AccountServiceUnavailableException unavailable) {
            throw unavailable;
        }
        throw new AccountServiceUnavailableException("Account service is unavailable, please retry later", t);
    }
}

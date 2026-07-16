package com.schwab.eventledger.account.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super("No account found with id '" + accountId + "'");
    }
}

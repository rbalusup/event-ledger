package com.schwab.eventledger.gateway.exception;

public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.example.indexsystem.service.exception;

/**
 * Exception thrown when there is an issue connecting to a market data provider.
 */
public class ConnectionException extends Exception {

    /**
     * Constructs a new ConnectionException with the specified detail message.
     *
     * @param message the detail message.
     */
    public ConnectionException(String message) {
        super(message);
    }

    /**
     * Constructs a new ConnectionException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause of the exception.
     */
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

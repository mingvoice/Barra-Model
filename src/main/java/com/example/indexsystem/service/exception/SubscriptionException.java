package com.example.indexsystem.service.exception;

/**
 * Exception thrown when there is an issue subscribing or unsubscribing to market data for an instrument.
 */
public class SubscriptionException extends Exception {

    /**
     * Constructs a new SubscriptionException with the specified detail message.
     *
     * @param message the detail message.
     */
    public SubscriptionException(String message) {
        super(message);
    }

    /**
     * Constructs a new SubscriptionException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause of the exception.
     */
    public SubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}

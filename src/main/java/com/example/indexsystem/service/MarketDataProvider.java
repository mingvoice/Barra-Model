package com.example.indexsystem.service;

import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.service.exception.ConnectionException;
import com.example.indexsystem.service.exception.SubscriptionException;

/**
 * Interface for providing market data.
 * Implementations of this interface will connect to a market data source,
 * allow subscriptions for specific instruments, and notify listeners of updates.
 */
public interface MarketDataProvider {

    /**
     * Connects to the market data provider.
     *
     * @throws ConnectionException if an error occurs while connecting.
     */
    void connect() throws ConnectionException;

    /**
     * Disconnects from the market data provider.
     */
    void disconnect();

    /**
     * Subscribes to market data updates for a specific instrument.
     *
     * @param instrument The instrument to subscribe to.
     * @param listener   The listener that will receive market data updates.
     * @throws SubscriptionException if an error occurs while subscribing.
     */
    void subscribe(Instrument instrument, MarketDataListener listener) throws SubscriptionException;

    /**
     * Unsubscribes from market data updates for a specific instrument.
     *
     * @param instrument The instrument to unsubscribe from.
     * @param listener   The listener to remove.
     */
    void unsubscribe(Instrument instrument, MarketDataListener listener);
}

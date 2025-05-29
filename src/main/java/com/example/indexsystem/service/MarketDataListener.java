package com.example.indexsystem.service;

import com.example.indexsystem.model.MarketData;

/**
 * Interface for components that need to listen to market data updates.
 */
public interface MarketDataListener {

    /**
     * Called when new market data is received.
     *
     * @param data The market data object containing the update.
     */
    void onMarketData(MarketData data);
}

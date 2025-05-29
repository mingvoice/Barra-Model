package com.example.indexsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Data Transfer Object for market data messages, typically used for Kafka serialization.
 */
public final class MarketDataMessage {
    private final String instrumentSymbol;
    private final String exchange;
    private final String currency;
    private final BigDecimal lastPrice;
    private final long volume;
    private final LocalDateTime timestamp;

    /**
     * Constructs a MarketDataMessage.
     *
     * @param instrumentSymbol The symbol of the instrument (e.g., "AAPL").
     * @param exchange         The exchange where the instrument is traded (e.g., "NASDAQ").
     * @param currency         The currency of the instrument (e.g., "USD").
     * @param lastPrice        The last traded price.
     * @param volume           The trading volume.
     * @param timestamp        The timestamp of the data.
     */
    public MarketDataMessage(String instrumentSymbol, String exchange, String currency,
                             BigDecimal lastPrice, long volume, LocalDateTime timestamp) {
        this.instrumentSymbol = instrumentSymbol;
        this.exchange = exchange;
        this.currency = currency;
        this.lastPrice = lastPrice;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    // Getters
    public String getInstrumentSymbol() {
        return instrumentSymbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public long getVolume() {
        return volume;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketDataMessage that = (MarketDataMessage) o;
        return volume == that.volume &&
               Objects.equals(instrumentSymbol, that.instrumentSymbol) &&
               Objects.equals(exchange, that.exchange) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(lastPrice, that.lastPrice) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrumentSymbol, exchange, currency, lastPrice, volume, timestamp);
    }

    @Override
    public String toString() {
        return "MarketDataMessage{" +
               "instrumentSymbol='" + instrumentSymbol + '\'' +
               ", exchange='" + exchange + '\'' +
               ", currency='" + currency + '\'' +
               ", lastPrice=" + lastPrice +
               ", volume=" + volume +
               ", timestamp=" + timestamp +
               '}';
    }

    /**
     * Creates a MarketDataMessage from a MarketData object and its Instrument.
     * @param marketData The MarketData object.
     * @return A new MarketDataMessage instance.
     */
    public static MarketDataMessage fromMarketData(MarketData marketData) {
        if (marketData == null || marketData.getInstrument() == null) {
            return null; // Or throw an exception
        }
        Instrument instrument = marketData.getInstrument();
        return new MarketDataMessage(
                instrument.getSymbol(),
                instrument.getExchange(),
                instrument.getCurrency(),
                marketData.getLastPrice(),
                marketData.getVolume(),
                marketData.getTimestamp()
        );
    }
}

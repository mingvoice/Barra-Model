package com.example.indexsystem.model;

import java.util.Objects;

/**
 * Represents a financial instrument.
 */
public final class Instrument {
    private final String symbol;
    private final String exchange;
    private final String currency;

    /**
     * Constructs an Instrument object.
     *
     * @param symbol   The symbol of the instrument (e.g., "AAPL").
     * @param exchange The exchange where the instrument is traded (e.g., "NASDAQ").
     * @param currency The currency of the instrument (e.g., "USD").
     */
    public Instrument(String symbol, String exchange, String currency) {
        this.symbol = symbol;
        this.exchange = exchange;
        this.currency = currency;
    }

    /**
     * Gets the symbol of the instrument.
     *
     * @return The symbol.
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets the exchange where the instrument is traded.
     *
     * @return The exchange.
     */
    public String getExchange() {
        return exchange;
    }

    /**
     * Gets the currency of the instrument.
     *
     * @return The currency.
     */
    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instrument that = (Instrument) o;
        return Objects.equals(symbol, that.symbol) &&
               Objects.equals(exchange, that.exchange) &&
               Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, exchange, currency);
    }

    @Override
    public String toString() {
        return "Instrument{" +
               "symbol='" + symbol + '\'' +
               ", exchange='" + exchange + '\'' +
               ", currency='" + currency + '\'' +
               '}';
    }
}

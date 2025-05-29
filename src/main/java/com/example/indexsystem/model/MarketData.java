package com.example.indexsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents market data for a financial instrument.
 */
public final class MarketData {
    private final Instrument instrument;
    private final BigDecimal lastPrice;
    private final long volume;
    private final LocalDateTime timestamp;

    /**
     * Constructs a MarketData object.
     *
     * @param instrument The financial instrument.
     * @param lastPrice  The last traded price.
     * @param volume     The trading volume.
     * @param timestamp  The timestamp of the data.
     */
    public MarketData(Instrument instrument, BigDecimal lastPrice, long volume, LocalDateTime timestamp) {
        this.instrument = instrument;
        this.lastPrice = lastPrice;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    /**
     * Gets the financial instrument.
     *
     * @return The instrument.
     */
    public Instrument getInstrument() {
        return instrument;
    }

    /**
     * Gets the last traded price.
     *
     * @return The last price.
     */
    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    /**
     * Gets the trading volume.
     *
     * @return The volume.
     */
    public long getVolume() {
        return volume;
    }

    /**
     * Gets the timestamp of the data.
     *
     * @return The timestamp.
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketData that = (MarketData) o;
        return volume == that.volume &&
               Objects.equals(instrument, that.instrument) &&
               Objects.equals(lastPrice, that.lastPrice) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrument, lastPrice, volume, timestamp);
    }

    @Override
    public String toString() {
        return "MarketData{" +
               "instrument=" + instrument +
               ", lastPrice=" + lastPrice +
               ", volume=" + volume +
               ", timestamp=" + timestamp +
               '}';
    }
}

package com.example.indexsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents the calculated value of an index at a specific time.
 */
public final class CalculatedIndexValue {
    private final String indexId;
    private final BigDecimal value;
    private final LocalDateTime timestamp;

    /**
     * Constructs a CalculatedIndexValue object.
     *
     * @param indexId   The ID of the index.
     * @param value     The calculated value of the index.
     * @param timestamp The timestamp of the calculation.
     */
    public CalculatedIndexValue(String indexId, BigDecimal value, LocalDateTime timestamp) {
        this.indexId = indexId;
        this.value = value;
        this.timestamp = timestamp;
    }

    /**
     * Gets the ID of the index.
     *
     * @return The index ID.
     */
    public String getIndexId() {
        return indexId;
    }

    /**
     * Gets the calculated value of the index.
     *
     * @return The index value.
     */
    public BigDecimal getValue() {
        return value;
    }

    /**
     * Gets the timestamp of the calculation.
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
        CalculatedIndexValue that = (CalculatedIndexValue) o;
        return Objects.equals(indexId, that.indexId) &&
               Objects.equals(value, that.value) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexId, value, timestamp);
    }

    @Override
    public String toString() {
        return "CalculatedIndexValue{" +
               "indexId='" + indexId + '\'' +
               ", value=" + value +
               ", timestamp=" + timestamp +
               '}';
    }
}

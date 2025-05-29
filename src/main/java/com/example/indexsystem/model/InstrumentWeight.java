package com.example.indexsystem.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents the weight of an instrument in an index.
 */
public final class InstrumentWeight {
    private final Instrument instrument;
    private final BigDecimal weight;

    /**
     * Constructs an InstrumentWeight object.
     *
     * @param instrument The financial instrument.
     * @param weight     The weight of the instrument in the index. Can be null.
     */
    public InstrumentWeight(Instrument instrument, BigDecimal weight) {
        this.instrument = instrument;
        this.weight = weight;
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
     * Gets the weight of the instrument.
     *
     * @return The weight. Can be null.
     */
    public BigDecimal getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstrumentWeight that = (InstrumentWeight) o;
        return Objects.equals(instrument, that.instrument) &&
               Objects.equals(weight, that.weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrument, weight);
    }

    @Override
    public String toString() {
        return "InstrumentWeight{" +
               "instrument=" + instrument +
               ", weight=" + weight +
               '}';
    }
}

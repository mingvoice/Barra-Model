package com.example.indexsystem.calculator;

import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.service.IndexCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calculates an index value based on the price-weighted average
 * of its constituent instruments.
 *
 * The formula used is: Index Value = Sum (Price_i) / Divisor.
 * The Divisor is the number of constituents with available market data.
 * The weights in {@link InstrumentWeight} are not used in this calculation.
 */
public class PriceWeightedIndexCalculator implements IndexCalculator {

    private static final Logger LOGGER = Logger.getLogger(PriceWeightedIndexCalculator.class.getName());
    private static final int DEFAULT_SCALE = 4; // Default scale for index value

    private final int scale;

    /**
     * Constructs a PriceWeightedIndexCalculator with a default scale.
     */
    public PriceWeightedIndexCalculator() {
        this(DEFAULT_SCALE);
    }

    /**
     * Constructs a PriceWeightedIndexCalculator with a specified scale.
     *
     * @param scale The number of decimal places for the calculated index value.
     */
    public PriceWeightedIndexCalculator(int scale) {
        if (scale < 0) {
            LOGGER.log(Level.WARNING, "Scale cannot be negative. Using default scale: {0}", DEFAULT_SCALE);
            this.scale = DEFAULT_SCALE;
        } else {
            this.scale = scale;
        }
    }

    @Override
    public CalculatedIndexValue calculate(IndexDefinition definition, Map<Instrument, MarketData> currentMarketData) {
        if (definition == null) {
            LOGGER.log(Level.SEVERE, "Index definition cannot be null.");
            // Or throw new IllegalArgumentException("Index definition cannot be null.");
            return new CalculatedIndexValue("UNDEFINED", BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP), LocalDateTime.now());
        }
        if (currentMarketData == null) {
            LOGGER.log(Level.SEVERE, "Current market data cannot be null for index: {0}", definition.getIndexId());
            // Or throw new IllegalArgumentException("Current market data cannot be null.");
            return new CalculatedIndexValue(definition.getIndexId(), BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP), LocalDateTime.now());
        }

        BigDecimal sumOfPrices = BigDecimal.ZERO;
        int constituentsWithDataCount = 0;

        for (InstrumentWeight constituentWeight : definition.getConstituents()) {
            Instrument instrument = constituentWeight.getInstrument();
            // Note: constituentWeight.getWeight() is not used for price-weighted index.

            MarketData marketData = currentMarketData.get(instrument);
            if (marketData == null || marketData.getLastPrice() == null) {
                LOGGER.log(Level.WARNING, "Market data or last price not available for instrument {0} in index {1}. Skipping.",
                           new Object[]{instrument.getSymbol(), definition.getIndexId()});
                continue;
            }

            BigDecimal price = marketData.getLastPrice();
            sumOfPrices = sumOfPrices.add(price);
            constituentsWithDataCount++;

            LOGGER.log(Level.FINE, "Index: {0}, Instrument: {1}, Price: {2}",
                       new Object[]{definition.getIndexId(), instrument.getSymbol(), price});
        }

        if (constituentsWithDataCount == 0) {
            LOGGER.log(Level.INFO, "No market data available for any constituents in index {0}. Returning zero value.", definition.getIndexId());
            return new CalculatedIndexValue(definition.getIndexId(), BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP), LocalDateTime.now());
        }
        
        LOGGER.log(Level.INFO, "Sum of Prices for index {0}: {1}, Number of Constituents with Data: {2}", new Object[]{definition.getIndexId(), sumOfPrices, constituentsWithDataCount});


        BigDecimal divisor = new BigDecimal(constituentsWithDataCount);
        BigDecimal indexValue = sumOfPrices.divide(divisor, scale, RoundingMode.HALF_UP);

        return new CalculatedIndexValue(definition.getIndexId(), indexValue, LocalDateTime.now());
    }
}

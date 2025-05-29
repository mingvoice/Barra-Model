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
 * Calculates an index value based on the market capitalization weighted average
 * of its constituent instruments.
 *
 * The formula used is: Index Value = Sum (Price_i * Shares_i) / Divisor.
 * The number of shares for each instrument is taken from {@link InstrumentWeight#getWeight()}.
 */
public class MarketCapWeightedIndexCalculator implements IndexCalculator {

    private static final Logger LOGGER = Logger.getLogger(MarketCapWeightedIndexCalculator.class.getName());
    private static final BigDecimal DEFAULT_DIVISOR = new BigDecimal("1000000"); // Example fixed divisor
    private static final int DEFAULT_SCALE = 4; // Default scale for index value

    private final BigDecimal divisor;
    private final int scale;

    /**
     * Constructs a MarketCapWeightedIndexCalculator with a default divisor and scale.
     */
    public MarketCapWeightedIndexCalculator() {
        this(DEFAULT_DIVISOR, DEFAULT_SCALE);
    }

    /**
     * Constructs a MarketCapWeightedIndexCalculator with a specified divisor and scale.
     *
     * @param divisor The divisor to be used in the index calculation.
     * @param scale   The number of decimal places for the calculated index value.
     */
    public MarketCapWeightedIndexCalculator(BigDecimal divisor, int scale) {
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.log(Level.WARNING, "Divisor must be positive. Using default divisor: {0}", DEFAULT_DIVISOR);
            this.divisor = DEFAULT_DIVISOR;
        } else {
            this.divisor = divisor;
        }
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
            return new CalculatedIndexValue("UNDEFINED", BigDecimal.ZERO, LocalDateTime.now());
        }
        if (currentMarketData == null) {
            LOGGER.log(Level.SEVERE, "Current market data cannot be null for index: {0}", definition.getIndexId());
            // Or throw new IllegalArgumentException("Current market data cannot be null.");
            return new CalculatedIndexValue(definition.getIndexId(), BigDecimal.ZERO, LocalDateTime.now());
        }

        BigDecimal totalMarketCap = BigDecimal.ZERO;

        for (InstrumentWeight constituentWeight : definition.getConstituents()) {
            Instrument instrument = constituentWeight.getInstrument();
            BigDecimal shares = constituentWeight.getWeight(); // Assumed to be number of shares

            if (shares == null || shares.compareTo(BigDecimal.ZERO) <= 0) {
                LOGGER.log(Level.WARNING, "Instrument {0} in index {1} has invalid shares ({2}). Skipping.",
                           new Object[]{instrument.getSymbol(), definition.getIndexId(), shares});
                continue;
            }

            MarketData marketData = currentMarketData.get(instrument);
            if (marketData == null || marketData.getLastPrice() == null) {
                LOGGER.log(Level.WARNING, "Market data or last price not available for instrument {0} in index {1}. Skipping.",
                           new Object[]{instrument.getSymbol(), definition.getIndexId()});
                continue;
            }

            BigDecimal price = marketData.getLastPrice();
            BigDecimal instrumentMarketCap = price.multiply(shares);
            totalMarketCap = totalMarketCap.add(instrumentMarketCap);

            LOGGER.log(Level.FINE, "Index: {0}, Instrument: {1}, Price: {2}, Shares: {3}, MarketCap: {4}",
                       new Object[]{definition.getIndexId(), instrument.getSymbol(), price, shares, instrumentMarketCap});
        }

        if (totalMarketCap.compareTo(BigDecimal.ZERO) == 0) {
            LOGGER.log(Level.INFO, "Total market cap for index {0} is zero. This could be due to missing data or zero prices/shares.", definition.getIndexId());
            // Return zero value if no effective market cap could be calculated
            return new CalculatedIndexValue(definition.getIndexId(), BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP), LocalDateTime.now());
        }
        
        LOGGER.log(Level.INFO, "Total Market Cap for index {0}: {1}, Divisor: {2}", new Object[]{definition.getIndexId(), totalMarketCap, divisor});

        BigDecimal indexValue = totalMarketCap.divide(divisor, scale, RoundingMode.HALF_UP);

        return new CalculatedIndexValue(definition.getIndexId(), indexValue, LocalDateTime.now());
    }
}

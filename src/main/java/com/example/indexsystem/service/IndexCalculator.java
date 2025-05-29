package com.example.indexsystem.service;

import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.MarketData;

import java.util.Map;

/**
 * Interface for calculating index values.
 * Implementations of this interface will define specific algorithms
 * for calculating an index based on its definition and current market data.
 */
public interface IndexCalculator {

    /**
     * Calculates the value of an index.
     *
     * @param definition        The definition of the index to be calculated.
     * @param currentMarketData A map containing the latest market data for each instrument
     *                          relevant to the index definition. The key is the Instrument
     *                          and the value is the corresponding MarketData.
     * @return The calculated index value.
     */
    CalculatedIndexValue calculate(IndexDefinition definition, Map<Instrument, MarketData> currentMarketData);
}

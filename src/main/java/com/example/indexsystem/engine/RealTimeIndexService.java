package com.example.indexsystem.engine;

import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.service.IndexCalculator;
import com.example.indexsystem.service.IndexRegistry;
import com.example.indexsystem.service.MarketDataListener;
import com.example.indexsystem.service.MarketDataProvider;
import com.example.indexsystem.service.exception.SubscriptionException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the real-time calculation of indices.
 * This service loads index definitions, subscribes to market data for constituent instruments,
 * receives market data updates, triggers index recalculations, and stores the latest calculated values.
 */
public class RealTimeIndexService implements MarketDataListener {

    private static final Logger LOGGER = Logger.getLogger(RealTimeIndexService.class.getName());

    private final IndexRegistry indexRegistry;
    private final MarketDataProvider marketDataProvider;
    private final Map<String, IndexCalculator> calculators;

    // Concurrent collections for thread safety
    private final Map<Instrument, Set<IndexDefinition>> instrumentToIndicesMap;
    private final Map<Instrument, MarketData> currentMarketDataState;
    private final Map<String, CalculatedIndexValue> latestIndexValues;

    /**
     * Constructs a RealTimeIndexService.
     *
     * @param indexRegistry      The registry to access index definitions.
     * @param marketDataProvider The provider for market data.
     * @param calculators        A map of calculation algorithm names to {@link IndexCalculator} implementations.
     */
    public RealTimeIndexService(IndexRegistry indexRegistry, MarketDataProvider marketDataProvider,
                                Map<String, IndexCalculator> calculators) {
        if (indexRegistry == null) {
            throw new IllegalArgumentException("IndexRegistry cannot be null.");
        }
        if (marketDataProvider == null) {
            throw new IllegalArgumentException("MarketDataProvider cannot be null.");
        }
        if (calculators == null) {
            throw new IllegalArgumentException("Calculators map cannot be null.");
        }

        this.indexRegistry = indexRegistry;
        this.marketDataProvider = marketDataProvider;
        this.calculators = new HashMap<>(calculators); // Defensive copy

        this.instrumentToIndicesMap = new ConcurrentHashMap<>();
        this.currentMarketDataState = new ConcurrentHashMap<>();
        this.latestIndexValues = new ConcurrentHashMap<>();

        init();
    }

    /**
     * Initializes the service by loading index definitions and subscribing to market data.
     */
    private void init() {
        LOGGER.info("Initializing RealTimeIndexService...");
        Collection<IndexDefinition> definitions = indexRegistry.getAllIndices();
        if (definitions == null || definitions.isEmpty()) {
            LOGGER.warning("No index definitions found in the registry. Service will be idle.");
            return;
        }

        for (IndexDefinition definition : definitions) {
            if (definition == null || definition.getIndexId() == null) {
                LOGGER.warning("Encountered a null index definition or definition with null ID. Skipping.");
                continue;
            }
            LOGGER.log(Level.INFO, "Loading index definition: {0} ({1}) with algorithm: {2}",
                       new Object[]{definition.getName(), definition.getIndexId(), definition.getCalculationAlgorithm()});

            if (!calculators.containsKey(definition.getCalculationAlgorithm())) {
                LOGGER.log(Level.SEVERE, "No calculator found for algorithm: {0} required by index: {1}. This index will not be calculated.",
                           new Object[]{definition.getCalculationAlgorithm(), definition.getIndexId()});
                continue; // Skip this index if no calculator is available
            }

            if (definition.getConstituents() == null || definition.getConstituents().isEmpty()) {
                LOGGER.log(Level.WARNING, "Index definition {0} has no constituents. Skipping subscriptions for this index.", definition.getIndexId());
                continue;
            }

            for (InstrumentWeight constituentWeight : definition.getConstituents()) {
                if (constituentWeight == null || constituentWeight.getInstrument() == null) {
                    LOGGER.warning("Null constituent or instrument found in index: " + definition.getIndexId() + ". Skipping.");
                    continue;
                }
                Instrument instrument = constituentWeight.getInstrument();

                // Add mapping from instrument to this index definition
                instrumentToIndicesMap.computeIfAbsent(instrument, k -> ConcurrentHashMap.newKeySet()).add(definition);

                // Subscribe to market data for this instrument
                try {
                    LOGGER.log(Level.INFO, "Subscribing to market data for instrument: {0} for index: {1}",
                               new Object[]{instrument.getSymbol(), definition.getIndexId()});
                    marketDataProvider.subscribe(instrument, this);
                } catch (SubscriptionException e) {
                    LOGGER.log(Level.SEVERE, "Failed to subscribe to instrument " + instrument.getSymbol() +
                                             " for index " + definition.getIndexId(), e);
                    // Depending on policy, we might want to stop or continue
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "An unexpected error occurred during subscription for instrument " +
                                             instrument.getSymbol() + " for index " + definition.getIndexId(), e);
                }
            }
        }
        LOGGER.info("RealTimeIndexService initialization complete.");
    }

    /**
     * Called by the {@link MarketDataProvider} when new market data is received.
     * This method updates the internal market data state and triggers recalculation
     * for all affected indices.
     *
     * @param data The new {@link MarketData} update.
     */
    @Override
    public void onMarketData(MarketData data) {
        if (data == null || data.getInstrument() == null) {
            LOGGER.warning("Received null market data or data with null instrument. Ignoring.");
            return;
        }
        Instrument updatedInstrument = data.getInstrument();
        LOGGER.log(Level.FINE, "Received market data update for {0}: {1}", new Object[]{updatedInstrument.getSymbol(), data});

        // Update the current state for this instrument
        currentMarketDataState.put(updatedInstrument, data);

        // Identify affected indices
        Set<IndexDefinition> affectedIndices = instrumentToIndicesMap.get(updatedInstrument);
        if (affectedIndices == null || affectedIndices.isEmpty()) {
            LOGGER.log(Level.FINE, "No indices are affected by market data for instrument: {0}", updatedInstrument.getSymbol());
            return;
        }

        for (IndexDefinition indexDef : affectedIndices) {
            LOGGER.log(Level.FINER, "Recalculating index {0} due to update for {1}", new Object[]{indexDef.getIndexId(), updatedInstrument.getSymbol()});

            IndexCalculator calculator = calculators.get(indexDef.getCalculationAlgorithm());
            if (calculator == null) {
                LOGGER.log(Level.SEVERE, "No calculator found for algorithm: {0} of index: {1} during onMarketData. Skipping calculation.",
                           new Object[]{indexDef.getCalculationAlgorithm(), indexDef.getIndexId()});
                continue;
            }

            // Gather all necessary market data for this specific index
            Map<Instrument, MarketData> relevantMarketDataForIndex = new HashMap<>();
            boolean allDataAvailable = true;
            if (indexDef.getConstituents() == null) {
                 LOGGER.log(Level.WARNING, "Index definition {0} has null constituents list during onMarketData. Skipping.", indexDef.getIndexId());
                 continue;
            }

            for (InstrumentWeight iw : indexDef.getConstituents()) {
                if (iw == null || iw.getInstrument() == null) {
                    LOGGER.warning("Null constituent or instrument in index " + indexDef.getIndexId() + " during onMarketData. Skipping this constituent.");
                    continue;
                }
                Instrument constituentInstrument = iw.getInstrument();
                MarketData constituentData = currentMarketDataState.get(constituentInstrument);
                if (constituentData == null) {
                    // Data for this constituent hasn't arrived yet or is missing
                    // The calculator implementation should handle this (e.g., by skipping or using stale data if designed to)
                    LOGGER.log(Level.FINER, "Market data for constituent {0} of index {1} is not yet available in currentMarketDataState.",
                               new Object[]{constituentInstrument.getSymbol(), indexDef.getIndexId()});
                    allDataAvailable = false; // Or specific handling based on index calculation rules
                    // For now, we will pass nulls to the calculator to decide
                }
                relevantMarketDataForIndex.put(constituentInstrument, constituentData);
            }
            
            // If a calculator strictly needs all data, you might check allDataAvailable here.
            // However, it's often better to let the calculator decide how to handle missing constituent data.

            try {
                CalculatedIndexValue newIndexValue = calculator.calculate(indexDef, relevantMarketDataForIndex);
                if (newIndexValue != null) {
                    latestIndexValues.put(indexDef.getIndexId(), newIndexValue);
                    LOGGER.log(Level.INFO, "Calculated Index [{0} ({1})]: {2} at {3}",
                               new Object[]{indexDef.getName(), newIndexValue.getIndexId(), newIndexValue.getValue(), newIndexValue.getTimestamp()});
                } else {
                    LOGGER.log(Level.WARNING, "Calculator for index {0} returned null value.", indexDef.getIndexId());
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error calculating index " + indexDef.getIndexId(), e);
            }
        }
    }

    /**
     * Retrieves the latest calculated value for a given index ID.
     *
     * @param indexId The ID of the index.
     * @return The {@link CalculatedIndexValue}, or {@code null} if not found or not yet calculated.
     */
    public CalculatedIndexValue getLatestIndexValue(String indexId) {
        if (indexId == null) {
            return null;
        }
        return latestIndexValues.get(indexId);
    }

    /**
     * Returns an unmodifiable copy of the map containing all latest calculated index values.
     *
     * @return A map of index IDs to their latest {@link CalculatedIndexValue}.
     */
    public Map<String, CalculatedIndexValue> getAllLatestIndexValues() {
        return Collections.unmodifiableMap(new HashMap<>(latestIndexValues)); // Return a copy
    }

    /**
     * Shuts down the service, unsubscribing from all market data.
     */
    public void shutdown() {
        LOGGER.info("Shutting down RealTimeIndexService...");
        if (marketDataProvider != null) {
            // Unsubscribe from all instruments
            // instrumentToIndicesMap contains all instruments we've subscribed to as keys
            for (Instrument instrument : instrumentToIndicesMap.keySet()) {
                try {
                    LOGGER.log(Level.INFO, "Unsubscribing from instrument: {0}", instrument.getSymbol());
                    marketDataProvider.unsubscribe(instrument, this);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error unsubscribing from instrument " + instrument.getSymbol(), e);
                }
            }
        }
        instrumentToIndicesMap.clear();
        currentMarketDataState.clear();
        latestIndexValues.clear();
        LOGGER.info("RealTimeIndexService shut down complete.");
    }
}

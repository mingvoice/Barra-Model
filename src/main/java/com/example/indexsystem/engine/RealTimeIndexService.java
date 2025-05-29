package com.example.indexsystem.engine;

import com.example.indexsystem.cache.RedisManager; // Added for query methods
import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition; // Keep for getAllLatestIndexValues example
import com.example.indexsystem.registry.InMemoryIndexRegistry; // Keep for instanceof check
import com.example.indexsystem.service.IndexRegistry;
import com.example.indexsystem.service.MarketDataProvider;
import com.example.indexsystem.streaming.IndexCalculationStreamTopology; // For managing streams app lifecycle

// Kafka related imports are removed as Kafka consumption is now handled by Kafka Streams
// import com.example.indexsystem.service.MarketDataListener; // If no other direct listening
// import com.example.indexsystem.service.exception.SubscriptionException;

import java.util.Collection; // Keep if needed for IndexRegistry
import java.util.Collections;
import java.util.HashMap;
import java.util.List; // Keep for getAllLatestIndexValues example
import java.util.Map;
// import java.util.Properties; // No longer needed for Kafka Consumer properties here
// import java.util.Set; // No longer needed for instrumentToIndicesMap
// import java.util.concurrent.ConcurrentHashMap; // No longer needed for internal state maps
// import java.util.concurrent.ExecutorService; // No longer needed for Kafka Consumer thread here
// import java.util.concurrent.Executors; // No longer needed for Kafka Consumer thread here
// import java.util.concurrent.TimeUnit; // No longer needed for Kafka Consumer thread here
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages index definitions, orchestrates the loading of these definitions into Redis,
 * and controls the lifecycle of the Kafka Streams application.
 * It can also provide an API to query the latest index values from Redis.
 */
public class RealTimeIndexService { // No longer implements MarketDataListener

    private static final Logger LOGGER = Logger.getLogger(RealTimeIndexService.class.getName());

    private final IndexRegistry indexRegistry;
    private final MarketDataProvider marketDataProvider; // Retained for now, if it has other uses
    private final RedisManager redisManager; // For querying results and loading definitions
    private IndexCalculationStreamTopology streamsApp; // To manage Kafka Streams app lifecycle
    private final String kafkaBootstrapServers; // Needed to start the streams app

    /**
     * Constructs a RealTimeIndexService.
     *
     * @param indexRegistry         The registry to access index definitions.
     * @param marketDataProvider    Optional provider for other market data needs or direct publishing. Can be null.
     * @param redisManager          Manager for Redis interactions. Cannot be null.
     * @param kafkaBootstrapServers Kafka bootstrap servers for the streams application. Cannot be null or empty.
     * @throws IllegalArgumentException if indexRegistry, redisManager or kafkaBootstrapServers is null/empty.
     */
    public RealTimeIndexService(IndexRegistry indexRegistry, MarketDataProvider marketDataProvider,
                                RedisManager redisManager, String kafkaBootstrapServers) {
        if (indexRegistry == null) throw new IllegalArgumentException("IndexRegistry cannot be null.");
        if (redisManager == null) throw new IllegalArgumentException("RedisManager cannot be null.");
        if (kafkaBootstrapServers == null || kafkaBootstrapServers.trim().isEmpty()) {
            throw new IllegalArgumentException("Kafka Bootstrap Servers cannot be null or empty.");
        }

        this.indexRegistry = indexRegistry;
        this.marketDataProvider = marketDataProvider; // Can be null
        this.redisManager = redisManager;
        this.kafkaBootstrapServers = kafkaBootstrapServers;

        initService();
    }

    private void initService() {
        LOGGER.info("Initializing RealTimeIndexService...");
        // Load index definitions into Redis using the provided IndexRegistry
        if (this.indexRegistry instanceof InMemoryIndexRegistry) {
            LOGGER.info("Loading index definitions into Redis via InMemoryIndexRegistry...");
            ((InMemoryIndexRegistry) this.indexRegistry).loadDefinitionsIntoRedis();
        } else {
            LOGGER.warning("IndexRegistry is not an InMemoryIndexRegistry. Ensure definitions are loaded into Redis by other means if needed by Streams app.");
        }
        LOGGER.info("RealTimeIndexService initialization complete.");
    }

    /**
     * Starts the associated Kafka Streams application for index calculation.
     */
    public void startProcessing() {
        if (streamsApp != null && streamsApp.isRunning()) { // Assuming streamsApp has an isRunning() method
            LOGGER.warning("Kafka Streams application is already running.");
            return;
        }
        LOGGER.info("Starting Kafka Streams application (IndexCalculationStreamTopology)...");
        streamsApp = new IndexCalculationStreamTopology(this.kafkaBootstrapServers, this.redisManager);
        try {
            streamsApp.start(); 
            LOGGER.info("Kafka Streams application (IndexCalculationStreamTopology) started successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start IndexCalculationStreamTopology", e);
            streamsApp = null; // Ensure it's null if start failed
        }
    }

    /**
     * Stops the associated Kafka Streams application.
     */
    public void stopProcessing() {
        if (streamsApp != null) {
            LOGGER.info("Stopping Kafka Streams application (IndexCalculationStreamTopology)...");
            streamsApp.stop();
            streamsApp = null; 
            LOGGER.info("Kafka Streams application (IndexCalculationStreamTopology) stopped.");
        } else {
            LOGGER.info("Kafka Streams application was not running or not initialized by this service.");
        }
    }

    /**
     * Retrieves the latest calculated value for a given index ID from Redis.
     *
     * @param indexId The ID of the index.
     * @return The {@link CalculatedIndexValue}, or {@code null} if not found.
     */
    public CalculatedIndexValue getLatestIndexValue(String indexId) {
        if (indexId == null) return null;
        LOGGER.log(Level.FINE, "Querying Redis for latest value of index: {0}", indexId);
        return redisManager.getCalculatedIndexValue(indexId);
    }

    /**
     * Returns an unmodifiable map containing all latest calculated index values from Redis.
     * This implementation fetches values for indices known to the local IndexRegistry.
     *
     * @return A map of index IDs to their latest {@link CalculatedIndexValue}.
     */
    public Map<String, CalculatedIndexValue> getAllLatestIndexValues() {
        LOGGER.log(Level.INFO, "Querying Redis for all latest index values based on local IndexRegistry definitions.");
        Map<String, CalculatedIndexValue> values = new HashMap<>();
        List<IndexDefinition> definitions = indexRegistry.getAllIndices(); // From local memory
        for (IndexDefinition def : definitions) {
            CalculatedIndexValue val = redisManager.getCalculatedIndexValue(def.getIndexId());
            if (val != null) {
                values.put(def.getIndexId(), val);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Shuts down the RealTimeIndexService.
     * This includes stopping the Kafka Streams application if managed,
     * and disconnecting the MarketDataProvider if used.
     */
    public void shutdown() {
        LOGGER.info("Shutting down RealTimeIndexService...");
        stopProcessing(); // Stop the Kafka Streams application

        if (marketDataProvider != null && marketDataProvider.isConnected()) {
            LOGGER.info("Disconnecting MarketDataProvider...");
            marketDataProvider.disconnect();
        }
        // RedisManager is managed by IndexCalculationStreamTopology, which should close it on its stop().
        // If this service had its own RedisManager instance not shared, it would be closed here.
        LOGGER.info("RealTimeIndexService shut down complete.");
    }
}

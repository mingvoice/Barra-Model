package com.example.indexsystem.streaming;

import com.example.indexsystem.cache.RedisManager;
import com.example.indexsystem.calculator.MarketCapWeightedIndexCalculator;
import com.example.indexsystem.calculator.PriceWeightedIndexCalculator;
import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.model.MarketDataMessage;
import com.example.indexsystem.service.IndexCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.example.indexsystem.metrics.MetricsFacade; // Import MetricsFacade
import io.micrometer.core.instrument.MeterRegistry; // Import MeterRegistry
// Import Timer if specific Timer methods are used directly, though facade methods are preferred
// import io.micrometer.core.instrument.Timer; 

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IndexCalculationStreamTopology {

    private static final Logger LOGGER = Logger.getLogger(IndexCalculationStreamTopology.class.getName());
    private static final String APPLICATION_ID = "realtime-index-system-streams";
    private static final String INPUT_TOPIC_MARKET_DATA = "market-data-raw";
    private static final String OUTPUT_TOPIC_CALCULATED_INDICES = "calculated-index-values";
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;


    private final String bootstrapServers;
    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Map<String, IndexCalculator> calculators; // To hold calculator instances
    private final LoadingCache<String, IndexDefinition> indexDefinitionCache; // Caffeine cache
    private final MeterRegistry meterRegistry; // Micrometer registry

    private KafkaStreams streams;

    public IndexCalculationStreamTopology(String bootstrapServers, RedisManager redisManager) {
        this.bootstrapServers = bootstrapServers;
        this.redisManager = redisManager;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.meterRegistry = MetricsFacade.getRegistry(); // Obtain MeterRegistry from Facade

        // Initialize Caffeine cache for IndexDefinitions
        this.indexDefinitionCache = Caffeine.newBuilder()
            .maximumSize(10_000) // Max number of entries
            .expireAfterAccess(1, TimeUnit.HOURS) // Evict after 1 hour of inactivity
            .build(indexId -> { // CacheLoader: How to load an entry if not found
                LOGGER.log(Level.INFO, "Cache miss for IndexDefinition: {0}. Loading from Redis.", indexId);
                IndexDefinition def = this.redisManager.getIndexDefinition(indexId);
                if (def == null) {
                    LOGGER.log(Level.WARNING, "IndexDefinition {0} not found in Redis by cache loader.", indexId);
                    // Optionally, return a specific non-null object or throw an exception if an indexId *must* exist
                }
                return def; // Can return null if not found, and cache will not store it unless configured otherwise.
            });

        // Initialize calculators
        this.calculators = new HashMap<>();
        // TODO: Make divisor and scale configurable for calculators
        this.calculators.put("MarketCapWeighted", new MarketCapWeightedIndexCalculator(new java.math.BigDecimal("1000000"), 4));
        this.calculators.put("PriceWeighted", new PriceWeightedIndexCalculator(4));
        // Add other calculator types as needed
    }

    private Properties getStreamsConfig() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Process from the beginning of the topic
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0); // Disable caching for development/testing if needed
        return props;
    }

    public void start() {
        if (streams != null && (streams.state().isRunningOrRebalancing())) {
            LOGGER.warning("Kafka Streams application is already running or rebalancing.");
            return;
        }

        StreamsBuilder builder = new StreamsBuilder();
        defineTopology(builder);

        streams = new KafkaStreams(builder.build(), getStreamsConfig());

        // Set up an uncaught exception handler
        streams.setUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.log(Level.SEVERE, "Uncaught exception in Kafka Streams thread " + thread.getName(), throwable);
            // Consider a more robust error handling strategy here (e.g., shutdown, alert)
        });
        
        final CountDownLatch latch = new CountDownLatch(1);
        // Add a state listener to know when the streams are running
        streams.setStateListener((newState, oldState) -> {
            LOGGER.log(Level.INFO, "Kafka Streams state changed from {0} to {1}", new Object[]{oldState, newState});
            if (newState == KafkaStreams.State.RUNNING && oldState == KafkaStreams.State.REBALANCING) {
                 latch.countDown(); // Signal that streams are running
            } else if (newState == KafkaStreams.State.ERROR) {
                 LOGGER.severe("Kafka Streams entered ERROR state. Shutting down.");
                 System.exit(1); // Or handle more gracefully
            }
        });


        // Start the Kafka Streams threads
        try {
            streams.start();
            LOGGER.info("Kafka Streams application started.");
             // Wait for the streams to be in RUNNING state before proceeding in a non-daemon thread context
            if (!latch.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warning("Kafka Streams did not reach RUNNING state within the timeout.");
            }
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to start Kafka Streams application", e);
            System.exit(1); // Critical failure
        }

        // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                LOGGER.info("Shutdown hook called. Closing Kafka Streams application...");
                stop();
            }
        });
    }

    public void stop() {
        if (streams != null) {
            try {
                streams.close(java.time.Duration.ofSeconds(10)); // Give 10 seconds to close gracefully
                LOGGER.info("Kafka Streams application closed.");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing Kafka Streams application", e);
            } finally {
                streams = null;
            }
        }
        if (redisManager != null) {
            redisManager.close();
            LOGGER.info("RedisManager closed.");
        }
    }
    
    /**
     * Checks if the Kafka Streams application is currently running or rebalancing.
     * @return true if running or rebalancing, false otherwise.
     */
    public boolean isRunning() {
        return streams != null && streams.state().isRunningOrRebalancing();
    }

    // Serde for MarketDataMessage
    private Serde<MarketDataMessage> marketDataMessageSerde() {
        return Serdes.serdeFrom(
            (topic, data) -> { // Serializer
                try {
                    return objectMapper.writeValueAsBytes(data);
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error serializing MarketDataMessage", e);
                    return null;
                }
            },
            (topic, data) -> { // Deserializer
                try {
                    return objectMapper.readValue(data, MarketDataMessage.class);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error deserializing MarketDataMessage", e);
                    return null;
                }
            }
        );
    }
    
    // Serde for CalculatedIndexValue
    private Serde<CalculatedIndexValue> calculatedIndexValueSerde() {
        return Serdes.serdeFrom(
            (topic, data) -> { // Serializer
                try {
                    return objectMapper.writeValueAsBytes(data);
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error serializing CalculatedIndexValue", e);
                    return null;
                }
            },
            (topic, data) -> { // Deserializer
                try {
                    return objectMapper.readValue(data, CalculatedIndexValue.class);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error deserializing CalculatedIndexValue", e);
                    return null;
                }
            }
        );
    }


    void defineTopology(StreamsBuilder builder) {
        final Properties streamsProps = getStreamsConfig(); // Get props to access APPLICATION_ID_CONFIG

        KStream<String, MarketDataMessage> marketDataStream = builder
            .stream(INPUT_TOPIC_MARKET_DATA, Consumed.with(Serdes.String(), marketDataMessageSerde()))
            .peek((key, value) -> {
                LOGGER.log(Level.INFO, "Processing market data: Key={0}, Value={1}", new Object[]{key, value});
                MetricsFacade.incrementMarketDataConsumed(streamsProps.getProperty(StreamsConfig.APPLICATION_ID_CONFIG));
            })
            .filter((key, value) -> value != null); // Filter out bad messages

        // Step 1: Store Market Data in Redis
        marketDataStream.foreach((key, marketDataMessage) -> {
            if (marketDataMessage != null && marketDataMessage.getInstrumentSymbol() != null) {
                // Metrics for Redis operation are now inside RedisManager
                redisManager.setMarketData(marketDataMessage);
                 LOGGER.log(Level.FINE, "Stored {0} in Redis.", marketDataMessage.getInstrumentSymbol());
            } else {
                 LOGGER.log(Level.WARNING, "Skipping storing null market data or message with null symbol. Key: {0}", key);
            }
        });

        // Step 2: Fan-out to Affected Indices
        KStream<String, String> indexToCalculateStream = marketDataStream
            .flatMapValues(marketDataMessage -> {
                if (marketDataMessage == null || marketDataMessage.getInstrumentSymbol() == null) {
                    return Collections.emptyList();
                }
                String instrumentSymbol = marketDataMessage.getInstrumentSymbol();
                Set<String> affectedIndexIds = redisManager.getIndicesForInstrument(instrumentSymbol);
                if (affectedIndexIds.isEmpty()) {
                     LOGGER.log(Level.FINE, "No indices found for instrument: {0}", instrumentSymbol);
                } else {
                     LOGGER.log(Level.INFO, "Instrument {0} affects indices: {1}", new Object[]{instrumentSymbol, affectedIndexIds});
                }
                return affectedIndexIds; // Each indexId will become a new value in the stream for the same key (instrumentSymbol)
            });

        // Step 3: Trigger Index Calculation
        KStream<String, CalculatedIndexValue> calculatedIndexStream = indexToCalculateStream
            .map((instrumentSymbol, indexId) -> { // Key is instrumentSymbol, Value is indexId
                LOGGER.log(Level.INFO, "Attempting to calculate index: {0} due to update for instrument: {1}", new Object[]{indexId, instrumentSymbol});
                
                long startTimeNanos = System.nanoTime(); // Record start time for latency

                // Retrieve IndexDefinition from Caffeine cache
                IndexDefinition indexDef = indexDefinitionCache.get(indexId); 
                
                if (indexDef == null) {
                    // This case means the cache loader returned null, and it wasn't found in Redis.
                    LOGGER.log(Level.WARNING, "IndexDefinition not found via cache for ID: {0}. Cannot calculate.", indexId);
                    return KeyValue.pair(indexId, null);
                }

                Map<Instrument, MarketData> constituentMarketData = new HashMap<>();
                boolean allConstituentDataAvailable = true;
                if (indexDef.getConstituents() == null || indexDef.getConstituents().isEmpty()) {
                    LOGGER.log(Level.WARNING, "Index {0} has no constituents defined. Cannot calculate.", indexId);
                    return KeyValue.pair(indexId, null);
                }

                for (InstrumentWeight iw : indexDef.getConstituents()) {
                    Instrument constituent = iw.getInstrument();
                    MarketDataMessage mdMsg = redisManager.getMarketData(constituent.getSymbol());
                    if (mdMsg == null) {
                        LOGGER.log(Level.WARNING, "Market data not found in Redis for constituent {0} of index {1}. Calculation might be partial or fail.", new Object[]{constituent.getSymbol(), indexId});
                        allConstituentDataAvailable = false;
                        // Store null so calculator knows it's missing, or skip this index calculation run
                        constituentMarketData.put(constituent, null);
                    } else {
                        constituentMarketData.put(constituent, new MarketData(constituent, mdMsg.getLastPrice(), mdMsg.getVolume(), mdMsg.getTimestamp()));
                    }
                }
                
                // Decide if calculation can proceed if not all data is available.
                // For now, we proceed and let the calculator handle it.
                // if (!allConstituentDataAvailable) {
                //    LOGGER.log(Level.WARNING, "Not all constituent market data available for index {0}. Calculation may be skipped or inaccurate.", indexId);
                // }

                IndexCalculator calculator = calculators.get(indexDef.getCalculationAlgorithm());
                if (calculator == null) {
                    LOGGER.log(Level.SEVERE, "No calculator found for algorithm: {0} of index: {1}. Cannot calculate.", new Object[]{indexDef.getCalculationAlgorithm(), indexId});
                    return KeyValue.pair(indexId, null);
                }

                try {
                    CalculatedIndexValue calculatedValue = calculator.calculate(indexDef, constituentMarketData);
                    
                    long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
                    MetricsFacade.recordCalculationTime(durationMillis); // Record latency
                    
                    if (calculatedValue != null) {
                        // Metrics for Redis operation are now inside RedisManager
                        redisManager.setCalculatedIndexValue(calculatedValue);
                        MetricsFacade.incrementIndicesCalculated(); // Increment count
                        LOGGER.log(Level.INFO, "Successfully calculated and stored index {0}: {1}", new Object[]{indexId, calculatedValue.getValue()});
                        return KeyValue.pair(indexId, calculatedValue);
                    } else {
                        LOGGER.log(Level.WARNING, "Calculation for index {0} resulted in null value.", indexId);
                        return KeyValue.pair(indexId, null);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Exception during calculation of index " + indexId, e);
                    // Optionally record calculation error metric here
                    return KeyValue.pair(indexId, null); // Error case
                }
            })
            .filter((key, value) -> value != null); // Filter out null calculation results

        // Step 4: Output to Kafka topic (optional)
        calculatedIndexStream.to(OUTPUT_TOPIC_CALCULATED_INDICES, Produced.with(Serdes.String(), calculatedIndexValueSerde()));
        LOGGER.info("Calculated index values will be published to topic: " + OUTPUT_TOPIC_CALCULATED_INDICES);
    }


    public static void main(String[] args) {
        // Configure logging to console for easier debugging
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT.%1$tL] [%4$-7s] %3$s - %5$s %6$s%n");
        Logger rootLogger = Logger.getLogger(""); // Get root logger
        rootLogger.setLevel(Level.INFO); // Set desired level for root
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) { // Change level on existing handlers
            if (handler instanceof java.util.logging.ConsoleHandler) {
                handler.setLevel(Level.INFO); // Set console handler to INFO
            }
        }
        
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") != null ?
                                  System.getenv("KAFKA_BOOTSTRAP_SERVERS") : DEFAULT_BOOTSTRAP_SERVERS;
        String redisHost = System.getenv("REDIS_HOST") != null ?
                           System.getenv("REDIS_HOST") : DEFAULT_REDIS_HOST;
        int redisPort = System.getenv("REDIS_PORT") != null ?
                        Integer.parseInt(System.getenv("REDIS_PORT")) : DEFAULT_REDIS_PORT;

        LOGGER.info("Starting IndexCalculationStreamTopology with Kafka: " + bootstrapServers + ", Redis: " + redisHost + ":" + redisPort);

        RedisManager redisManager = new RedisManager(redisHost, redisPort);
        
        // Here, you might want to initialize InMemoryIndexRegistry and load definitions to Redis
        // if this application is also responsible for that.
        // Example:
        // InMemoryIndexRegistry registry = new InMemoryIndexRegistry(redisManager);
        // ... add definitions to registry ...
        // registry.loadDefinitionsIntoRedis();
        // LOGGER.info("Index definitions loaded into Redis.");


        IndexCalculationStreamTopology streamApp = new IndexCalculationStreamTopology(bootstrapServers, redisManager);
        streamApp.start();
    }
}

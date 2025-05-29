package com.example.indexsystem.provider.mock;

import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.service.MarketDataListener;
import com.example.indexsystem.service.MarketDataProvider;
import com.example.indexsystem.service.exception.ConnectionException;
import com.example.indexsystem.service.exception.SubscriptionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.model.MarketDataMessage;
import com.example.indexsystem.service.MarketDataListener;
import com.example.indexsystem.service.MarketDataProvider;
import com.example.indexsystem.service.exception.ConnectionException;
import com.example.indexsystem.service.exception.SubscriptionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.model.MarketDataMessage;
import com.example.indexsystem.service.MarketDataListener;
import com.example.indexsystem.service.MarketDataProvider;
import com.example.indexsystem.service.exception.ConnectionException;
import com.example.indexsystem.service.exception.SubscriptionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A mock implementation of {@link MarketDataProvider} that simulates market data
 * for a predefined list of instruments and sends it to a Kafka topic.
 */
public class MockMarketDataProvider implements MarketDataProvider {

    private static final Logger LOGGER = Logger.getLogger(MockMarketDataProvider.class.getName());
    private static final int PRICE_SCALE = 2; // For currency
    private static final BigDecimal MAX_PRICE_CHANGE_PERCENT = new BigDecimal("0.02"); // +/- 2%
    private static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String KAFKA_TOPIC = "market-data-raw";
    private static final int DEFAULT_NUMBER_OF_INSTRUMENTS = 100;
    private static final int DEFAULT_MESSAGES_PER_SECOND_PER_INSTRUMENT = 1;


    private final List<Instrument> supportedInstruments;
    private final Map<Instrument, List<MarketDataListener>> subscriptions;
    private final Map<Instrument, MarketData> lastMarketData;
    private final Random random = new Random();
    private final String kafkaBootstrapServers;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry; // Micrometer registry
    private final Counter messagesProducedCounter;
    private final int messagesPerSecondPerInstrument; // For load configuration
    private final int numberOfInstruments; // For load configuration

    private ScheduledExecutorService executorService;
    private KafkaProducer<String, String> kafkaProducer;
    private boolean isConnected = false;


    public MockMarketDataProvider(MeterRegistry meterRegistry) {
        this(DEFAULT_NUMBER_OF_INSTRUMENTS, DEFAULT_MESSAGES_PER_SECOND_PER_INSTRUMENT, DEFAULT_KAFKA_BOOTSTRAP_SERVERS, meterRegistry);
    }

    public MockMarketDataProvider(int numberOfInstruments, int messagesPerSecondPerInstrument, String kafkaBootstrapServers, MeterRegistry meterRegistry) {
        this.numberOfInstruments = numberOfInstruments;
        this.messagesPerSecondPerInstrument = messagesPerSecondPerInstrument;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.meterRegistry = meterRegistry;

        this.supportedInstruments = Collections.unmodifiableList(generateInstruments(this.numberOfInstruments));
        this.subscriptions = new ConcurrentHashMap<>();
        this.lastMarketData = new ConcurrentHashMap<>();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Initialize base prices
        for (Instrument instrument : supportedInstruments) {
            BigDecimal basePrice = new BigDecimal(100 + random.nextInt(900)); // Random base price
            this.lastMarketData.put(instrument, new MarketData(instrument, basePrice, 0L, LocalDateTime.now()));
        }
        
        this.messagesProducedCounter = Counter.builder("marketdata.produced.total")
            .description("Total market data messages produced")
            .register(this.meterRegistry);

        LOGGER.info(String.format("MockMarketDataProvider initialized for %d instruments, %d msg/sec/inst. Kafka target: %s",
                    this.numberOfInstruments, this.messagesPerSecondPerInstrument, this.kafkaBootstrapServers));
    }
    
    private List<Instrument> generateInstruments(int count) {
        return IntStream.range(1, count + 1)
            .mapToObj(i -> new Instrument("INST_" + i, "MOCK_EXCH", "USD"))
            .collect(Collectors.toList());
    }


    private void initKafkaProducer() {
        if (this.kafkaProducer != null) {
            return;
        }
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.kafkaBootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Add other producer configs as needed (e.g., acks, retries)
        try {
            this.kafkaProducer = new KafkaProducer<>(props);
            LOGGER.info("KafkaProducer initialized successfully to " + this.kafkaBootstrapServers);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize KafkaProducer to " + this.kafkaBootstrapServers, e);
            // Depending on policy, could throw a ConnectionException here or handle in connect()
        }
    }

    private BigDecimal generateNextPrice(Instrument instrument) {
        MarketData previousData = lastMarketData.get(instrument);
        BigDecimal previousPrice = (previousData != null) ? previousData.getLastPrice() : new BigDecimal("100.00");
        BigDecimal changePercent = BigDecimal.valueOf((random.nextDouble() - 0.5) * 2)
                                            .multiply(MAX_PRICE_CHANGE_PERCENT);
        BigDecimal changeAmount = previousPrice.multiply(changePercent);
        BigDecimal newPrice = previousPrice.add(changeAmount);
        if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
            newPrice = BigDecimal.ZERO;
        }
        return newPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private void marketDataGenerationTask() {
        if (!isConnected || kafkaProducer == null) {
            LOGGER.log(Level.WARNING, "Not connected or Kafka producer not initialized. Skipping data generation.");
            return;
        }
        for (Instrument instrument : supportedInstruments) {
            BigDecimal newPrice = generateNextPrice(instrument);
            long volume = 1000 + random.nextInt(5000);
            LocalDateTime timestamp = LocalDateTime.now();
            MarketData marketData = new MarketData(instrument, newPrice, volume, timestamp);
            lastMarketData.put(instrument, marketData);

            MarketDataMessage messageDto = MarketDataMessage.fromMarketData(marketData);
            if (messageDto == null) {
                LOGGER.log(Level.WARNING, "Failed to create MarketDataMessage from MarketData for {0}", instrument.getSymbol());
                continue;
            }

            try {
                String jsonMessage = objectMapper.writeValueAsString(messageDto);
                ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, instrument.getSymbol(), jsonMessage);

                kafkaProducer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        LOGGER.log(Level.SEVERE, "Failed to send market data to Kafka for " + instrument.getSymbol(), exception);
                    } else {
                        LOGGER.log(Level.FINE, "Sent market data to Kafka for {0}: {1} to topic {2} partition {3}",
                                   new Object[]{instrument.getSymbol(), jsonMessage, metadata.topic(), metadata.partition()});
                    }
                });
            } catch (JsonProcessingException e) {
                LOGGER.log(Level.SEVERE, "Failed to serialize MarketDataMessage to JSON for " + instrument.getSymbol(), e);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error during Kafka send for " + instrument.getSymbol(), e);
            }
        }
    }

    @Override
    public void connect() throws ConnectionException {
        if (isConnected) {
            LOGGER.info("Already connected.");
            return;
        }
        LOGGER.info("Connecting to MockMarketDataProvider and initializing Kafka producer...");
        initKafkaProducer();
        if (this.kafkaProducer == null) {
            throw new ConnectionException("Failed to initialize Kafka producer. Check logs for details.");
        }

        executorService = Executors.newSingleThreadScheduledExecutor();
        // Calculate total messages per second and thereby the period for the scheduled task.
        // The task will send one message for each instrument in one run.
        // So, if we want X messages per instrument per second, the task should run X times per second.
        // Period in milliseconds = 1000 / messagesPerSecondPerInstrument.
        long periodMillis = 1000 / Math.max(1, this.messagesPerSecondPerInstrument); 
                                        // Ensure at least 1 msg/sec/inst if config is 0 or less, to avoid division by zero.
                                        // Or, handle 0 msg/sec as a special case (don't schedule).
        if (this.messagesPerSecondPerInstrument <= 0) {
             LOGGER.warning("messagesPerSecondPerInstrument is <= 0, data generation will not be scheduled.");
        } else {
            executorService.scheduleAtFixedRate(this::marketDataGenerationTask, 0, periodMillis, TimeUnit.MILLISECONDS);
            LOGGER.info(String.format("Market data generation scheduled with period %d ms for %d instruments.", periodMillis, this.numberOfInstruments));
        }
        
        isConnected = true;
        LOGGER.info("MockMarketDataProvider connected. Data generation to Kafka topic '" + KAFKA_TOPIC + "' potentially started (if rate > 0).");
    }

    @Override
    public void disconnect() {
        if (!isConnected) {
            LOGGER.info("Already disconnected.");
            return;
        }
        LOGGER.info("Disconnecting from MockMarketDataProvider...");
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (kafkaProducer != null) {
            try {
                kafkaProducer.flush();
                kafkaProducer.close(java.time.Duration.ofSeconds(5)); // Wait for 5s for close
                LOGGER.info("KafkaProducer closed.");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing KafkaProducer", e);
            }
            kafkaProducer = null;
        }
        isConnected = false;
        LOGGER.info("MockMarketDataProvider disconnected.");
    }

    @Override
    public void subscribe(Instrument instrument, MarketDataListener listener) throws SubscriptionException {
        LOGGER.log(Level.INFO, "Subscribe called for {0}. This MockMarketDataProvider now sends data to Kafka, " +
                               "and direct listener notification is deprecated. Listener will not be actively called by this provider.",
                               instrument.getSymbol());
        if (instrument == null || listener == null) {
            throw new IllegalArgumentException("Instrument and listener cannot be null.");
        }
        // Keep track of subscriptions for compatibility or potential future use, but don't use for data sending
        subscriptions.computeIfAbsent(instrument, k -> new ArrayList<>()).add(listener);
    }

    @Override
    public void unsubscribe(Instrument instrument, MarketDataListener listener) {
         LOGGER.log(Level.INFO, "Unsubscribe called for {0}. Direct listener notification is deprecated.", instrument.getSymbol());
        if (instrument == null || listener == null) {
            LOGGER.log(Level.WARNING, "Attempted to unsubscribe with null instrument or listener.");
            return;
        }
        List<MarketDataListener> instrumentListeners = subscriptions.get(instrument);
        if (instrumentListeners != null) {
            instrumentListeners.remove(listener);
            if (instrumentListeners.isEmpty()) {
                subscriptions.remove(instrument);
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public List<Instrument> getSupportedInstruments() {
        return supportedInstruments;
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

        String kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") != null ?
                                      System.getenv("KAFKA_BOOTSTRAP_SERVERS") : DEFAULT_KAFKA_BOOTSTRAP_SERVERS;
        
        int numInstruments = System.getenv("NUM_INSTRUMENTS") != null ?
                             Integer.parseInt(System.getenv("NUM_INSTRUMENTS")) : DEFAULT_NUMBER_OF_INSTRUMENTS;
        
        int msgsPerSecPerInst = System.getenv("MSGS_PER_SEC_PER_INST") != null ?
                                Integer.parseInt(System.getenv("MSGS_PER_SEC_PER_INST")) : DEFAULT_MESSAGES_PER_SECOND_PER_INSTRUMENT;

        LOGGER.info(String.format("Starting MockMarketDataProvider with Kafka: %s, Instruments: %d, Rate: %d msg/sec/inst",
                                  kafkaBootstrapServers, numInstruments, msgsPerSecPerInst));

        // Initialize MetricsFacade to setup JMX registry (or other registries)
        MeterRegistry meterRegistry = com.example.indexsystem.metrics.MetricsFacade.getRegistry();

        MockMarketDataProvider provider = new MockMarketDataProvider(numInstruments, msgsPerSecPerInst, kafkaBootstrapServers, meterRegistry);

        try {
            provider.connect();
            LOGGER.info("MockMarketDataProvider connected and running.");
            
            // Keep the main thread alive until shutdown is triggered (e.g., by SIGTERM)
            // Add a shutdown hook to gracefully disconnect
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown hook called. Disconnecting MockMarketDataProvider...");
                provider.disconnect();
                LOGGER.info("MockMarketDataProvider disconnected.");
            }));
            
            // Keep the main thread alive indefinitely or until interrupted
            Thread.currentThread().join();
            
        } catch (ConnectionException e) {
            LOGGER.log(Level.SEVERE, "Failed to connect MockMarketDataProvider", e);
            System.exit(1);
        } catch (InterruptedException e) {
            LOGGER.info("MockMarketDataProvider main thread interrupted. Exiting.");
            Thread.currentThread().interrupt(); // Restore interruption status
        } finally {
            if (provider.isConnected()) {
                provider.disconnect();
            }
        }
    }
}

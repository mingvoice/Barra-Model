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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A mock implementation of {@link MarketDataProvider} that simulates market data
 * for a predefined list of instruments.
 */
public class MockMarketDataProvider implements MarketDataProvider {

    private static final Logger LOGGER = Logger.getLogger(MockMarketDataProvider.class.getName());
    private static final int PRICE_SCALE = 2; // For currency
    private static final BigDecimal MAX_PRICE_CHANGE_PERCENT = new BigDecimal("0.02"); // +/- 2%

    private final List<Instrument> supportedInstruments;
    private final Map<Instrument, List<MarketDataListener>> subscriptions;
    private final Map<Instrument, MarketData> lastMarketData;
    private final Random random = new Random();

    private ScheduledExecutorService executorService;
    private boolean isConnected = false;

    /**
     * Constructs a MockMarketDataProvider.
     *
     * @param instrumentsToSimulate A list of instruments for which this provider will simulate data.
     *                              Initial base prices are set for these instruments.
     */
    public MockMarketDataProvider(List<Instrument> instrumentsToSimulate) {
        this.supportedInstruments = Collections.unmodifiableList(new ArrayList<>(instrumentsToSimulate));
        this.subscriptions = new ConcurrentHashMap<>();
        this.lastMarketData = new ConcurrentHashMap<>();

        // Initialize base prices
        for (Instrument instrument : supportedInstruments) {
            BigDecimal basePrice = BigDecimal.ZERO;
            // Example base prices, can be made more configurable
            if ("AAPL".equals(instrument.getSymbol())) {
                basePrice = new BigDecimal("150.00");
            } else if ("GOOGL".equals(instrument.getSymbol())) {
                basePrice = new BigDecimal("2500.00");
            } else if ("MSFT".equals(instrument.getSymbol())) {
                basePrice = new BigDecimal("300.00");
            } else {
                basePrice = new BigDecimal(100 + random.nextInt(900)); // Default for others
            }
            this.lastMarketData.put(instrument, new MarketData(instrument, basePrice, 0L, LocalDateTime.now()));
        }
        LOGGER.info("MockMarketDataProvider initialized for instruments: " + supportedInstruments.stream().map(Instrument::getSymbol).collect(Collectors.joining(", ")));
    }

    /**
     * Generates the next pseudo-random price for an instrument based on its previous price.
     *
     * @param instrument The instrument for which to generate a price.
     * @return The new pseudo-random price.
     */
    private BigDecimal generateNextPrice(Instrument instrument) {
        MarketData previousData = lastMarketData.get(instrument);
        BigDecimal previousPrice = (previousData != null) ? previousData.getLastPrice() : new BigDecimal("100.00"); // Default if no history

        // Calculate change: previousPrice * (random_double_between -0.5 and 0.5) * MAX_PRICE_CHANGE_PERCENT
        BigDecimal changePercent = BigDecimal.valueOf((random.nextDouble() - 0.5) * 2) // Range -1.0 to 1.0
                                            .multiply(MAX_PRICE_CHANGE_PERCENT);         // Scale by max change
        BigDecimal changeAmount = previousPrice.multiply(changePercent);
        BigDecimal newPrice = previousPrice.add(changeAmount);

        // Ensure price doesn't go negative and scale it
        if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
            newPrice = BigDecimal.ZERO;
        }
        return newPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Task to generate and publish market data updates.
     */
    private void marketDataGenerationTask() {
        if (!isConnected) {
            return;
        }
        for (Instrument instrument : supportedInstruments) {
            BigDecimal newPrice = generateNextPrice(instrument);
            long volume = 1000 + random.nextInt(5000); // Random volume
            LocalDateTime timestamp = LocalDateTime.now();
            MarketData marketData = new MarketData(instrument, newPrice, volume, timestamp);

            lastMarketData.put(instrument, marketData); // Update last known data

            // Notify listeners
            List<MarketDataListener> instrumentListeners = subscriptions.get(instrument);
            if (instrumentListeners != null && !instrumentListeners.isEmpty()) {
                // Create a copy for safe iteration if listeners might unsubscribe during notification
                List<MarketDataListener> listenersCopy = new ArrayList<>(instrumentListeners);
                LOGGER.log(Level.FINE, "Notifying {0} listeners for {1} with data: {2}",
                           new Object[]{listenersCopy.size(), instrument.getSymbol(), marketData});
                for (MarketDataListener listener : listenersCopy) {
                    try {
                        listener.onMarketData(marketData);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error notifying listener for instrument " + instrument.getSymbol(), e);
                    }
                }
            }
        }
    }

    @Override
    public void connect() throws ConnectionException {
        if (isConnected) {
            LOGGER.info("Already connected.");
            return;
        }
        LOGGER.info("Connecting to MockMarketDataProvider...");
        executorService = Executors.newSingleThreadScheduledExecutor();
        // Schedule the task to run every 1 to 2 seconds (e.g., 1500 ms)
        executorService.scheduleAtFixedRate(this::marketDataGenerationTask, 0, 1500, TimeUnit.MILLISECONDS);
        isConnected = true;
        LOGGER.info("MockMarketDataProvider connected and data generation started.");
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
        isConnected = false;
        // Optionally clear subscriptions:
        // subscriptions.clear();
        // lastMarketData.clear(); // Or retain last known prices
        LOGGER.info("MockMarketDataProvider disconnected.");
    }

    @Override
    public void subscribe(Instrument instrument, MarketDataListener listener) throws SubscriptionException {
        if (instrument == null || listener == null) {
            throw new IllegalArgumentException("Instrument and listener cannot be null.");
        }

        if (!supportedInstruments.contains(instrument)) {
            LOGGER.log(Level.WARNING, "Attempted to subscribe to an unsupported instrument: {0}. This provider only supports: {1}",
                       new Object[]{instrument.getSymbol(), supportedInstruments.stream().map(Instrument::getSymbol).collect(Collectors.joining(", "))});
            // Not throwing SubscriptionException as per requirement ("log a warning and not subscribe")
            return;
        }

        subscriptions.computeIfAbsent(instrument, k -> new ArrayList<>()).add(listener);
        LOGGER.log(Level.INFO, "Listener subscribed for instrument: {0}", instrument.getSymbol());

        // Optionally, send the last known market data upon subscription
        MarketData currentData = lastMarketData.get(instrument);
        if (currentData != null) {
            listener.onMarketData(currentData);
        }
    }

    @Override
    public void unsubscribe(Instrument instrument, MarketDataListener listener) {
        if (instrument == null || listener == null) {
            LOGGER.log(Level.WARNING, "Attempted to unsubscribe with null instrument or listener.");
            return;
        }

        List<MarketDataListener> instrumentListeners = subscriptions.get(instrument);
        if (instrumentListeners != null) {
            boolean removed = instrumentListeners.remove(listener);
            if (removed) {
                LOGGER.log(Level.INFO, "Listener unsubscribed for instrument: {0}", instrument.getSymbol());
                if (instrumentListeners.isEmpty()) {
                    subscriptions.remove(instrument); // Clean up if no listeners left
                }
            } else {
                LOGGER.log(Level.WARNING, "Listener not found for unsubscription from instrument: {0}", instrument.getSymbol());
            }
        } else {
            LOGGER.log(Level.WARNING, "No listeners found for instrument during unsubscription: {0}", instrument.getSymbol());
        }
    }

    /**
     * Checks if the provider is currently connected.
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Gets the list of instruments this provider simulates data for.
     * @return An unmodifiable list of supported instruments.
     */
    public List<Instrument> getSupportedInstruments() {
        return supportedInstruments;
    }
}

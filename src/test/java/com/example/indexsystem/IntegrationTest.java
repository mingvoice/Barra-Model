package com.example.indexsystem;

import com.example.indexsystem.calculator.MarketCapWeightedIndexCalculator;
import com.example.indexsystem.engine.RealTimeIndexService;
import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import com.example.indexsystem.provider.mock.MockMarketDataProvider;
import com.example.indexsystem.registry.InMemoryIndexRegistry;
import com.example.indexsystem.service.IndexCalculator;
import com.example.indexsystem.service.exception.ConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the real-time index calculation system.
 * This test sets up a mini version of the system to ensure components interact correctly,
 * from market data generation to index calculation and logging.
 */
public class IntegrationTest {

    // Setup a logger to capture output for verification, if needed.
    // For simplicity in this example, we'll rely on console output observation primarily.
    // For more robust CI tests, capturing logs or using a test appender would be better.
    private static final Logger systemLogger = Logger.getLogger("com.example.indexsystem");

    static {
        // Configure logger for console output for easier observation during tests
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %3$s - %5$s %6$s%n");
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        consoleHandler.setLevel(Level.INFO); // Set to FINE or FINER for more detailed logs

        // Remove existing handlers to avoid duplicate logging if tests run multiple times in same VM
        for (Handler handler : systemLogger.getHandlers()) {
            systemLogger.removeHandler(handler);
        }
        systemLogger.addHandler(consoleHandler);
        systemLogger.setLevel(Level.INFO); // Or FINE/FINER
        systemLogger.setUseParentHandlers(false); // Prevent duplicate output from root logger
    }

    /**
     * Tests the end-to-end flow of index calculation.
     * 1. Defines instruments and an index.
     * 2. Sets up the registry, calculator, market data provider, and the main service.
     * 3. Starts the market data provider.
     * 4. Allows the system to run for a few seconds.
     * 5. Verifies (primarily through expected log output and basic assertions) that data is processed and indices are calculated.
     * 6. Shuts down the components.
     *
     * @throws InterruptedException if the thread sleep is interrupted.
     * @throws ConnectionException if the market data provider fails to connect.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS) // Test timeout to prevent hangs
    void testEndToEndIndexCalculation() throws InterruptedException, ConnectionException {
        System.out.println("Starting Integration Test: testEndToEndIndexCalculation");
        systemLogger.info("Integration Test: Setting up components...");

        // 1. Define Instruments
        Instrument aapl = new Instrument("AAPL", "NASDAQ", "USD");
        Instrument googl = new Instrument("GOOGL", "NASDAQ", "USD");
        List<Instrument> instruments = List.of(aapl, googl);

        // 2. Define Index Definition
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(aapl, new BigDecimal("1000")), // 1000 shares for AAPL
                new InstrumentWeight(googl, new BigDecimal("500"))   // 500 shares for GOOGL
        );
        IndexDefinition techIndexDef = new IndexDefinition(
                "TECH_INDEX",
                "Major Tech Index",
                "A market cap weighted index of major tech stocks.",
                constituents,
                "MARKET_CAP_WEIGHTED" // Calculation algorithm key
        );
        systemLogger.info("Integration Test: Defined Instruments and Index: " + techIndexDef.getIndexId());


        // 3. Setup Index Registry
        InMemoryIndexRegistry indexRegistry = new InMemoryIndexRegistry();
        indexRegistry.addIndex(techIndexDef);
        systemLogger.info("Integration Test: IndexRegistry populated.");

        // 4. Setup Calculators
        MarketCapWeightedIndexCalculator marketCapCalculator = new MarketCapWeightedIndexCalculator(
                new BigDecimal("1000000"), // Example divisor
                4                          // Scale for index value
        );
        Map<String, IndexCalculator> calculators = new HashMap<>();
        calculators.put("MARKET_CAP_WEIGHTED", marketCapCalculator);
        systemLogger.info("Integration Test: Calculators configured.");

        // 5. Setup Market Data Provider
        MockMarketDataProvider marketDataProvider = new MockMarketDataProvider(instruments);
        systemLogger.info("Integration Test: MockMarketDataProvider initialized.");

        // 6. Setup RealTimeIndexService
        RealTimeIndexService realTimeIndexService = new RealTimeIndexService(
                indexRegistry,
                marketDataProvider,
                calculators
        );
        systemLogger.info("Integration Test: RealTimeIndexService initialized. Service will now load definitions and subscribe.");

        // Execution
        systemLogger.info("Integration Test: Connecting MarketDataProvider to start data generation...");
        marketDataProvider.connect();
        assertTrue(marketDataProvider.isConnected(), "MarketDataProvider should be connected.");

        systemLogger.info("Integration Test: Letting the system run for 5 seconds to process data...");
        // Allow time for a few ticks of data processing.
        // In a real CI, more sophisticated synchronization (e.g., CountDownLatch on expected number of calculations)
        // would be better than Thread.sleep().
        Thread.sleep(5000); // 5 seconds

        systemLogger.info("Integration Test: System run complete. Performing checks...");

        // Verification
        // Primary verification is by observing console logs for:
        // - MockMarketDataProvider generating data for AAPL, GOOGL.
        // - RealTimeIndexService receiving data and calculating TECH_INDEX.
        // - Calculated index values appearing reasonable and changing.

        // Optional advanced verification:
        CalculatedIndexValue latestValue = realTimeIndexService.getLatestIndexValue("TECH_INDEX");
        assertNotNull(latestValue, "Latest calculated value for TECH_INDEX should not be null after some processing time.");
        systemLogger.info("Integration Test: Retrieved latest value for TECH_INDEX: " + latestValue.getValue());

        // Assuming positive prices and shares, the index value should be greater than zero.
        // The MockMarketDataProvider initializes AAPL with 150, GOOGL with 2500.
        // Expected initial (before random changes): (150*1000 + 2500*500) / 1000000 = (150000 + 1250000) / 1000000 = 1400000 / 1000000 = 1.4
        // Prices will fluctuate, so we just check it's positive and non-zero.
        assertTrue(latestValue.getValue().compareTo(BigDecimal.ZERO) > 0,
                   "Calculated index value should be greater than zero.");

        Map<String, CalculatedIndexValue> allValues = realTimeIndexService.getAllLatestIndexValues();
        assertFalse(allValues.isEmpty(), "Should have at least one calculated index.");
        assertTrue(allValues.containsKey("TECH_INDEX"));


        // Cleanup
        systemLogger.info("Integration Test: Shutting down components...");
        marketDataProvider.disconnect();
        assertFalse(marketDataProvider.isConnected(), "MarketDataProvider should be disconnected.");
        realTimeIndexService.shutdown();
        systemLogger.info("Integration Test: Cleanup complete. Test finished.");
        System.out.println("Finished Integration Test: testEndToEndIndexCalculation");
    }
}

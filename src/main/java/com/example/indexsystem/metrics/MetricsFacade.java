package com.example.indexsystem.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Facade for accessing a shared Micrometer MeterRegistry and common metrics operations.
 */
public class MetricsFacade {

    private static final Logger LOGGER = Logger.getLogger(MetricsFacade.class.getName());
    private static final MeterRegistry METER_REGISTRY;

    static {
        LOGGER.info("Initializing Micrometer JMX MeterRegistry...");
        METER_REGISTRY = new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM);
        // You can add more registries here if needed (e.g., Prometheus, Datadog)
        // GlobalMetrics.addRegistry(METER_REGISTRY); // Optional: if you want to use GlobalMetrics
        LOGGER.info("JMX MeterRegistry initialized. Metrics should be available via JMX (e.g., JConsole, VisualVM).");
    }

    /**
     * Gets the shared MeterRegistry instance.
     *
     * @return The MeterRegistry.
     */
    public static MeterRegistry getRegistry() {
        return METER_REGISTRY;
    }

    // Convenience methods (optional but helpful)

    /**
     * Records the time taken for an index calculation.
     *
     * @param milliseconds The duration of the calculation in milliseconds.
     */
    public static void recordCalculationTime(long milliseconds) {
        Timer.builder("index.calculation.latency")
             .description("Time taken to calculate an index value")
             .publishPercentiles(0.5, 0.95, 0.99)
             .register(METER_REGISTRY)
             .record(milliseconds, TimeUnit.MILLISECONDS);
    }

    /**
     * Increments the counter for the total number of indices calculated.
     */
    public static void incrementIndicesCalculated() {
        Counter.builder("indices.calculated.total")
               .description("Total indices calculated")
               .register(METER_REGISTRY)
               .increment();
    }
    
    /**
     * Increments the counter for the total number of market data messages consumed by the streams application.
     * @param streamAppId The application ID of the Kafka Streams instance.
     */
    public static void incrementMarketDataConsumed(String streamAppId) {
        Counter.builder("marketdata.consumed.total")
               .description("Total market data messages consumed by the streams application")
               .tag("streamAppId", streamAppId) // Tag by stream application ID
               .register(METER_REGISTRY)
               .increment();
    }
    
    /**
     * Records the time taken for a specific Redis operation.
     *
     * @param operationName The name of the Redis operation (e.g., "getMarketData", "setIndexDefinition").
     * @param runnable      The Redis operation to execute and time.
     */
    public static void recordRedisOperation(String operationName, Runnable runnable) {
        Timer.builder("redis.operation.latency")
            .description("Latency of Redis operations")
            .tag("operation", operationName)
            .publishPercentiles(0.5, 0.95)
            .register(METER_REGISTRY)
            .record(runnable);
    }

    /**
     * Records the time taken for a specific Redis operation that returns a value.
     *
     * @param <T>           The type of the return value.
     * @param operationName The name of the Redis operation (e.g., "getMarketData", "getIndexDefinition").
     * @param callable      The Redis operation to execute and time.
     * @return The result of the callable.
     * @throws Exception if the callable throws an exception.
     */
    public static <T> T recordRedisOperation(String operationName, java.util.concurrent.Callable<T> callable) throws Exception {
         return Timer.builder("redis.operation.latency")
            .description("Latency of Redis operations")
            .tag("operation", operationName)
            .publishPercentiles(0.5, 0.95)
            .register(METER_REGISTRY)
            .recordCallable(callable);
    }
}

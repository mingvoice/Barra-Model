package com.example.indexsystem.cache;

import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.MarketDataMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.indexsystem.metrics.MetricsFacade; // Import MetricsFacade
import io.micrometer.core.instrument.MeterRegistry; // Import MeterRegistry
import io.micrometer.core.instrument.Timer; // Import Timer
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages interactions with a Redis instance for caching and state management.
 */
public class RedisManager {
    private static final Logger LOGGER = Logger.getLogger(RedisManager.class.getName());
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry; // Micrometer registry

    // Key prefixes for Redis
    private static final String MARKET_DATA_PREFIX = "marketdata:";
    private static final String INDEX_VALUE_PREFIX = "indexvalue:";
    private static final String INSTRUMENT_TO_INDEX_PREFIX = "instr2idx:";
    private static final String INDEX_DEFINITION_PREFIX = "indexdef:";

    /**
     * Constructs a RedisManager with default host (localhost) and port (6379).
     */
    public RedisManager() {
        this(DEFAULT_REDIS_HOST, DEFAULT_REDIS_PORT);
    }

    /**
     * Constructs a RedisManager with specified host and port.
     *
     * @param host The Redis host.
     * @param port The Redis port.
     */
    public RedisManager(String host, int port) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        // Configure pool settings if needed (e.g., maxTotal, maxIdle)
        this.jedisPool = new JedisPool(poolConfig, host, port);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // For Java Time API support
        this.meterRegistry = MetricsFacade.getRegistry(); // Obtain MeterRegistry
        LOGGER.log(Level.INFO, "RedisManager initialized. Connected to Redis at {0}:{1}", new Object[]{host, port});

        // Test connection
        try (Jedis jedis = jedisPool.getResource()) {
            String pingResponse = jedis.ping();
            LOGGER.log(Level.INFO, "Successfully connected to Redis. PING response: {0}", pingResponse);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to Redis at " + host + ":" + port, e);
            // Depending on policy, could throw a runtime exception here to halt startup
        }
    }

    private String toJson(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }

    private <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return objectMapper.readValue(json, clazz);
    }

    public void setMarketData(MarketDataMessage data) {
        if (data == null || data.getInstrumentSymbol() == null) {
            LOGGER.warning("MarketDataMessage or its symbol is null. Cannot store in Redis.");
            return;
        }
        String key = MARKET_DATA_PREFIX + data.getInstrumentSymbol();
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "setMarketData");
        try {
            timer.record(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String jsonValue = toJson(data);
                    jedis.set(key, jsonValue);
                    LOGGER.log(Level.FINE, "Stored market data for {0} in Redis. Key: {1}", new Object[]{data.getInstrumentSymbol(), key});
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error serializing MarketDataMessage for " + data.getInstrumentSymbol(), e);
                    throw new RuntimeException(e); // Re-throw to be caught by timer or outer handler
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error setting market data for " + data.getInstrumentSymbol(), e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            // Timer runnable re-throws, so this general catch might not be strictly needed
            // if specific exceptions are handled inside, but good for safety.
            LOGGER.log(Level.SEVERE, "Failed Redis operation setMarketData after timer instrumentation", e);
        }
    }

    public MarketDataMessage getMarketData(String instrumentSymbol) {
        if (instrumentSymbol == null) return null;
        String key = MARKET_DATA_PREFIX + instrumentSymbol;
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "getMarketData");
        try {
            return timer.recordCallable(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String jsonValue = jedis.get(key);
                    if (jsonValue == null) {
                        LOGGER.log(Level.FINER, "No market data found in Redis for symbol: {0}", instrumentSymbol);
                        return null;
                    }
                    MarketDataMessage data = fromJson(jsonValue, MarketDataMessage.class);
                    LOGGER.log(Level.FINE, "Retrieved market data for {0} from Redis.", instrumentSymbol);
                    return data;
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error deserializing MarketDataMessage for " + instrumentSymbol, e);
                    throw e; // Re-throw to be caught by timer or outer handler
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error getting market data for " + instrumentSymbol, e);
                    throw e;
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed Redis operation getMarketData after timer instrumentation", e);
            return null;
        }
    }

    public void setCalculatedIndexValue(CalculatedIndexValue value) {
        if (value == null || value.getIndexId() == null) {
            LOGGER.warning("CalculatedIndexValue or its ID is null. Cannot store in Redis.");
            return;
        }
        String key = INDEX_VALUE_PREFIX + value.getIndexId();
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "setCalculatedIndexValue");
        try {
            timer.record(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String jsonValue = toJson(value);
                    jedis.set(key, jsonValue);
                    LOGGER.log(Level.INFO, "Stored calculated index value for {0} in Redis. Key: {1}", new Object[]{value.getIndexId(), key});
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error serializing CalculatedIndexValue for " + value.getIndexId(), e);
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error setting calculated index value for " + value.getIndexId(), e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed Redis operation setCalculatedIndexValue after timer instrumentation", e);
        }
    }

    public CalculatedIndexValue getCalculatedIndexValue(String indexId) {
        if (indexId == null) return null;
        String key = INDEX_VALUE_PREFIX + indexId;
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "getCalculatedIndexValue");
        try {
            return timer.recordCallable(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String jsonValue = jedis.get(key);
                    if (jsonValue == null) {
                        LOGGER.log(Level.FINER, "No calculated index value found in Redis for ID: {0}", indexId);
                        return null;
                    }
                    CalculatedIndexValue val = fromJson(jsonValue, CalculatedIndexValue.class);
                    LOGGER.log(Level.FINE, "Retrieved calculated index value for {0} from Redis.", indexId);
                    return val;
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error deserializing CalculatedIndexValue for " + indexId, e);
                    throw e;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error getting calculated index value for " + indexId, e);
                    throw e;
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed Redis operation getCalculatedIndexValue after timer instrumentation", e);
            return null;
        }
    }

    public void addInstrumentToIndexMapping(String instrumentSymbol, String indexId) {
        if (instrumentSymbol == null || indexId == null) {
            LOGGER.warning("Instrument symbol or index ID is null. Cannot create mapping in Redis.");
            return;
        }
        String key = INSTRUMENT_TO_INDEX_PREFIX + instrumentSymbol;
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "addInstrumentToIndexMapping");
        try {
            timer.record(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.sadd(key, indexId);
                    LOGGER.log(Level.INFO, "Added mapping: instrument {0} -> index {1} in Redis. Key: {2}", new Object[]{instrumentSymbol, indexId, key});
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error adding instrument-to-index mapping for " + instrumentSymbol, e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed Redis operation addInstrumentToIndexMapping after timer instrumentation", e);
        }
    }

    public Set<String> getIndicesForInstrument(String instrumentSymbol) {
        if (instrumentSymbol == null) return Collections.emptySet();
        String key = INSTRUMENT_TO_INDEX_PREFIX + instrumentSymbol;
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "getIndicesForInstrument");
        try {
            return timer.recordCallable(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    Set<String> indexIds = jedis.smembers(key);
                    LOGGER.log(Level.FINE, "Retrieved {0} index mappings for instrument {1} from Redis.", new Object[]{indexIds.size(), instrumentSymbol});
                    return indexIds != null ? indexIds : Collections.emptySet();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error getting indices for instrument " + instrumentSymbol, e);
                    throw e;
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed Redis operation getIndicesForInstrument after timer instrumentation", e);
            return Collections.emptySet();
        }
    }

    public void setIndexDefinition(IndexDefinition definition) {
        if (definition == null || definition.getIndexId() == null) {
            LOGGER.warning("IndexDefinition or its ID is null. Cannot store in Redis.");
            return;
        }
        String key = INDEX_DEFINITION_PREFIX + definition.getIndexId();
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "setIndexDefinition");
        try {
            timer.record(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String jsonValue = toJson(definition);
                    jedis.set(key, jsonValue);
                    LOGGER.log(Level.INFO, "Stored index definition for {0} in Redis. Key: {1}", new Object[]{definition.getIndexId(), key});
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error serializing IndexDefinition for " + definition.getIndexId(), e);
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error setting index definition for " + definition.getIndexId(), e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed Redis operation setIndexDefinition after timer instrumentation", e);
        }
    }

    public IndexDefinition getIndexDefinition(String indexId) {
        if (indexId == null) return null;
        String key = INDEX_DEFINITION_PREFIX + indexId;
        Timer timer = MetricsFacade.getRegistry().timer("redis.operation.latency", "operation", "getIndexDefinition");
        try {
            return timer.recordCallable(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String jsonValue = jedis.get(key);
                    if (jsonValue == null) {
                        LOGGER.log(Level.FINER, "No index definition found in Redis for ID: {0}", indexId);
                        return null;
                    }
                    IndexDefinition def = fromJson(jsonValue, IndexDefinition.class);
                    LOGGER.log(Level.FINE, "Retrieved index definition for {0} from Redis.", indexId);
                    return def;
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.SEVERE, "Error deserializing IndexDefinition for " + indexId, e);
                    throw e;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Redis error getting index definition for " + indexId, e);
                    throw e;
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed Redis operation getIndexDefinition after timer instrumentation", e);
            return null;
        }
    }

    /**
     * Closes the JedisPool. Should be called when the application is shutting down.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            LOGGER.info("JedisPool closed.");
        }
    }
}

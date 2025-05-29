package com.example.indexsystem.registry;

import com.example.indexsystem.cache.RedisManager;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import com.example.indexsystem.service.IndexRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An in-memory implementation of the {@link IndexRegistry} interface.
 * It also writes index definitions and instrument-to-index mappings to Redis
 * for accessibility by distributed stream processors.
 */
public class InMemoryIndexRegistry implements IndexRegistry {

    private static final Logger LOGGER = Logger.getLogger(InMemoryIndexRegistry.class.getName());
    private final Map<String, IndexDefinition> indexDefinitions;
    private final RedisManager redisManager; // Added for writing to Redis

    /**
     * Constructs an InMemoryIndexRegistry.
     *
     * @param redisManager The RedisManager instance to use for writing definitions to Redis.
     *                     Cannot be null.
     * @throws IllegalArgumentException if redisManager is null.
     */
    public InMemoryIndexRegistry(RedisManager redisManager) {
        this.indexDefinitions = new ConcurrentHashMap<>();
        if (redisManager == null) {
           throw new IllegalArgumentException("RedisManager cannot be null for InMemoryIndexRegistry.");
        }
        this.redisManager = redisManager;
        LOGGER.info("InMemoryIndexRegistry initialized with Redis support.");
    }
    
    /**
     * Loads all locally added index definitions into Redis.
     * This should be called after all definitions are added via `addIndex`.
     */
    public void loadDefinitionsIntoRedis() {
        LOGGER.info("Loading all (" + indexDefinitions.size() + ") index definitions into Redis...");
        if (redisManager == null) {
            LOGGER.severe("RedisManager is not initialized. Cannot load definitions into Redis.");
            return;
        }
        for (IndexDefinition definition : indexDefinitions.values()) {
            redisManager.setIndexDefinition(definition);
            if (definition.getConstituents() != null) {
                for (InstrumentWeight iw : definition.getConstituents()) {
                    if (iw != null && iw.getInstrument() != null && iw.getInstrument().getSymbol() != null && definition.getIndexId() != null) {
                        redisManager.addInstrumentToIndexMapping(iw.getInstrument().getSymbol(), definition.getIndexId());
                    }
                }
            }
        }
        LOGGER.info("Finished loading index definitions into Redis.");
    }


    /**
     * Adds a new index definition to the local in-memory registry.
     * Note: This method itself does NOT write to Redis. Call {@link #loadDefinitionsIntoRedis()}
     * after all definitions are added to persist them.
     * If an index definition with the same ID already exists, it will be overwritten,
     * and a warning will be logged.
     *
     * @param definition The index definition to add. Must not be null, and its ID must not be null.
     * @throws IllegalArgumentException if the definition or its ID is null.
     */
    @Override
    public void addIndex(IndexDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("IndexDefinition cannot be null.");
        }
        if (definition.getIndexId() == null) {
            throw new IllegalArgumentException("IndexDefinition ID cannot be null.");
        }

        String indexId = definition.getIndexId();
        if (indexDefinitions.containsKey(indexId)) {
            LOGGER.log(Level.WARNING, "Overwriting existing index definition for ID: {0}", indexId);
        }
        indexDefinitions.put(indexId, definition);
        LOGGER.log(Level.INFO, "Added/Updated index definition in-memory: {0} ({1})", new Object[]{definition.getName(), indexId});
        // Note: Removed direct writing to Redis here to allow batch loading via loadDefinitionsIntoRedis()
    }

    /**
     * Retrieves an index definition by its ID from the local in-memory cache.
     * This method does NOT consult Redis.
     *
     * @param indexId The ID of the index to retrieve.
     * @return The {@link IndexDefinition} if found, or {@code null} if no index with the given ID exists in memory.
     */
    @Override
    public IndexDefinition getIndex(String indexId) {
        if (indexId == null) {
            LOGGER.log(Level.FINE, "Attempted to retrieve index with null ID from memory.");
            return null;
        }
        IndexDefinition definition = indexDefinitions.get(indexId);
        if (definition == null) {
            LOGGER.log(Level.FINE, "No index definition found in memory for ID: {0}", indexId);
        } else {
            LOGGER.log(Level.FINE, "Retrieved index definition from memory for ID: {0}", indexId);
        }
        return definition;
    }

    /**
     * Retrieves all index definitions currently in the local in-memory registry.
     * This method does NOT consult Redis.
     *
     * @return A new list containing all {@link IndexDefinition}s from memory. Returns an empty list if the registry is empty.
     *         The returned list is a defensive copy.
     */
    @Override
    public List<IndexDefinition> getAllIndices() {
        List<IndexDefinition> allDefs = new ArrayList<>(indexDefinitions.values());
        LOGGER.log(Level.INFO, "Retrieved all {0} index definitions from the in-memory registry.", allDefs.size());
        return allDefs;
    }
}

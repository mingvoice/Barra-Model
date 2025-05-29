package com.example.indexsystem.registry;

import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.service.IndexRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An in-memory implementation of the {@link IndexRegistry} interface.
 * This class stores index definitions in a thread-safe {@link ConcurrentHashMap}.
 */
public class InMemoryIndexRegistry implements IndexRegistry {

    private static final Logger LOGGER = Logger.getLogger(InMemoryIndexRegistry.class.getName());
    private final Map<String, IndexDefinition> indexDefinitions;

    /**
     * Constructs an empty InMemoryIndexRegistry.
     */
    public InMemoryIndexRegistry() {
        this.indexDefinitions = new ConcurrentHashMap<>();
        LOGGER.info("InMemoryIndexRegistry initialized.");
    }

    /**
     * Adds a new index definition to the registry.
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
        LOGGER.log(Level.INFO, "Added/Updated index definition: {0} ({1})", new Object[]{definition.getName(), indexId});
    }

    /**
     * Retrieves an index definition by its ID.
     *
     * @param indexId The ID of the index to retrieve.
     * @return The {@link IndexDefinition} if found, or {@code null} if no index with the given ID exists.
     */
    @Override
    public IndexDefinition getIndex(String indexId) {
        if (indexId == null) {
            LOGGER.log(Level.FINE, "Attempted to retrieve index with null ID.");
            return null;
        }
        IndexDefinition definition = indexDefinitions.get(indexId);
        if (definition == null) {
            LOGGER.log(Level.FINE, "No index definition found for ID: {0}", indexId);
        } else {
            LOGGER.log(Level.FINE, "Retrieved index definition for ID: {0}", indexId);
        }
        return definition;
    }

    /**
     * Retrieves all index definitions currently in the registry.
     *
     * @return A new list containing all {@link IndexDefinition}s. Returns an empty list if the registry is empty.
     *         The returned list is a defensive copy.
     */
    @Override
    public List<IndexDefinition> getAllIndices() {
        List<IndexDefinition> allDefs = new ArrayList<>(indexDefinitions.values());
        LOGGER.log(Level.INFO, "Retrieved all {0} index definitions from the registry.", allDefs.size());
        return allDefs;
    }
}

package com.example.indexsystem.service;

import com.example.indexsystem.model.IndexDefinition;

import java.util.List;

/**
 * Interface for managing a registry of index definitions.
 * Implementations will allow adding, retrieving, and listing index definitions.
 */
public interface IndexRegistry {

    /**
     * Adds a new index definition to the registry.
     * If an index with the same ID already exists, the behavior is implementation-dependent
     * (e.g., it might update the existing one or throw an exception).
     *
     * @param definition The index definition to add.
     */
    void addIndex(IndexDefinition definition);

    /**
     * Retrieves an index definition by its ID.
     *
     * @param indexId The ID of the index to retrieve.
     * @return The {@link IndexDefinition} if found, or {@code null} if no index with the given ID exists.
     */
    IndexDefinition getIndex(String indexId);

    /**
     * Retrieves all index definitions currently in the registry.
     *
     * @return A list of all {@link IndexDefinition}s. Returns an empty list if the registry is empty.
     *         The returned list should be unmodifiable or a defensive copy to prevent external modifications.
     */
    List<IndexDefinition> getAllIndices();
}

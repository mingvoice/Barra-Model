package com.example.indexsystem.registry;

import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryIndexRegistryTest {

    private InMemoryIndexRegistry registry;
    private IndexDefinition def1;
    private IndexDefinition def2;

    @BeforeEach
    void setUp() {
        registry = new InMemoryIndexRegistry();
        Instrument instA = new Instrument("INSTA", "EXCH", "USD");
        Instrument instB = new Instrument("INSTB", "EXCH", "USD");

        def1 = new IndexDefinition("IDX1", "Index One", "First test index",
                                   List.of(new InstrumentWeight(instA, BigDecimal.TEN)), "Algo1");
        def2 = new IndexDefinition("IDX2", "Index Two", "Second test index",
                                   List.of(new InstrumentWeight(instB, BigDecimal.ONE)), "Algo2");
    }

    @Test
    void testAddIndexAndGetIndex() {
        registry.addIndex(def1);
        IndexDefinition retrievedDef1 = registry.getIndex("IDX1");

        assertNotNull(retrievedDef1);
        assertEquals("IDX1", retrievedDef1.getIndexId());
        assertEquals("Index One", retrievedDef1.getName());
        assertEquals(def1, retrievedDef1, "Retrieved definition should be equal to the added one.");

        IndexDefinition nonExistent = registry.getIndex("NONEXISTENT");
        assertNull(nonExistent, "Should return null for a non-existent index ID.");
    }

    @Test
    void testAddIndex_NullDefinition() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.addIndex(null);
        });
        assertEquals("IndexDefinition cannot be null.", exception.getMessage());
    }

    @Test
    void testAddIndex_NullIndexIdInDefinition() {
        IndexDefinition defWithNullId = new IndexDefinition(null, "Null ID Index", "", Collections.emptyList(), "Algo");
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.addIndex(defWithNullId);
        });
        assertEquals("IndexDefinition ID cannot be null.", exception.getMessage());
    }

    @Test
    void testAddIndex_OverwriteExisting() {
        registry.addIndex(def1); // Add original def1

        IndexDefinition def1Updated = new IndexDefinition("IDX1", "Index One Updated", "Updated Desc",
                                                        Collections.emptyList(), "Algo1Updated");
        registry.addIndex(def1Updated); // Add updated def1 with same ID

        IndexDefinition retrieved = registry.getIndex("IDX1");
        assertNotNull(retrieved);
        assertEquals("Index One Updated", retrieved.getName(), "Name should be from the updated definition.");
        assertEquals("Updated Desc", retrieved.getDescription());
        assertEquals("Algo1Updated", retrieved.getCalculationAlgorithm());
        assertEquals(1, registry.getAllIndices().size(), "Registry size should still be 1 after overwrite.");
    }

    @Test
    void testGetIndex_NullId() {
        assertNull(registry.getIndex(null), "Should return null if null ID is passed.");
    }

    @Test
    void testGetAllIndices_EmptyRegistry() {
        List<IndexDefinition> allIndices = registry.getAllIndices();
        assertNotNull(allIndices);
        assertTrue(allIndices.isEmpty(), "getAllIndices should return an empty list for an empty registry.");
    }

    @Test
    void testGetAllIndices_PopulatedRegistry() {
        registry.addIndex(def1);
        registry.addIndex(def2);

        List<IndexDefinition> allIndices = registry.getAllIndices();
        assertNotNull(allIndices);
        assertEquals(2, allIndices.size(), "Should return all added index definitions.");
        assertTrue(allIndices.contains(def1), "List should contain def1.");
        assertTrue(allIndices.contains(def2), "List should contain def2.");
    }

    @Test
    void testGetAllIndices_ReturnsDefensiveCopy() {
        registry.addIndex(def1);
        List<IndexDefinition> indices1 = registry.getAllIndices();
        assertNotNull(indices1);
        assertEquals(1, indices1.size());

        // Modify the returned list
        indices1.clear();

        List<IndexDefinition> indices2 = registry.getAllIndices();
        assertNotNull(indices2);
        assertEquals(1, indices2.size(), "Internal list should not be affected by modifications to the returned list.");
    }

    // Test thread-safety implicitly via ConcurrentHashMap usage, explicit multi-threaded tests are complex for this unit scope.
    // Basic operations are tested for correctness, relying on ConcurrentHashMap for thread safety.
}

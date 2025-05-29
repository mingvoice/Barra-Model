package com.example.indexsystem.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the definition of an index.
 */
public final class IndexDefinition {
    private final String indexId;
    private final String name;
    private final String description;
    private final List<InstrumentWeight> constituents;
    private final String calculationAlgorithm;

    /**
     * Constructs an IndexDefinition object.
     *
     * @param indexId             The ID of the index.
     * @param name                The name of the index.
     * @param description         A description of the index.
     * @param constituents        A list of instrument weights representing the constituents of the index.
     * @param calculationAlgorithm The algorithm used to calculate the index.
     */
    public IndexDefinition(String indexId, String name, String description,
                           List<InstrumentWeight> constituents, String calculationAlgorithm) {
        this.indexId = indexId;
        this.name = name;
        this.description = description;
        // Defensively copy the list to ensure immutability
        this.constituents = new ArrayList<>(constituents);
        this.calculationAlgorithm = calculationAlgorithm;
    }

    /**
     * Gets the ID of the index.
     *
     * @return The index ID.
     */
    public String getIndexId() {
        return indexId;
    }

    /**
     * Gets the name of the index.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the description of the index.
     *
     * @return The description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets a defensive copy of the list of instrument weights for the constituents.
     *
     * @return The list of constituents.
     */
    public List<InstrumentWeight> getConstituents() {
        // Return a defensive copy to maintain immutability
        return new ArrayList<>(constituents);
    }

    /**
     * Gets the calculation algorithm for the index.
     *
     * @return The calculation algorithm.
     */
    public String getCalculationAlgorithm() {
        return calculationAlgorithm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexDefinition that = (IndexDefinition) o;
        return Objects.equals(indexId, that.indexId) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(constituents, that.constituents) &&
               Objects.equals(calculationAlgorithm, that.calculationAlgorithm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexId, name, description, constituents, calculationAlgorithm);
    }

    @Override
    public String toString() {
        return "IndexDefinition{" +
               "indexId='" + indexId + '\'' +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", constituents=" + constituents +
               ", calculationAlgorithm='" + calculationAlgorithm + '\'' +
               '}';
    }
}

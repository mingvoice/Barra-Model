package com.example.indexsystem.calculator;

import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import com.example.indexsystem.model.MarketData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketCapWeightedIndexCalculatorTest {

    private MarketCapWeightedIndexCalculator calculator;
    private Instrument inst1;
    private Instrument inst2;
    private Instrument inst3;

    @BeforeEach
    void setUp() {
        // Using a divisor of 1 for simplicity in verifying calculations, scale of 4
        calculator = new MarketCapWeightedIndexCalculator(new BigDecimal("1"), 4);
        inst1 = new Instrument("AAPL", "NASDAQ", "USD");
        inst2 = new Instrument("GOOGL", "NASDAQ", "USD");
        inst3 = new Instrument("MSFT", "NASDAQ", "USD");
    }

    private IndexDefinition createIndexDef(List<InstrumentWeight> constituents) {
        return new IndexDefinition("TestIndex", "Test Market Cap Index", "Description",
                                   constituents, "MarketCapWeighted");
    }

    private Map<Instrument, MarketData> createMarketDataMap(Object... data) {
        Map<Instrument, MarketData> map = new HashMap<>();
        for (int i = 0; i < data.length; i += 2) {
            map.put((Instrument) data[i], (MarketData) data[i + 1]);
        }
        return map;
    }

    @Test
    void testCalculateWithValidInputs() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1000")), // 1000 shares
                new InstrumentWeight(inst2, new BigDecimal("500"))   // 500 shares
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("150.25"), 10000L, LocalDateTime.now()),
                inst2, new MarketData(inst2, new BigDecimal("2500.50"), 5000L, LocalDateTime.now())
        );

        // Expected: (150.25 * 1000) + (2500.50 * 500) = 150250 + 1250250 = 1400500
        // Divisor is 1, Scale is 4
        BigDecimal expectedValue = new BigDecimal("1400500.0000");

        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals("TestIndex", result.getIndexId());
        assertEquals(0, expectedValue.compareTo(result.getValue()), "Calculated index value does not match expected.");
        assertEquals(4, result.getValue().scale());
    }

    @Test
    void testCalculateWithPrecisionAndRounding() {
        // Divisor that will cause rounding
        calculator = new MarketCapWeightedIndexCalculator(new BigDecimal("3"), 6);
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1"))
        );
        IndexDefinition indexDef = createIndexDef(constituents);
        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("10.00"), 100L, LocalDateTime.now())
        );

        // Expected: (10.00 * 1) / 3 = 3.3333333... rounded to 6 places
        BigDecimal expectedValue = new BigDecimal("3.333333");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
        assertEquals(6, result.getValue().scale());
    }


    @Test
    void testCalculateWithMissingMarketDataForOneConstituent() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1000")),
                new InstrumentWeight(inst2, new BigDecimal("500")) // Market data for inst2 will be missing
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("150.00"), 10000L, LocalDateTime.now())
                // No data for inst2
        );

        // Expected: (150.00 * 1000) = 150000. inst2 is skipped.
        BigDecimal expectedValue = new BigDecimal("150000.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testCalculateWithMarketDataMissingForAllConstituents() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1000")),
                new InstrumentWeight(inst2, new BigDecimal("500"))
        );
        IndexDefinition indexDef = createIndexDef(constituents);
        Map<Instrument, MarketData> marketData = Collections.emptyMap(); // No data for any instrument

        BigDecimal expectedValue = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testCalculateWithEmptyListOfConstituents() {
        IndexDefinition indexDef = createIndexDef(Collections.emptyList());
        Map<Instrument, MarketData> marketData = createMarketDataMap(
            inst1, new MarketData(inst1, new BigDecimal("150.00"), 10000L, LocalDateTime.now())
        );

        BigDecimal expectedValue = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testCalculateWithZeroShares() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1000")),
                new InstrumentWeight(inst2, new BigDecimal("0")) // Zero shares for inst2
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("150.00"), 10000L, LocalDateTime.now()),
                inst2, new MarketData(inst2, new BigDecimal("2500.00"), 5000L, LocalDateTime.now())
        );

        // Expected: (150.00 * 1000) = 150000. inst2 is skipped due to 0 shares.
        BigDecimal expectedValue = new BigDecimal("150000.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testCalculateWithNegativeShares() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1000")),
                new InstrumentWeight(inst2, new BigDecimal("-50")) // Negative shares for inst2
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("150.00"), 10000L, LocalDateTime.now()),
                inst2, new MarketData(inst2, new BigDecimal("2500.00"), 5000L, LocalDateTime.now())
        );

        // Expected: (150.00 * 1000) = 150000. inst2 is skipped due to negative shares.
        BigDecimal expectedValue = new BigDecimal("150000.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testCalculateWithZeroPrice() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1000")),
                new InstrumentWeight(inst2, new BigDecimal("500"))
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("0.00"), 10000L, LocalDateTime.now()), // Zero price for inst1
                inst2, new MarketData(inst2, new BigDecimal("2500.00"), 5000L, LocalDateTime.now())
        );

        // Expected: (0.00 * 1000) + (2500.00 * 500) = 0 + 1250000 = 1250000
        BigDecimal expectedValue = new BigDecimal("1250000.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testCalculateWithNegativePrice() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, new BigDecimal("1000")),
                new InstrumentWeight(inst2, new BigDecimal("500"))
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("-50.00"), 10000L, LocalDateTime.now()), // Negative price for inst1
                inst2, new MarketData(inst2, new BigDecimal("2500.00"), 5000L, LocalDateTime.now())
        );

        // Expected: (-50.00 * 1000) + (2500.00 * 500) = -50000 + 1250000 = 1200000
        BigDecimal expectedValue = new BigDecimal("1200000.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testConstructorWithInvalidDivisor() {
        // Test with zero divisor, should use default
        MarketCapWeightedIndexCalculator calcZeroDiv = new MarketCapWeightedIndexCalculator(BigDecimal.ZERO, 2);
        // Test with negative divisor, should use default
        MarketCapWeightedIndexCalculator calcNegDiv = new MarketCapWeightedIndexCalculator(new BigDecimal("-100"), 2);

        // This test relies on knowing the default divisor is 1,000,000.
        // A more robust test might involve reflection or a getter if the actual divisor was critical.
        // For now, we'll just ensure it doesn't throw an exception.
        assertNotNull(calcZeroDiv);
        assertNotNull(calcNegDiv);

        List<InstrumentWeight> constituents = List.of(new InstrumentWeight(inst1, new BigDecimal("1")));
        IndexDefinition indexDef = createIndexDef(constituents);
        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("1000000"), 1L, LocalDateTime.now())
        );
        // (1 * 1000000) / 1000000 (default divisor) = 1
        assertEquals(0, new BigDecimal("1.00").compareTo(calcZeroDiv.calculate(indexDef, marketData).getValue()));
    }

    @Test
    void testConstructorWithInvalidScale() {
        MarketCapWeightedIndexCalculator calcNegScale = new MarketCapWeightedIndexCalculator(new BigDecimal("1"), -2);
        assertNotNull(calcNegScale); // Should use default scale

        List<InstrumentWeight> constituents = List.of(new InstrumentWeight(inst1, new BigDecimal("1")));
        IndexDefinition indexDef = createIndexDef(constituents);
        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("1.3333333"), 1L, LocalDateTime.now())
        );
        // Default scale is 4
        assertEquals(0, new BigDecimal("1.3333").compareTo(calcNegScale.calculate(indexDef, marketData).getValue()));
        assertEquals(4, calcNegScale.calculate(indexDef, marketData).getValue().scale());

    }
     @Test
    void testNullIndexDefinition() {
        CalculatedIndexValue value = calculator.calculate(null, Collections.emptyMap());
        assertNotNull(value);
        assertEquals("UNDEFINED", value.getIndexId());
        assertEquals(0, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP).compareTo(value.getValue()));
    }

    @Test
    void testNullMarketDataMap() {
        IndexDefinition indexDef = createIndexDef(Collections.singletonList(new InstrumentWeight(inst1, new BigDecimal("100"))));
        CalculatedIndexValue value = calculator.calculate(indexDef, null);
        assertNotNull(value);
        assertEquals(indexDef.getIndexId(), value.getIndexId());
        assertEquals(0, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP).compareTo(value.getValue()));
    }
}

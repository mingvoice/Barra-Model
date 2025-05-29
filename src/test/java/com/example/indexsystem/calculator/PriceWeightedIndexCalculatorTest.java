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

class PriceWeightedIndexCalculatorTest {

    private PriceWeightedIndexCalculator calculator;
    private Instrument inst1;
    private Instrument inst2;
    private Instrument inst3;

    @BeforeEach
    void setUp() {
        calculator = new PriceWeightedIndexCalculator(4); // Scale of 4
        inst1 = new Instrument("AAPL", "NASDAQ", "USD");
        inst2 = new Instrument("GOOGL", "NASDAQ", "USD");
        inst3 = new Instrument("MSFT", "NASDAQ", "USD");
    }

    private IndexDefinition createIndexDef(List<InstrumentWeight> constituents) {
        // Instrument weights (shares) are not used by this calculator, but the list of instruments is.
        return new IndexDefinition("TestPriceIndex", "Test Price-Weighted Index", "Description",
                                   constituents, "PriceWeighted");
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
                new InstrumentWeight(inst1, BigDecimal.ONE), // Weight value doesn't matter here
                new InstrumentWeight(inst2, BigDecimal.ONE)
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("150.50"), 10000L, LocalDateTime.now()),
                inst2, new MarketData(inst2, new BigDecimal("2500.20"), 5000L, LocalDateTime.now())
        );

        // Expected: (150.50 + 2500.20) / 2 = 2650.70 / 2 = 1325.35
        BigDecimal expectedValue = new BigDecimal("1325.3500");

        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals("TestPriceIndex", result.getIndexId());
        assertEquals(0, expectedValue.compareTo(result.getValue()), "Calculated index value does not match expected.");
        assertEquals(4, result.getValue().scale());
    }

    @Test
    void testCalculateWithPrecisionAndRounding() {
        calculator = new PriceWeightedIndexCalculator(5); // Scale of 5
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, BigDecimal.ONE),
                new InstrumentWeight(inst2, BigDecimal.ONE),
                new InstrumentWeight(inst3, BigDecimal.ONE)
        );
        IndexDefinition indexDef = createIndexDef(constituents);
        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("10.00"), 100L, LocalDateTime.now()),
                inst2, new MarketData(inst2, new BigDecimal("20.00"), 100L, LocalDateTime.now()),
                inst3, new MarketData(inst3, new BigDecimal("15.00"), 100L, LocalDateTime.now())
        );

        // Expected: (10 + 20 + 15) / 3 = 45 / 3 = 15.00
        // Let's try one that needs rounding: (10 + 20 + 16) / 3 = 46 / 3 = 15.33333...
         marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("10.00"), 100L, LocalDateTime.now()),
                inst2, new MarketData(inst2, new BigDecimal("20.00"), 100L, LocalDateTime.now()),
                inst3, new MarketData(inst3, new BigDecimal("16.00"), 100L, LocalDateTime.now())
        );
        BigDecimal expectedValue = new BigDecimal("15.33333"); // Rounded to 5 places
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
        assertEquals(5, result.getValue().scale());
    }

    @Test
    void testCalculateWithMissingMarketDataForOneConstituent() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, BigDecimal.ONE),
                new InstrumentWeight(inst2, BigDecimal.ONE), // Market data for inst2 will be missing
                new InstrumentWeight(inst3, BigDecimal.ONE)
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("150.00"), 10000L, LocalDateTime.now()),
                // No data for inst2
                inst3, new MarketData(inst3, new BigDecimal("300.00"), 7000L, LocalDateTime.now())
        );

        // Expected: (150.00 + 300.00) / 2 = 450.00 / 2 = 225.00
        BigDecimal expectedValue = new BigDecimal("225.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testCalculateWithMarketDataMissingForAllConstituents() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, BigDecimal.ONE),
                new InstrumentWeight(inst2, BigDecimal.ONE)
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
    void testCalculateWithZeroPrice() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, BigDecimal.ONE),
                new InstrumentWeight(inst2, BigDecimal.ONE)
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("0.00"), 10000L, LocalDateTime.now()), // Zero price for inst1
                inst2, new MarketData(inst2, new BigDecimal("2500.00"), 5000L, LocalDateTime.now())
        );

        // Expected: (0.00 + 2500.00) / 2 = 1250.00
        BigDecimal expectedValue = new BigDecimal("1250.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }
    
    @Test
    void testCalculateWithNegativePrice() {
        List<InstrumentWeight> constituents = List.of(
                new InstrumentWeight(inst1, BigDecimal.ONE),
                new InstrumentWeight(inst2, BigDecimal.ONE)
        );
        IndexDefinition indexDef = createIndexDef(constituents);

        Map<Instrument, MarketData> marketData = createMarketDataMap(
                inst1, new MarketData(inst1, new BigDecimal("-50.00"), 10000L, LocalDateTime.now()), // Negative price for inst1
                inst2, new MarketData(inst2, new BigDecimal("250.00"), 5000L, LocalDateTime.now())
        );

        // Expected: (-50.00 + 250.00) / 2 = 200.00 / 2 = 100.00
        BigDecimal expectedValue = new BigDecimal("100.0000");
        CalculatedIndexValue result = calculator.calculate(indexDef, marketData);

        assertNotNull(result);
        assertEquals(0, expectedValue.compareTo(result.getValue()));
    }

    @Test
    void testConstructorWithInvalidScale() {
        PriceWeightedIndexCalculator calcNegScale = new PriceWeightedIndexCalculator(-2);
        assertNotNull(calcNegScale); // Should use default scale (4)

        List<InstrumentWeight> constituents = List.of(new InstrumentWeight(inst1, BigDecimal.ONE));
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
        IndexDefinition indexDef = createIndexDef(Collections.singletonList(new InstrumentWeight(inst1, BigDecimal.ONE)));
        CalculatedIndexValue value = calculator.calculate(indexDef, null);
        assertNotNull(value);
        assertEquals(indexDef.getIndexId(), value.getIndexId());
        assertEquals(0, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP).compareTo(value.getValue()));
    }
}

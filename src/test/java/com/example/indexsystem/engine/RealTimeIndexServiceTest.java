package com.example.indexsystem.engine;

import com.example.indexsystem.model.CalculatedIndexValue;
import com.example.indexsystem.model.IndexDefinition;
import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.InstrumentWeight;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.service.IndexCalculator;
import com.example.indexsystem.service.IndexRegistry;
import com.example.indexsystem.service.MarketDataProvider;
import com.example.indexsystem.service.exception.SubscriptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealTimeIndexServiceTest {

    @Mock
    private IndexRegistry mockIndexRegistry;
    @Mock
    private MarketDataProvider mockMarketDataProvider;
    @Mock
    private IndexCalculator mockCalculator1;
    @Mock
    private IndexCalculator mockCalculator2;

    @Captor
    private ArgumentCaptor<Map<Instrument, MarketData>> marketDataMapCaptor;
    @Captor
    private ArgumentCaptor<IndexDefinition> indexDefinitionCaptor;

    private RealTimeIndexService realTimeIndexService;

    private Instrument inst1, inst2, inst3;
    private IndexDefinition indexDef1, indexDef2;
    private Map<String, IndexCalculator> calculatorsMap;

    @BeforeEach
    void setUp() {
        inst1 = new Instrument("AAPL", "NASDAQ", "USD");
        inst2 = new Instrument("GOOGL", "NASDAQ", "USD");
        inst3 = new Instrument("MSFT", "NASDAQ", "USD"); // Used in a different index

        // Define calculators
        calculatorsMap = new HashMap<>();
        calculatorsMap.put("CalcType1", mockCalculator1);
        calculatorsMap.put("CalcType2", mockCalculator2);

        // Define Index 1: inst1, inst2 using CalcType1
        List<InstrumentWeight> constituents1 = List.of(
                new InstrumentWeight(inst1, new BigDecimal("100")),
                new InstrumentWeight(inst2, new BigDecimal("200"))
        );
        indexDef1 = new IndexDefinition("Index1", "Test Index One", "Desc1", constituents1, "CalcType1");

        // Define Index 2: inst2, inst3 using CalcType2
        List<InstrumentWeight> constituents2 = List.of(
                new InstrumentWeight(inst2, new BigDecimal("50")), // inst2 is in both indices
                new InstrumentWeight(inst3, new BigDecimal("150"))
        );
        indexDef2 = new IndexDefinition("Index2", "Test Index Two", "Desc2", constituents2, "CalcType2");
    }

    private void initializeService() {
        realTimeIndexService = new RealTimeIndexService(mockIndexRegistry, mockMarketDataProvider, calculatorsMap);
    }

    @Test
    void testInit_SubscribesToInstrumentsAndPopulatesMap() throws SubscriptionException {
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(indexDef1, indexDef2));

        initializeService();

        // Verify subscriptions for each unique instrument
        // inst1 (from indexDef1)
        // inst2 (from indexDef1 and indexDef2 - should subscribe only once)
        // inst3 (from indexDef2)
        verify(mockMarketDataProvider, times(1)).subscribe(eq(inst1), any(RealTimeIndexService.class));
        verify(mockMarketDataProvider, times(1)).subscribe(eq(inst2), any(RealTimeIndexService.class));
        verify(mockMarketDataProvider, times(1)).subscribe(eq(inst3), any(RealTimeIndexService.class));

        // Verify internal map (instrumentToIndicesMap) - this is harder without direct access
        // We can infer its state by testing onMarketData behavior
        // For now, we trust the subscription calls imply correct setup for onMarketData.
        // If we had a getter or a more observable behavior, we could assert map contents.
        assertTrue(realTimeIndexService.instrumentToIndicesMap.containsKey(inst1));
        assertTrue(realTimeIndexService.instrumentToIndicesMap.get(inst1).contains(indexDef1));
        assertTrue(realTimeIndexService.instrumentToIndicesMap.containsKey(inst2));
        assertTrue(realTimeIndexService.instrumentToIndicesMap.get(inst2).contains(indexDef1));
        assertTrue(realTimeIndexService.instrumentToIndicesMap.get(inst2).contains(indexDef2));
        assertTrue(realTimeIndexService.instrumentToIndicesMap.containsKey(inst3));
        assertTrue(realTimeIndexService.instrumentToIndicesMap.get(inst3).contains(indexDef2));
    }
    
    @Test
    void testInit_HandlesEmptyIndexDefinitions() throws SubscriptionException {
        when(mockIndexRegistry.getAllIndices()).thenReturn(Collections.emptyList());
        initializeService();
        verify(mockMarketDataProvider, never()).subscribe(any(), any());
        assertTrue(realTimeIndexService.instrumentToIndicesMap.isEmpty());
    }

    @Test
    void testInit_HandlesIndexWithNoConstituents() throws SubscriptionException {
        IndexDefinition emptyDef = new IndexDefinition("EmptyIndex", "Empty", "", Collections.emptyList(), "CalcType1");
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(emptyDef));
        initializeService();
        verify(mockMarketDataProvider, never()).subscribe(any(), any());
    }
    
    @Test
    void testInit_HandlesMissingCalculatorForIndex() throws SubscriptionException {
        IndexDefinition missingCalcDef = new IndexDefinition("MissingCalcIdx", "No Calc", "", List.of(new InstrumentWeight(inst1, BigDecimal.ONE)), "UnknownCalc");
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(missingCalcDef));
        initializeService();
        // Should not subscribe if calculator is missing and index won't be processed for that constituent.
        // The current implementation subscribes, then onMarketData skips. Let's verify current behavior.
        // The log message "No calculator found for algorithm..." is key.
        // If the requirement was to not subscribe, this test would change.
        // Current RealTimeIndexService subscribes first, then checks calculator in onMarketData.
        verify(mockMarketDataProvider, times(1)).subscribe(eq(inst1), any(RealTimeIndexService.class));
    }


    @Test
    void onMarketData_TriggersCalculationForAffectedIndices() {
        // Setup: Index1 (inst1, inst2) and Index2 (inst2, inst3)
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(indexDef1, indexDef2));

        // Mock calculation results
        CalculatedIndexValue calcValue1 = new CalculatedIndexValue("Index1", new BigDecimal("1000.00"), LocalDateTime.now());
        CalculatedIndexValue calcValue2 = new CalculatedIndexValue("Index2", new BigDecimal("2000.00"), LocalDateTime.now());

        when(mockCalculator1.calculate(eq(indexDef1), anyMap())).thenReturn(calcValue1);
        when(mockCalculator2.calculate(eq(indexDef2), anyMap())).thenReturn(calcValue2);
        
        initializeService(); // This will set up instrumentToIndicesMap

        // Market data update for inst2 (affects both Index1 and Index2)
        MarketData mdInst2 = new MarketData(inst2, new BigDecimal("2500.00"), 1000L, LocalDateTime.now());
        realTimeIndexService.onMarketData(mdInst2);

        // Verify calculator1 was called for Index1
        verify(mockCalculator1, times(1)).calculate(eq(indexDef1), marketDataMapCaptor.capture());
        Map<Instrument, MarketData> capturedMap1 = marketDataMapCaptor.getValue();
        assertEquals(mdInst2, capturedMap1.get(inst2), "Market data for inst2 should be in map for Index1");
        assertNull(capturedMap1.get(inst1), "Market data for inst1 (not updated yet) should be null in map for Index1"); // or last known if pre-populated

        // Verify calculator2 was called for Index2
        verify(mockCalculator2, times(1)).calculate(eq(indexDef2), marketDataMapCaptor.capture());
        Map<Instrument, MarketData> capturedMap2 = marketDataMapCaptor.getValue();
        assertEquals(mdInst2, capturedMap2.get(inst2), "Market data for inst2 should be in map for Index2");
        assertNull(capturedMap2.get(inst3), "Market data for inst3 (not updated yet) should be null in map for Index2");


        // Check latest values
        assertEquals(calcValue1, realTimeIndexService.getLatestIndexValue("Index1"));
        assertEquals(calcValue2, realTimeIndexService.getLatestIndexValue("Index2"));

        // Market data update for inst1 (affects only Index1)
        MarketData mdInst1 = new MarketData(inst1, new BigDecimal("150.00"), 2000L, LocalDateTime.now());
        realTimeIndexService.onMarketData(mdInst1);

        // Verify calculator1 called again (total 2 times now)
        verify(mockCalculator1, times(2)).calculate(eq(indexDef1), marketDataMapCaptor.capture());
        Map<Instrument, MarketData> capturedMap3 = marketDataMapCaptor.getValue();
        assertEquals(mdInst1, capturedMap3.get(inst1)); // Now contains inst1's new data
        assertEquals(mdInst2, capturedMap3.get(inst2)); // Still contains inst2's data from previous update

        // Verify calculator2 was NOT called again (still 1 time)
        verify(mockCalculator2, times(1)).calculate(any(), any());
    }
    
    @Test
    void onMarketData_MissingCalculatorSkipsCalculation() {
        // Index with a calc type that has no registered calculator
        IndexDefinition missingCalcDef = new IndexDefinition("MissingCalcIdx", "No Calc", "", List.of(new InstrumentWeight(inst1, BigDecimal.ONE)), "UnknownCalcType");
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(missingCalcDef));
        
        initializeService(); // init will log a warning about unknown calc type
        
        MarketData mdInst1 = new MarketData(inst1, new BigDecimal("100"), 1L, LocalDateTime.now());
        realTimeIndexService.onMarketData(mdInst1);
        
        // No calculator should have been called
        verify(mockCalculator1, never()).calculate(any(), any());
        verify(mockCalculator2, never()).calculate(any(), any());
        assertNull(realTimeIndexService.getLatestIndexValue("MissingCalcIdx"));
    }


    @Test
    void getLatestIndexValue_ReturnsCorrectValueOrNull() {
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(indexDef1));
        CalculatedIndexValue calcValue = new CalculatedIndexValue("Index1", new BigDecimal("1000.00"), LocalDateTime.now());
        when(mockCalculator1.calculate(any(), any())).thenReturn(calcValue);
        
        initializeService();

        assertNull(realTimeIndexService.getLatestIndexValue("Index1"), "Initially, value should be null.");
        assertNull(realTimeIndexService.getLatestIndexValue("NonExistentIndex"), "Value for non-existent index should be null.");

        MarketData md = new MarketData(inst1, new BigDecimal("150.00"), 1L, LocalDateTime.now());
        realTimeIndexService.onMarketData(md); // This should trigger calculation and update

        assertEquals(calcValue, realTimeIndexService.getLatestIndexValue("Index1"));
    }

    @Test
    void getAllLatestIndexValues_ReturnsAllValues() {
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(indexDef1, indexDef2));
        CalculatedIndexValue val1 = new CalculatedIndexValue("Index1", new BigDecimal("100.0"), LocalDateTime.now());
        CalculatedIndexValue val2 = new CalculatedIndexValue("Index2", new BigDecimal("200.0"), LocalDateTime.now());
        when(mockCalculator1.calculate(eq(indexDef1), anyMap())).thenReturn(val1);
        when(mockCalculator2.calculate(eq(indexDef2), anyMap())).thenReturn(val2);

        initializeService();
        assertTrue(realTimeIndexService.getAllLatestIndexValues().isEmpty(), "Initially should be empty.");

        // Trigger calculation for Index1
        realTimeIndexService.onMarketData(new MarketData(inst1, BigDecimal.TEN, 1L, LocalDateTime.now()));
        // Trigger calculation for Index2
        realTimeIndexService.onMarketData(new MarketData(inst3, BigDecimal.ONE, 1L, LocalDateTime.now()));
        // Need to also send data for inst2 as it's common
        realTimeIndexService.onMarketData(new MarketData(inst2, BigDecimal.ONE, 1L, LocalDateTime.now()));


        Map<String, CalculatedIndexValue> allValues = realTimeIndexService.getAllLatestIndexValues();
        assertEquals(2, allValues.size());
        assertEquals(val1, allValues.get("Index1"));
        assertEquals(val2, allValues.get("Index2"));
        
        // Test that the returned map is a copy
        allValues.clear();
        assertFalse(realTimeIndexService.getAllLatestIndexValues().isEmpty(), "Clearing returned map should not affect internal map.");

    }

    @Test
    void shutdown_UnsubscribesFromAllInstruments() throws SubscriptionException {
        when(mockIndexRegistry.getAllIndices()).thenReturn(List.of(indexDef1, indexDef2));
        initializeService();

        // Verify subscriptions occurred during init
        verify(mockMarketDataProvider, times(1)).subscribe(eq(inst1), eq(realTimeIndexService));
        verify(mockMarketDataProvider, times(1)).subscribe(eq(inst2), eq(realTimeIndexService));
        verify(mockMarketDataProvider, times(1)).subscribe(eq(inst3), eq(realTimeIndexService));

        realTimeIndexService.shutdown();

        // Verify unsubscriptions
        verify(mockMarketDataProvider, times(1)).unsubscribe(eq(inst1), eq(realTimeIndexService));
        verify(mockMarketDataProvider, times(1)).unsubscribe(eq(inst2), eq(realTimeIndexService));
        verify(mockMarketDataProvider, times(1)).unsubscribe(eq(inst3), eq(realTimeIndexService));
        
        assertTrue(realTimeIndexService.instrumentToIndicesMap.isEmpty());
        assertTrue(realTimeIndexService.currentMarketDataState.isEmpty());
        assertTrue(realTimeIndexService.latestIndexValues.isEmpty());
    }
    
    @Test
    void testConstructor_NullChecks() {
        assertThrows(IllegalArgumentException.class, () -> new RealTimeIndexService(null, mockMarketDataProvider, calculatorsMap));
        assertThrows(IllegalArgumentException.class, () -> new RealTimeIndexService(mockIndexRegistry, null, calculatorsMap));
        assertThrows(IllegalArgumentException.class, () -> new RealTimeIndexService(mockIndexRegistry, mockMarketDataProvider, null));
    }

    @Test
    void onMarketData_NullDataOrInstrument() {
        initializeService();
        realTimeIndexService.onMarketData(null); // Should log and ignore
        realTimeIndexService.onMarketData(new MarketData(null, BigDecimal.ONE, 1L, LocalDateTime.now())); // Should log and ignore
        
        verify(mockCalculator1, never()).calculate(any(), any());
        assertTrue(realTimeIndexService.latestIndexValues.isEmpty());
    }
}

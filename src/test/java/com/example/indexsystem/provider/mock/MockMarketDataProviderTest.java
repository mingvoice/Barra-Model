package com.example.indexsystem.provider.mock;

import com.example.indexsystem.model.Instrument;
import com.example.indexsystem.model.MarketData;
import com.example.indexsystem.service.MarketDataListener;
import com.example.indexsystem.service.exception.ConnectionException;
import com.example.indexsystem.service.exception.SubscriptionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MockMarketDataProviderTest {

    private MockMarketDataProvider provider;
    private Instrument testInst1;
    private Instrument testInst2;
    private Instrument unsupportedInst;

    @BeforeEach
    void setUp() {
        testInst1 = new Instrument("AAPL", "NASDAQ", "USD");
        testInst2 = new Instrument("GOOGL", "NASDAQ", "USD");
        unsupportedInst = new Instrument("TSLA", "NASDAQ", "USD"); // Not in the initial list
        provider = new MockMarketDataProvider(List.of(testInst1, testInst2));
    }

    @AfterEach
    void tearDown() {
        if (provider.isConnected()) {
            provider.disconnect();
        }
    }

    @Test
    void testConnectAndDisconnect() throws ConnectionException {
        assertFalse(provider.isConnected(), "Provider should initially be disconnected.");
        provider.connect();
        assertTrue(provider.isConnected(), "Provider should be connected after connect().");
        provider.disconnect();
        assertFalse(provider.isConnected(), "Provider should be disconnected after disconnect().");
    }

    @Test
    void testConnectAlreadyConnected() throws ConnectionException {
        provider.connect();
        assertTrue(provider.isConnected());
        provider.connect(); // Connect again
        assertTrue(provider.isConnected(), "Connecting again should not change state.");
    }
    
    @Test
    void testDisconnectAlreadyDisconnected() throws ConnectionException {
        assertFalse(provider.isConnected());
        provider.disconnect(); // Disconnect when already disconnected
        assertFalse(provider.isConnected(), "Disconnecting again should not change state.");
    }


    @Test
    void testSubscribeAndUnsubscribe() throws SubscriptionException {
        MarketDataListener mockListener = mock(MarketDataListener.class);
        
        // Subscribe - should receive last known data
        provider.subscribe(testInst1, mockListener);
        
        // Check if lastMarketData map is populated (it should be by constructor)
        MarketData initialData = provider.lastMarketData.get(testInst1);
        assertNotNull(initialData, "Last market data should exist for supported instrument after construction.");
        verify(mockListener, times(1)).onMarketData(initialData);

        // Unsubscribe
        provider.unsubscribe(testInst1, mockListener);
        
        // To truly test if listener is removed, we'd need to trigger an update
        // and see if onMarketData is called. We'll do that in the data generation test.
        // For now, this confirms the methods run without error.
        // We can also check internal state if we make it accessible or add a method.
        // For this test, we assume the internal map is correctly updated.
    }

    @Test
    void testSubscribeNullInstrumentOrListener() {
        MarketDataListener mockListener = mock(MarketDataListener.class);
        assertThrows(IllegalArgumentException.class, () -> provider.subscribe(null, mockListener));
        assertThrows(IllegalArgumentException.class, () -> provider.subscribe(testInst1, null));
    }
    
    @Test
    void testUnsubscribeNullInstrumentOrListener() {
        // These should log warnings but not throw exceptions
        provider.unsubscribe(null, mock(MarketDataListener.class));
        provider.unsubscribe(testInst1, null);
        // No assertion, just ensuring no NPE or other exception
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS) // Increased timeout for async operations
    void testDataGenerationAndListenerNotification() throws ConnectionException, InterruptedException, SubscriptionException {
        CountDownLatch latch = new CountDownLatch(1); // To wait for at least one update
        AtomicReference<MarketData> receivedDataRef = new AtomicReference<>();

        MarketDataListener mockListener = data -> {
            assertNotNull(data, "Received MarketData should not be null.");
            assertEquals(testInst1.getSymbol(), data.getInstrument().getSymbol(), "Instrument symbol should match.");
            assertTrue(data.getLastPrice().compareTo(BigDecimal.ZERO) >= 0, "Price should be non-negative.");
            assertTrue(data.getVolume() >= 0, "Volume should be non-negative.");
            assertNotNull(data.getTimestamp(), "Timestamp should not be null.");
            receivedDataRef.set(data);
            latch.countDown();
        };

        provider.subscribe(testInst1, mockListener); // This will send initial data
        MarketData initialData = provider.lastMarketData.get(testInst1);
        assertNotNull(initialData); // From constructor

        provider.connect();
        assertTrue(provider.isConnected());

        // Wait for the latch to be counted down by the listener, or timeout
        boolean received = latch.await(3, TimeUnit.SECONDS); // Wait for up to 3 seconds for a new update
        
        provider.disconnect(); // Stop generation
        assertFalse(provider.isConnected());

        assertTrue(received, "Listener should have received a market data update after initial.");
        MarketData liveUpdate = receivedDataRef.get();
        assertNotNull(liveUpdate, "Live market data update was not received.");
        
        // Check if the live update is different from the initial one (timestamp or value)
        // Note: Price might randomly be the same, but timestamp should differ if a new update came.
        // If the update interval is faster than the timestamp resolution, this could be tricky.
        // The mock provider updates prices, so we expect a different price or at least timestamp.
        assertNotEquals(initialData.getTimestamp(), liveUpdate.getTimestamp(), "Live update timestamp should be different from initial if new data generated.");
    }

    @Test
    void testSubscribeToUnsupportedInstrument() throws SubscriptionException {
        MarketDataListener mockListener = mock(MarketDataListener.class);
        provider.subscribe(unsupportedInst, mockListener);
        
        // Verify listener was NOT called with any data for the unsupported instrument
        verify(mockListener, never()).onMarketData(any(MarketData.class));
        
        // Also check internal subscriptions map (if accessible, or through behavior)
        // For now, we rely on the logging and lack of notification.
        assertFalse(provider.subscriptions.containsKey(unsupportedInst), "Unsupported instrument should not be in subscriptions map.");
    }
    
    @Test
    void testSubscriptionToUnsupportedInstrumentDoesNotThrow() {
        MarketDataListener mockListener = mock(MarketDataListener.class);
        assertDoesNotThrow(() -> provider.subscribe(unsupportedInst, mockListener),
            "Subscribing to an unsupported instrument should not throw SubscriptionException.");
    }


    @Test
    void testSendLastKnownDataUponNewSubscription() throws SubscriptionException {
        // First, let some data be generated for testInst1
        MarketData initialDataForInst1 = provider.lastMarketData.get(testInst1);
        assertNotNull(initialDataForInst1, "Initial data for testInst1 should exist.");

        // Now subscribe a new listener
        MarketDataListener newListener = mock(MarketDataListener.class);
        provider.subscribe(testInst1, newListener);

        // Verify that this new listener immediately receives the last known data for testInst1
        verify(newListener, times(1)).onMarketData(initialDataForInst1);
        verify(newListener, times(1)).onMarketData(argThat(md -> md.getInstrument().equals(testInst1)));
    }
    
    @Test
    void testUnsubscribeNonExistentListener() {
        MarketDataListener mockListener = mock(MarketDataListener.class);
        // Try to unsubscribe a listener that was never subscribed
        assertDoesNotThrow(() -> provider.unsubscribe(testInst1, mockListener));
        // Check logs if necessary, but no exception should be thrown.
    }

    @Test
    void testUnsubscribeFromNonSubscribedInstrument() {
         MarketDataListener mockListener = mock(MarketDataListener.class);
         // Try to unsubscribe from an instrument that has no listeners
         assertDoesNotThrow(() -> provider.unsubscribe(testInst1, mockListener));
    }
    
    @Test
    void testGetSupportedInstruments() {
        List<Instrument> supported = provider.getSupportedInstruments();
        assertNotNull(supported);
        assertEquals(2, supported.size());
        assertTrue(supported.contains(testInst1));
        assertTrue(supported.contains(testInst2));
        // Ensure it's unmodifiable (optional, depends on strictness of "unmodifiable" requirement)
        assertThrows(UnsupportedOperationException.class, () -> supported.add(unsupportedInst));
    }
}

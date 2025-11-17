package com.abc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class OrderPersistenceTest {

    @TempDir
    Path tempDir;

    private Order testOrder1;
    private Order testOrder2;
    private List<Order> pendingOrders;
    private List<Order> inProgressOrders;
    private List<Order> completedOrders;

    @BeforeEach
    void setUp() {
        // Set up test orders
        testOrder1 = new Order();
        testOrder1.setType("Restaurant");
        testOrder1.setSource("Test Restaurant 1");
        testOrder1.setOrder_date(System.currentTimeMillis());

        Item item1 = new Item();
        item1.setName("Pizza");
        item1.setQuantity(2);
        item1.setPrice(12.50);
        testOrder1.setItems(Arrays.asList(item1));

        testOrder2 = new Order();
        testOrder2.setType("Delivery");
        testOrder2.setSource("Test Restaurant 2");
        testOrder2.setOrder_date(System.currentTimeMillis());

        Item item2 = new Item();
        item2.setName("Burger");
        item2.setQuantity(1);
        item2.setPrice(8.99);
        testOrder2.setItems(Arrays.asList(item2));

        pendingOrders = Arrays.asList(testOrder1);
        inProgressOrders = Arrays.asList(testOrder2);
        completedOrders = Arrays.asList();
    }

    @Test
    void testOrderStateConstructor() {
        // Act
        OrderPersistence.OrderState state = new OrderPersistence.OrderState(pendingOrders, inProgressOrders, completedOrders);

        // Assert
        assertEquals(1, state.getPendingOrders().size(), "Should have 1 pending order");
        assertEquals(1, state.getInProgressOrders().size(), "Should have 1 in-progress order");
        assertEquals(0, state.getCompletedOrders().size(), "Should have 0 completed orders");
    }

    @Test
    void testOrderStateDefaultConstructor() {
        // Act
        OrderPersistence.OrderState state = new OrderPersistence.OrderState();

        // Assert
        assertNotNull(state.getPendingOrders(), "Pending orders list should not be null");
        assertNotNull(state.getInProgressOrders(), "In-progress orders list should not be null");
        assertNotNull(state.getCompletedOrders(), "Completed orders list should not be null");
        assertEquals(0, state.getPendingOrders().size(), "Should have 0 pending orders by default");
        assertEquals(0, state.getInProgressOrders().size(), "Should have 0 in-progress orders by default");
        assertEquals(0, state.getCompletedOrders().size(), "Should have 0 completed orders by default");
    }

    @Test
    void testOrderStateSettersAndGetters() {
        // Arrange
        OrderPersistence.OrderState state = new OrderPersistence.OrderState();

        // Act
        state.setPendingOrders(pendingOrders);
        state.setInProgressOrders(inProgressOrders);
        state.setCompletedOrders(completedOrders);

        // Assert
        assertEquals(pendingOrders, state.getPendingOrders(), "Pending orders should match");
        assertEquals(inProgressOrders, state.getInProgressOrders(), "In-progress orders should match");
        assertEquals(completedOrders, state.getCompletedOrders(), "Completed orders should match");
    }

    @Test
    void testSaveAndLoadOrderState() {
        // Act
        boolean saveResult = OrderPersistence.INSTANCE.saveOrderState(pendingOrders, inProgressOrders, completedOrders);
        OrderPersistence.OrderState loadResult = OrderPersistence.INSTANCE.loadOrderState();

        // Assert
        assertTrue(saveResult, "Save should succeed");
        assertNotNull(loadResult, "Load should return a state");

        OrderPersistence.OrderState state = loadResult;
        assertEquals(1, state.getPendingOrders().size(), "Should have 1 pending order");
        assertEquals(1, state.getInProgressOrders().size(), "Should have 1 in-progress order");
        assertEquals(0, state.getCompletedOrders().size(), "Should have 0 completed orders");

        // Verify order details
        Order loadedPending = state.getPendingOrders().get(0);
        assertEquals("Restaurant", loadedPending.getTypeOrDefault(), "Loaded pending order type should match");
        assertEquals("Test Restaurant 1", loadedPending.getSource(), "Loaded pending order source should match");

        Order loadedInProgress = state.getInProgressOrders().get(0);
        assertEquals("Delivery", loadedInProgress.getTypeOrDefault(), "Loaded in-progress order type should match");
        assertEquals("Test Restaurant 2", loadedInProgress.getSource(), "Loaded in-progress order source should match");
    }

    @Test
    void testLoadOrderStateWhenFileDoesNotExist() {
        // First ensure no saved state exists
        OrderPersistence.INSTANCE.clearSavedState();

        // Act
        OrderPersistence.OrderState result = OrderPersistence.INSTANCE.loadOrderState();

        // Assert
        assertNull(result, "Load should return null when file doesn't exist");
    }

    @Test
    void testHasSavedState() {
        // Clear any existing state first
        OrderPersistence.INSTANCE.clearSavedState();

        // Initially should be false
        assertFalse(OrderPersistence.INSTANCE.hasSavedState(), "Should not have saved state initially");

        // After saving should be true
        OrderPersistence.INSTANCE.saveOrderState(pendingOrders, inProgressOrders, completedOrders);
        assertTrue(OrderPersistence.INSTANCE.hasSavedState(), "Should have saved state after saving");
    }
}
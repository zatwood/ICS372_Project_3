package com.abc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

class OrderTest {

    private Order order;
    private Item item1;
    private Item item2;

    @BeforeEach
    void setUp() {
        order = new Order();
        item1 = new Item();
        item1.setName("Pizza");
        item1.setQuantity(2);
        item1.setPrice(12.50);

        item2 = new Item();
        item2.setName("Soda");
        item2.setQuantity(3);
        item2.setPrice(2.00);
    }

    @Test
    void testCalculateTotal() {
        // Arrange
        order.setItems(Arrays.asList(item1, item2));

        // Act
        double total = order.calculateTotal();

        // Assert
        assertEquals(31.00, total, 0.001, "Total should be sum of all item line totals");
    }

    @Test
    void testCalculateTotalWithNoItems() {
        // Arrange
        order.setItems(Arrays.asList());

        // Act
        double total = order.calculateTotal();

        // Assert
        assertEquals(0.0, total, 0.001, "Total should be 0 when items list is empty");
    }

    @Test
    void testIsValidWithValidOrder() {
        // Arrange
        order.setOrder_date(System.currentTimeMillis());
        order.setItems(Arrays.asList(item1));

        // Act & Assert
        assertTrue(order.isValid(), "Order should be valid with positive date and items");
    }

    @Test
    void testIsValidWithNoItems() {
        // Arrange
        order.setOrder_date(System.currentTimeMillis());
        order.setItems(Arrays.asList());

        // Act & Assert
        assertFalse(order.isValid(), "Order should be invalid with no items");
    }

    @Test
    void testSetTypeWithNull() {
        // Act
        order.setType(null);

        // Assert
        assertEquals("Unknown", order.getTypeOrDefault(), "Type should default to 'Unknown' when null is provided");
    }

    @Test
    void testSetOrderStatus() {
        // Act
        order.setStatus(Order.OrderStatus.IN_PROGRESS);

        // Assert
        assertEquals(Order.OrderStatus.IN_PROGRESS, order.getStatus(), "Status should be updated correctly");
    }

    @Test
    void testEquals() {
        // Arrange
        Order order1 = new Order();
        order1.setType("Restaurant");
        order1.setSource("Test");
        order1.setOrder_date(1609459200000L);
        order1.setItems(Arrays.asList(item1));

        Order order2 = new Order();
        order2.setType("Restaurant");
        order2.setSource("Test");
        order2.setOrder_date(1609459200000L);
        order2.setItems(Arrays.asList(item2)); // Different item but same count

        // Act & Assert
        assertEquals(order1, order2, "Orders should be equal when type, source, date, and item count match");
        assertEquals(order1.hashCode(), order2.hashCode(), "Equal orders should have same hash code");
    }

    @Test
    void testNotEquals() {
        // Arrange
        Order order1 = new Order();
        order1.setType("Restaurant");
        order1.setOrder_date(1609459200000L);

        Order order2 = new Order();
        order2.setType("Delivery");
        order2.setOrder_date(1609459200000L);

        // Act & Assert
        assertNotEquals(order1, order2, "Orders with different types should not be equal");
    }
}
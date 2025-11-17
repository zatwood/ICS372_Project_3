package com.abc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class ItemTest {

    private Item item;

    @BeforeEach
    void setUp() {
        item = new Item();
    }

    @Test
    void testGetLineTotal() {
        // Arrange
        item.setName("Test Item");
        item.setQuantity(3);
        item.setPrice(10.50);

        // Act
        double total = item.getLineTotal();

        // Assert
        assertEquals(31.50, total, 0.001, "Line total should be quantity * price");
    }



    @Test
    void testSetPriceEnsuresNonNegative() {
        // Act
        item.setPrice(-10.99);

        // Assert
        assertEquals(0.0, item.getPrice(), 0.001, "Price should be set to 0 when negative value is provided");
    }

    @Test
    void testSetNameWithNull() {
        // Act
        item.setName(null);

        // Assert
        String name = item.getName();
        assertNotNull(name, "Name should not be null");
        assertEquals("Unknown Item", name, "Name should default to 'Unknown Item' when null is provided");
    }

    @Test
    void testSetNameWithWhitespace() {
        // Act
        item.setName("  Test Item  ");

        // Assert
        assertEquals("Test Item", item.getName(), "Name should be trimmed of whitespace");
    }

    @Test
    void testToString() {
        // Arrange
        item.setName("Test Item");
        item.setQuantity(2);
        item.setPrice(5.99);

        // Act
        String result = item.toString();

        // Assert
        assertTrue(result.contains("Test Item"), "toString should contain the item name");
        assertTrue(result.contains("2"), "toString should contain the quantity");
        assertTrue(result.contains("5.99"), "toString should contain the price");
    }
}
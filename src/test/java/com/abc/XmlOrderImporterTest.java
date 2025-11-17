package com.abc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class XmlOrderImporterTest {

    @TempDir
    Path tempDir;

    private String validXmlContent;
    private String invalidXmlContent;

    @BeforeEach
    void setUp() {
        validXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<orders>\n" +
                "    <order>\n" +
                "        <type>Restaurant</type>\n" +
                "        <source>Test Restaurant</source>\n" +
                "        <order_date>2021-01-01 12:00:00</order_date>\n" +
                "        <items>\n" +
                "            <item>\n" +
                "                <name>Pizza</name>\n" +
                "                <quantity>2</quantity>\n" +
                "                <price>12.50</price>\n" +
                "            </item>\n" +
                "            <item>\n" +
                "                <name>Soda</name>\n" +
                "                <quantity>3</quantity>\n" +
                "                <price>2.00</price>\n" +
                "            </item>\n" +
                "        </items>\n" +
                "    </order>\n" +
                "</orders>";

        invalidXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<orders>\n" +
                "    <order>\n" +
                "        <type>Restaurant</type>\n" +
                "        <source>Test Restaurant</source>\n" +
                "        <order_date>invalid-date</order_date>\n" +
                "        <items>\n" +
                "            <item>\n" +
                "                <name>Pizza</name>\n" +
                "                <quantity>invalid-quantity</quantity>\n" +
                "                <price>invalid-price</price>\n" +
                "            </item>\n" +
                "        </items>\n" +
                "    </order>\n" +
                "</orders>";
    }

    @Test
    void testImportFromXmlWithValidFile() throws IOException {
        // Arrange
        Path xmlFile = tempDir.resolve("valid_orders.xml");
        Files.writeString(xmlFile, validXmlContent);

        // Act
        XmlOrderImporter.ImportResult result = XmlOrderImporter.INSTANCE.importFromXml(xmlFile.toString());

        // Assert
        assertNotNull(result, "Import result should not be null");
        assertEquals(1, result.getSuccessCount(), "Should import 1 order");
        assertFalse(result.hasErrors(), "Should not have errors with valid XML");
        assertEquals("valid_orders.xml", result.getSourceFile(), "Source file name should match");

        List<Order> orders = result.getImportedOrders();
        assertEquals(1, orders.size(), "Should have 1 order in the list");

        Order order = orders.get(0);
        assertEquals("Restaurant", order.getTypeOrDefault(), "Order type should match");
        assertEquals("Test Restaurant", order.getSource(), "Order source should match");
        assertTrue(order.isValid(), "Order should be valid");
        assertEquals(2, order.getItemsOrEmpty().size(), "Order should have 2 items");

        // Check first item
        Item pizza = order.getItemsOrEmpty().get(0);
        assertEquals("Pizza", pizza.getName(), "First item name should match");
        assertEquals(2, pizza.getQuantity(), "First item quantity should match");
        assertEquals(12.50, pizza.getPrice(), 0.001, "First item price should match");
    }

    @Test
    void testImportFromXmlWithInvalidFile() throws IOException {
        // Arrange
        Path xmlFile = tempDir.resolve("invalid_orders.xml");
        Files.writeString(xmlFile, invalidXmlContent);

        // Act
        XmlOrderImporter.ImportResult result = XmlOrderImporter.INSTANCE.importFromXml(xmlFile.toString());

        // Assert
        assertNotNull(result, "Import result should not be null");
        assertEquals(1, result.getSuccessCount(), "Should still import 1 order despite invalid data");
        // Note: Invalid data is handled gracefully with defaults, so no errors are reported
        assertFalse(result.hasErrors(), "Should not have errors - invalid data is handled with defaults");

        List<Order> orders = result.getImportedOrders();
        assertEquals(1, orders.size(), "Should have 1 order in the list");

        Order order = orders.get(0);
        assertEquals("Restaurant", order.getTypeOrDefault(), "Order type should match");
        assertEquals("Test Restaurant", order.getSource(), "Order source should match");

        // Check that invalid data was handled with defaults
        Item item = order.getItemsOrEmpty().get(0);
        assertEquals("Pizza", item.getName(), "Item name should still be valid");
        assertEquals(1, item.getQuantity(), "Invalid quantity should default to 1");
        assertEquals(0.0, item.getPrice(), 0.001, "Invalid price should default to 0.0");
    }

    @Test
    void testImportFromXmlWithNonExistentFile() {
        // Act
        XmlOrderImporter.ImportResult result = XmlOrderImporter.INSTANCE.importFromXml("non_existent_file.xml");

        // Assert
        assertNotNull(result, "Import result should not be null");
        assertEquals(0, result.getSuccessCount(), "Should not import any orders");
        assertTrue(result.hasErrors(), "Should have errors with non-existent file");
        assertTrue(result.getErrors().get(0).contains("Failed to process XML file"),
                "Error message should indicate file processing failure");
    }

    @Test
    void testImportFromDirectoryWithNonExistentDirectory() {
        // Act
        List<XmlOrderImporter.ImportResult> results = XmlOrderImporter.INSTANCE.importFromDirectory("non_existent_directory");

        // Assert
        assertEquals(0, results.size(), "Should return empty list for non-existent directory");
    }
}
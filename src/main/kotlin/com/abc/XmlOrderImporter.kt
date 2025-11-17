package com.abc

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XML importer for external restaurant orders.
 * Supports multiple order formats and handles errors gracefully.
 */
object XmlOrderImporter {

    private val DATE_FORMATTERS = arrayOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_DATE
    )

    /**
     * Represents the result of an XML import operation
     */
    data class ImportResult(
        val importedOrders: List<Order>,
        val errors: List<String>,
        val sourceFile: String
    ) {
        fun hasErrors(): Boolean = errors.isNotEmpty()
        fun getSuccessCount(): Int = importedOrders.size
        fun getErrorCount(): Int = errors.size
    }

    /**
     * Import orders from an XML file
     */
    fun importFromXml(filePath: String): ImportResult {
        val orders = mutableListOf<Order>()
        val errors = mutableListOf<String>()
        val fileName = Paths.get(filePath).fileName.toString()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(File(filePath))
            val root = document.documentElement

            // Process order elements
            val orderNodes = root.getElementsByTagName("order")
            for (i in 0 until orderNodes.length) {
                try {
                    val orderElement = orderNodes.item(i) as Element
                    val order = parseOrderElement(orderElement)
                    if (order != null) {
                        orders.add(order)
                    }
                } catch (e: Exception) {
                    errors.add("Error parsing order #${i + 1}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to process XML file: ${e.message}")
        }

        return ImportResult(orders, errors, fileName)
    }

    /**
     * Parse a single order element from XML
     */
    private fun parseOrderElement(orderElement: Element): Order? {
        return try {
            val order = Order()

            // Parse order type with fallback names
            order.type = getElementTextWithFallbacks(
                orderElement,
                "type", "order_type", "restaurant_type", "category", "Unknown"
            )

            // Parse source field with fallback names
            order.source = getElementTextWithFallbacks(
                orderElement,
                "source", "restaurant", "restaurant_name", "provider", "vendor", "Unknown"
            )

            // Parse order date with fallback names
            val dateStr = getElementTextWithFallbacks(
                orderElement,
                "order_date", "date", "timestamp", "created_at", null
            )

            if (dateStr != null) {
                val timestamp = parseDate(dateStr)
                order.order_date = if (timestamp > 0) timestamp else System.currentTimeMillis()
            } else {
                order.order_date = System.currentTimeMillis()
            }

            // Parse items
            order.items = parseItems(orderElement)

            order
        } catch (e: Exception) {
            System.err.println("Error parsing order element: ${e.message}")
            null
        }
    }

    /**
     * Parse items from an order element
     */
    private fun parseItems(orderElement: Element): List<Item> {
        val items = mutableListOf<Item>()

        // Find items container or use order element as fallback
        val itemsContainer = findElementContainer(
            orderElement,
            "items", "order_items", "products", "menu_items"
        )

        // Parse items with multiple possible tag names
        val itemNames = arrayOf("item", "order_item", "product", "menu_item")
        for (itemName in itemNames) {
            val itemNodes = itemsContainer.getElementsByTagName(itemName)
            for (i in 0 until itemNodes.length) {
                try {
                    val itemElement = itemNodes.item(i) as Element
                    val item = parseItemElement(itemElement)
                    if (item != null) {
                        items.add(item)
                    }
                } catch (e: Exception) {
                    System.err.println("Error parsing item: ${e.message}")
                }
            }
        }

        // If no items found, create a default item
        if (items.isEmpty()) {
            items.add(createDefaultItem())
        }

        return items
    }

    /**
     * Parse a single item element
     */
    private fun parseItemElement(itemElement: Element): Item? {
        val item = Item()

        // Parse item name with fallback names
        item.name = getElementTextWithFallbacks(
            itemElement,
            "name", "item_name", "product_name", "description", "Unknown Item"
        )

        // Parse quantity with fallback names
        val quantityStr = getElementTextWithFallbacks(
            itemElement,
            "quantity", "qty", "count", null
        )
        item.quantity = parseQuantity(quantityStr)

        // Parse price with fallback names
        val priceStr = getElementTextWithFallbacks(
            itemElement,
            "price", "unit_price", "cost", null
        )
        item.price = parsePrice(priceStr)

        return item
    }

    /**
     * Get text content of an element by tag name
     */
    private fun getElementText(parent: Element, tagName: String): String? {
        val nodes = parent.getElementsByTagName(tagName)
        if (nodes.length > 0) {
            val node = nodes.item(0)
            if (node != null) {
                val text = node.textContent
                return text?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    /**
     * Get element text with multiple fallback tag names
     */
    private fun getElementTextWithFallbacks(parent: Element, vararg fallbacks: String?): String {
        for (i in 0 until fallbacks.size - 1) {
            fallbacks[i]?.let { tagName ->
                val text = getElementText(parent, tagName)
                if (text != null) {
                    return text
                }
            }
        }
        return fallbacks.last() ?: "" // Return default value (last parameter)
    }

    /**
     * Parse quantity string with error handling
     */
    private fun parseQuantity(quantityStr: String?): Int {
        if (quantityStr == null) return 1
        return try {
            val quantity = quantityStr.trim().toInt()
            maxOf(quantity, 1)
        } catch (e: NumberFormatException) {
            System.err.println("Invalid quantity: $quantityStr, using default 1")
            1
        }
    }

    /**
     * Parse price string with error handling
     */
    private fun parsePrice(priceStr: String?): Double {
        if (priceStr == null) return 0.0
        return try {
            val cleanedPrice = priceStr.replace(Regex("[^\\d.-]"), "")
            val price = cleanedPrice.trim().toDouble()
            maxOf(price, 0.0)
        } catch (e: NumberFormatException) {
            System.err.println("Invalid price: $priceStr, using default 0.0")
            0.0
        }
    }

    /**
     * Find an element container by trying multiple possible tag names
     */
    private fun findElementContainer(parent: Element, vararg containerNames: String): Element {
        for (containerName in containerNames) {
            val containers = parent.getElementsByTagName(containerName)
            if (containers.length > 0) {
                return containers.item(0) as Element
            }
        }
        return parent // Fallback to parent element
    }

    /**
     * Create a default item when no items are found
     */
    private fun createDefaultItem(): Item {
        return Item().apply {
            name = "Unknown Item"
            quantity = 1
            price = 0.0
        }
    }

    /**
     * Parse date string with multiple format support
     */
    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) {
            return 0
        }

        val trimmedDateStr = dateStr.trim()

        // Try parsing as timestamp first (most efficient)
        val timestamp = parseTimestamp(trimmedDateStr)
        if (timestamp > 0) {
            return timestamp
        }

        // Try each date format
        for (formatter in DATE_FORMATTERS) {
            val parsedTimestamp = parseWithFormatter(trimmedDateStr, formatter)
            if (parsedTimestamp > 0) {
                return parsedTimestamp
            }
        }

        return 0
    }

    /**
     * Try parsing as a timestamp
     */
    private fun parseTimestamp(dateStr: String): Long {
        return try {
            val timestamp = dateStr.toLong()
            if (timestamp > 0 && timestamp < 4102444800000L) timestamp else 0
        } catch (e: NumberFormatException) {
            0
        }
    }

    /**
     * Parse date string with specific formatter
     */
    private fun parseWithFormatter(dateStr: String, formatter: DateTimeFormatter): Long {
        return try {
            if (formatter == DateTimeFormatter.ISO_DATE) {
                val date = LocalDate.parse(dateStr, formatter)
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                val dateTime = LocalDateTime.parse(dateStr, formatter)
                dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        } catch (e: DateTimeParseException) {
            0
        }
    }

    /**
     * Import all XML files from a directory
     */
    fun importFromDirectory(directoryPath: String): List<ImportResult> {
        val results = mutableListOf<ImportResult>()

        try {
            val dirPath = Paths.get(directoryPath)
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                System.err.println("Directory not found: $directoryPath")
                return results
            }

            Files.newDirectoryStream(dirPath, "*.xml").use { stream ->
                for (filePath in stream) {
                    val result = importFromXml(filePath.toString())
                    results.add(result)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error reading XML files from directory: ${e.message}")
        }

        return results
    }
}

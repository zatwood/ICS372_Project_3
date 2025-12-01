package com.abc

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.CopyOnWriteArrayList
import javax.xml.parsers.DocumentBuilderFactory

object OrderIn {
    private val mapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
    }

    private val processedFiles = mutableSetOf<String>()
    private var watchService: WatchService? = null
    private var isWatching = false
    private val listeners = CopyOnWriteArrayList<OrderUpdateListener>()

    interface OrderUpdateListener {
        fun onOrdersUpdated(newOrders: List<Order>)
        fun onOrdersReloaded(allOrders: List<Order>)
    }

    fun addOrderUpdateListener(listener: OrderUpdateListener) {
        listeners.add(listener)
    }

    fun removeOrderUpdateListener(listener: OrderUpdateListener) {
        listeners.remove(listener)
    }

    // New: generic importer interface used by JSON and XML importers
    interface OrderImporter {
        // Return true if this importer can handle the given filename
        fun canImport(fileName: String): Boolean

        // Read a single order from a file (if the file contains one order)
        fun readOrder(filePath: String): Order?

        // Read all orders from a file (useful for XML files containing multiple orders)
        fun readAllOrders(filePath: String): List<Order>
    }

    // JSON importer implementation (single-order JSON files)
    private object JsonOrderImporter : OrderImporter {
        override fun canImport(fileName: String): Boolean {
            val lower = fileName.lowercase()
            return lower.endsWith(".json") && !lower.endsWith("orders_out.json")
        }

        override fun readOrder(filePath: String): Order? {
            return try {
                val wrapper = mapper.readValue(File(filePath), OrderWrapper::class.java)
                val order = wrapper.order

                if (order != null && order.isValid()) {
                    order
                } else {
                    System.err.println("Invalid order data in JSON file: $filePath")
                    null
                }
            } catch (e: Exception) {
                System.err.println("Error reading JSON order from $filePath: ${e.message}")
                null
            }
        }

        override fun readAllOrders(filePath: String): List<Order> {
            readOrder(filePath)?.let { return listOf(it) }
            return emptyList()
        }
    }

    // --- Embedded XML importer implementation (moved from XmlOrderImporter) ---

    private val DATE_FORMATTERS = arrayOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_DATE
    )

    data class XmlImportResult(
        val importedOrders: List<Order>,
        val errors: List<String>,
        val sourceFile: String
    ) {
        fun hasErrors(): Boolean = errors.isNotEmpty()
        fun getSuccessCount(): Int = importedOrders.size
        fun getErrorCount(): Int = errors.size
        // Java-friendly getters are generated for properties
    }

    // Public ImportResult type for compatibility with tests
    class ImportResult(private val result: XmlImportResult) {
        fun hasErrors(): Boolean = result.hasErrors()
        fun getSuccessCount(): Int = result.getSuccessCount()
        fun getErrorCount(): Int = result.getErrorCount()
        fun getImportedOrders(): List<Order> = result.importedOrders
        fun getErrors(): List<String> = result.errors
        fun getSourceFile(): String = result.sourceFile
    }

    // Public API so external callers (including tests) can call OrderIn.INSTANCE.importFromXml
    fun importFromXml(filePath: String): ImportResult {
        val res = internalImportFromXml(filePath)
        return ImportResult(res)
    }

    fun importFromDirectory(directoryPath: String): List<ImportResult> {
        val results = mutableListOf<ImportResult>()
        val list = internalImportFromDirectory(directoryPath)
        list.forEach { results.add(ImportResult(it)) }
        return results
    }

    private fun internalImportFromXml(filePath: String): XmlImportResult {
        val orders = mutableListOf<Order>()
        val errors = mutableListOf<String>()
        val fileName = Paths.get(filePath).fileName.toString()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(File(filePath))
            val root = document.documentElement

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

        return XmlImportResult(orders, errors, fileName)
    }

    private fun internalImportFromDirectory(directoryPath: String): List<XmlImportResult> {
        val results = mutableListOf<XmlImportResult>()

        try {
            val dirPath = Paths.get(directoryPath)
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                System.err.println("Directory not found: $directoryPath")
                return results
            }

            Files.newDirectoryStream(dirPath, "*.xml").use { stream ->
                for (filePath in stream) {
                    val result = internalImportFromXml(filePath.toString())
                    results.add(result)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error reading XML files from directory: ${e.message}")
        }

        return results
    }

    private fun parseOrderElement(orderElement: Element): Order? {
        return try {
            val order = Order()

            order.type = getElementTextWithFallbacks(
                orderElement,
                "type", "order_type", "restaurant_type", "category", "Unknown"
            )

            order.source = getElementTextWithFallbacks(
                orderElement,
                "source", "restaurant", "restaurant_name", "provider", "vendor", "Unknown"
            )

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

            order.items = parseItems(orderElement)

            order
        } catch (e: Exception) {
            System.err.println("Error parsing order element: ${e.message}")
            null
        }
    }

    private fun parseItems(orderElement: Element): List<Item> {
        val items = mutableListOf<Item>()

        val itemsContainer = findElementContainer(
            orderElement,
            "items", "order_items", "products", "menu_items"
        )

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

        if (items.isEmpty()) {
            items.add(createDefaultItem())
        }

        return items
    }

    private fun parseItemElement(itemElement: Element): Item? {
        val item = Item()

        item.name = getElementTextWithFallbacks(
            itemElement,
            "name", "item_name", "product_name", "description", "Unknown Item"
        )

        val quantityStr = getElementTextWithFallbacks(
            itemElement,
            "quantity", "qty", "count", null
        )
        item.quantity = parseQuantity(quantityStr)

        val priceStr = getElementTextWithFallbacks(
            itemElement,
            "price", "unit_price", "cost", null
        )
        item.price = parsePrice(priceStr)

        return item
    }

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

    private fun getElementTextWithFallbacks(parent: Element, vararg fallbacks: String?): String? {
        for (i in 0 until fallbacks.size - 1) {
            fallbacks[i]?.let { tagName ->
                val text = getElementText(parent, tagName)
                if (text != null) {
                    return text
                }
            }
        }
        return fallbacks.last()
    }

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

    private fun findElementContainer(parent: Element, vararg containerNames: String): Element {
        for (containerName in containerNames) {
            val containers = parent.getElementsByTagName(containerName)
            if (containers.length > 0) {
                return containers.item(0) as Element
            }
        }
        return parent
    }

    private fun createDefaultItem(): Item {
        return Item().apply {
            name = "Unknown Item"
            quantity = 1
            price = 0.0
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) {
            return 0
        }

        val trimmedDateStr = dateStr.trim()

        val timestamp = parseTimestamp(trimmedDateStr)
        if (timestamp > 0) {
            return timestamp
        }

        for (formatter in DATE_FORMATTERS) {
            val parsedTimestamp = parseWithFormatter(trimmedDateStr, formatter)
            if (parsedTimestamp > 0) {
                return parsedTimestamp
            }
        }

        return 0
    }

    private fun parseTimestamp(dateStr: String): Long {
        return try {
            val timestamp = dateStr.toLong()
            if (timestamp > 0 && timestamp < 4102444800000L) timestamp else 0
        } catch (e: NumberFormatException) {
            0
        }
    }

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

    // XML adapter that uses the embedded XML import logic
    private object XmlOrderImporterAdapter : OrderImporter {
        override fun canImport(fileName: String): Boolean {
            return fileName.lowercase().endsWith(".xml")
        }

        override fun readOrder(filePath: String): Order? {
            return try {
                val result = internalImportFromXml(filePath)
                if (result.getSuccessCount() > 0) {
                    result.importedOrders[0]
                } else {
                    System.err.println("No valid orders found in XML file: $filePath")
                    if (result.hasErrors()) {
                        result.errors.forEach { error -> System.err.println("  XML Error: $error") }
                    }
                    null
                }
            } catch (e: Exception) {
                System.err.println("Error reading XML order from $filePath: ${e.message}")
                null
            }
        }

        override fun readAllOrders(filePath: String): List<Order> {
            val result = internalImportFromXml(filePath)
            return result.importedOrders
        }
    }

    // List of available importers (order matters if a file type could be ambiguous)
    private val importers: List<OrderImporter> = listOf(XmlOrderImporterAdapter, JsonOrderImporter)

    // Public readOrder now delegates to the appropriate importer
    fun readOrder(filename: String): Order? {
        return try {
            val filePath = Paths.get(filename)
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                System.err.println("File not found: $filename")
                return null
            }

            val fileName = filePath.fileName.toString()
            val importer = importers.firstOrNull { it.canImport(fileName) }

            if (importer == null) {
                System.err.println("Unsupported file type: $filename")
                return null
            }

            importer.readOrder(filePath.toString())
        } catch (e: Exception) {
            System.err.println("Error reading order from $filename: ${e.message}")
            null
        }
    }

    fun readOrdersFromDirectory(directoryPath: String): List<Order> {
        val orders = mutableListOf<Order>()
        // Don't clear processedFiles - this prevents duplicate imports on refresh
        // processedFiles should only be cleared on app startup or explicit reload

        try {
            var dirPath = Paths.get(directoryPath)
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                // Try alternative paths
                dirPath = Paths.get("uploads")
                if (!Files.exists(dirPath)) {
                    dirPath = Paths.get("src/java")
                    if (!Files.exists(dirPath)) {
                        System.err.println("Orders directory not found")
                        return orders
                    }
                }
            }

            // Use a combined directory stream to pick up both json and xml files
            Files.newDirectoryStream(dirPath, "*.json").use { jsonStream ->
                for (filePath in jsonStream) {
                    val fileName = filePath.fileName.toString()
                    if (fileName != "orders_out.json" && !processedFiles.contains(fileName)) {
                        JsonOrderImporter.readOrder(filePath.toString())?.let { order ->
                            orders.add(order)
                            processedFiles.add(fileName)
                        }
                    }
                }
            }

            Files.newDirectoryStream(dirPath, "*.xml").use { xmlStream ->
                for (filePath in xmlStream) {
                    val fileName = filePath.fileName.toString()
                    if (!processedFiles.contains(fileName)) {
                        val xmlOrders = XmlOrderImporterAdapter.readAllOrders(filePath.toString())
                        if (xmlOrders.isNotEmpty()) {
                            orders.addAll(xmlOrders)
                            processedFiles.add(fileName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Error reading orders from directory: ${e.message}")
        }

        return orders
    }

    fun startFileWatcher(directoryPath: String) {
        if (isWatching) {
            println("File watcher is already running")
            return
        }

        val watcherThread = Thread {
            try {
                val watchPath = Paths.get(directoryPath)

                // Create directory if it doesn't exist
                if (!Files.exists(watchPath)) {
                    Files.createDirectories(watchPath)
                    println("Created directory: ${watchPath.toAbsolutePath()}")
                }

                watchService = FileSystems.getDefault().newWatchService()
                watchPath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )

                println("Started watching directory: ${watchPath.toAbsolutePath()}")
                isWatching = true

                while (isWatching) {
                    val key: WatchKey = try {
                        watchService?.take() ?: break
                    } catch (e: InterruptedException) {
                        println("File watcher interrupted")
                        break
                    }

                    for (event in key.pollEvents()) {
                        val kind = event.kind()

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue
                        }

                        @Suppress("UNCHECKED_CAST")
                        val ev = event as WatchEvent<Path>
                        val fileName = ev.context()

                        val fileNameStr = fileName.toString()
                        val isJsonFile = fileNameStr.endsWith(".json") && fileNameStr != "orders_out.json"
                        val isXmlFile = fileNameStr.endsWith(".xml")

                        if (isJsonFile || isXmlFile) {
                            println("File event: ${kind.name()} - $fileName (${if (isJsonFile) "JSON" else "XML"})")

                            if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                                kind == StandardWatchEventKinds.ENTRY_MODIFY
                            ) {
                                // Small delay to ensure the file is completely written
                                Thread.sleep(100)

                                val fullPath = watchPath.resolve(fileName)
                                val fileKey = fileName.toString()

                                if (!processedFiles.contains(fileKey)) {
                                    if (isXmlFile) {
                                        // Handle XML files with potentially multiple orders
                                        val newOrders = XmlOrderImporterAdapter.readAllOrders(fullPath.toString())
                                        if (newOrders.isNotEmpty()) {
                                            processedFiles.add(fileKey)
                                            notifyNewOrders(newOrders)
                                        }
                                    } else {
                                        // Handle JSON files (single order)
                                        val newOrder = JsonOrderImporter.readOrder(fullPath.toString())
                                        if (newOrder != null) {
                                            processedFiles.add(fileKey)
                                            notifyNewOrders(listOf(newOrder))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val valid = key.reset()
                    if (!valid) {
                        println("Watch key no longer valid")
                        break
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error in file watcher: ${e.message}")
            } finally {
                isWatching = false
                println("File watcher stopped")
            }
        }

        watcherThread.isDaemon = true
        watcherThread.name = "Order-File-Watcher"
        watcherThread.start()
    }

    fun startPollingWatcher(directoryPath: String) {
        if (isWatching) {
            println("Polling watcher is already running")
            return
        }

        val pollingThread = Thread {
            try {
                val watchPath = Paths.get(directoryPath)

                // Create directory if it doesn't exist
                if (!Files.exists(watchPath)) {
                    Files.createDirectories(watchPath)
                    println("Created directory: ${watchPath.toAbsolutePath()}")
                }

                println("Started polling directory: ${watchPath.toAbsolutePath()}")
                isWatching = true

                var lastFiles = mutableSetOf<String>()

                while (isWatching) {
                    try {
                        Thread.sleep(2000) // Poll for new orders every 2 seconds

                        val currentFiles = mutableSetOf<String>()
                        val newOrders = mutableListOf<Order>()

                        Files.newDirectoryStream(watchPath, "*.{json,xml}").use { stream ->
                            for (filePath in stream) {
                                val fileName = filePath.fileName.toString()
                                val isJsonFile = fileName.endsWith(".json") && fileName != "orders_out.json"
                                val isXmlFile = fileName.endsWith(".xml")

                                if (isJsonFile || isXmlFile) {
                                    currentFiles.add(fileName)

                                    if (!processedFiles.contains(fileName) && !lastFiles.contains(fileName)) {
                                        if (isXmlFile) {
                                            // Handle XML files with potentially multiple orders
                                            val xmlOrders = XmlOrderImporterAdapter.readAllOrders(filePath.toString())
                                            if (xmlOrders.isNotEmpty()) {
                                                newOrders.addAll(xmlOrders)
                                                processedFiles.add(fileName)
                                            }
                                        } else {
                                            // Handle JSON files (single order)
                                            val order = JsonOrderImporter.readOrder(filePath.toString())
                                            if (order != null) {
                                                newOrders.add(order)
                                                processedFiles.add(fileName)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (newOrders.isNotEmpty()) {
                            notifyNewOrders(newOrders)
                        }

                        lastFiles = currentFiles
                    } catch (e: InterruptedException) {
                        println("Polling watcher interrupted")
                        break
                    } catch (e: Exception) {
                        System.err.println("Error in polling watcher: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error starting polling watcher: ${e.message}")
            } finally {
                isWatching = false
                println("Polling watcher stopped")
            }
        }

        pollingThread.isDaemon = true
        pollingThread.name = "Order-Polling-Watcher"
        pollingThread.start()
    }

    fun stopFileWatcher() {
        isWatching = false
        watchService?.let {
            try {
                it.close()
            } catch (e: IOException) {
                System.err.println("Error closing watch service: ${e.message}")
            }
        }
    }

    private fun notifyNewOrders(newOrders: List<Order>) {
        if (newOrders.isNotEmpty()) {
            for (listener in listeners) {
                try {
                    listener.onOrdersUpdated(newOrders)
                } catch (e: Exception) {
                    System.err.println("Error notifying listener: ${e.message}")
                }
            }
        }
    }

    fun notifyReloadAllOrders(allOrders: List<Order>) {
        for (listener in listeners) {
            try {
                listener.onOrdersReloaded(allOrders)
            } catch (e: Exception) {
                System.err.println("Error notifying listener: ${e.message}")
            }
        }
    }

    fun clearProcessedFiles() {
        processedFiles.clear()
    }

    // Inner class for OrderWrapper
    data class OrderWrapper(
        var order: Order? = null
    )
}

package com.abc

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.util.concurrent.CopyOnWriteArrayList

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

    fun readOrder(filename: String): Order? {
        return try {
            val filePath = Paths.get(filename)
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                System.err.println("File not found: $filename")
                return null
            }

            // Check file extension to determine parsing method
            val fileName = filePath.fileName.toString().lowercase()

            when {
                fileName.endsWith(".xml") -> readOrderFromXml(filename)
                fileName.endsWith(".json") -> readOrderFromJson(filename)
                else -> {
                    System.err.println("Unsupported file type: $filename")
                    null
                }
            }
        } catch (e: Exception) {
            System.err.println("Error reading order from $filename: ${e.message}")
            null
        }
    }

    private fun readOrderFromJson(filename: String): Order? {
        return try {
            val wrapper = mapper.readValue(File(filename), OrderWrapper::class.java)
            val order = wrapper.order

            if (order != null && order.isValid()) {
                order
            } else {
                System.err.println("Invalid order data in JSON file: $filename")
                null
            }
        } catch (e: Exception) {
            System.err.println("Error reading JSON order from $filename: ${e.message}")
            null
        }
    }

    private fun readOrderFromXml(filename: String): Order? {
        return try {
            val result = XmlOrderImporter.importFromXml(filename)

            if (result.getSuccessCount() > 0) {
                // Return the first valid order from the XML file
                result.importedOrders[0]
            } else {
                System.err.println("No valid orders found in XML file: $filename")
                if (result.hasErrors()) {
                    result.errors.forEach { error ->
                        System.err.println("  XML Error: $error")
                    }
                }
                null
            }
        } catch (e: Exception) {
            System.err.println("Error reading XML order from $filename: ${e.message}")
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

            // Read JSON files
            Files.newDirectoryStream(dirPath, "*.json").use { jsonStream ->
                for (filePath in jsonStream) {
                    val fileName = filePath.fileName.toString()
                    if (fileName != "orders_out.json" && !processedFiles.contains(fileName)) {
                        readOrder(filePath.toString())?.let { order ->
                            orders.add(order)
                            processedFiles.add(fileName)
                        }
                    }
                }
            }

            // Read XML files
            Files.newDirectoryStream(dirPath, "*.xml").use { xmlStream ->
                for (filePath in xmlStream) {
                    val fileName = filePath.fileName.toString()
                    if (!processedFiles.contains(fileName)) {
                        // For XML files, we need to handle multiple orders per file
                        val xmlOrders = readAllOrdersFromXml(filePath.toString())
                        orders.addAll(xmlOrders)
                        processedFiles.add(fileName)
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Error reading orders from directory: ${e.message}")
        }

        return orders
    }

    private fun readAllOrdersFromXml(filename: String): List<Order> {
        val orders = mutableListOf<Order>()
        try {
            val result = XmlOrderImporter.importFromXml(filename)
            orders.addAll(result.importedOrders)

            if (result.hasErrors()) {
                System.err.println("Errors in XML file $filename:")
                result.errors.forEach { error ->
                    System.err.println("  $error")
                }
            }
        } catch (e: Exception) {
            System.err.println("Error reading XML orders from $filename: ${e.message}")
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
                                        val newOrders = readAllOrdersFromXml(fullPath.toString())
                                        if (newOrders.isNotEmpty()) {
                                            processedFiles.add(fileKey)
                                            notifyNewOrders(newOrders)
                                        }
                                    } else {
                                        // Handle JSON files (single order)
                                        val newOrder = readOrder(fullPath.toString())
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
                                            val xmlOrders = readAllOrdersFromXml(filePath.toString())
                                            if (xmlOrders.isNotEmpty()) {
                                                newOrders.addAll(xmlOrders)
                                                processedFiles.add(fileName)
                                            }
                                        } else {
                                            // Handle JSON files (single order)
                                            val order = readOrder(filePath.toString())
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

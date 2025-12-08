package com.abc

import javafx.application.Platform
import javafx.collections.ObservableList

@Suppress("unused")
object OrderFileManager {

    fun loadOrders(
        uploadsDir: String,
        pending: ObservableList<Order>,
        inProgress: ObservableList<Order>,
        completed: ObservableList<Order>,
        onAdded: (List<Order>) -> Unit
    ) {
        val orders = OrderIn.readOrdersFromDirectory(uploadsDir)
        val newOrders = mutableListOf<Order>()

        for (order in orders) {
            val alreadyExists = pending.contains(order) || inProgress.contains(order) || completed.contains(order)
            if (!alreadyExists) {
                newOrders.add(order)
            }
        }

        if (newOrders.isNotEmpty()) {
            onAdded(newOrders)
        }
    }

    fun startFileWatcher(
        watchDirectory: String,
        autoRefreshEnabled: () -> Boolean,
        onNewOrders: (List<Order>) -> Unit,
        onReload: () -> Unit
    ) {
        OrderIn.addOrderUpdateListener(object : OrderIn.OrderUpdateListener {
            override fun onOrdersUpdated(newOrders: List<Order>) {
                Platform.runLater {
                    if (autoRefreshEnabled()) {
                        // Let caller handle adding and dedupe
                        try {
                            onNewOrders(newOrders)
                        } catch (e: Exception) {
                            println("Error in onNewOrders handler: ${e.message}")
                        }
                    }
                }
            }

            override fun onOrdersReloaded(allOrders: List<Order>) {
                Platform.runLater {
                    onReload()
                }
            }
        })

        try {
            OrderIn.startFileWatcher(watchDirectory)
            println("File system watcher started successfully")
        } catch (e: Exception) {
            println("File system watcher not available, using polling: ${e.message}")
            OrderIn.startPollingWatcher(watchDirectory)
        }
    }

    //Find the source file for a specific order.
    fun findOrderFile(order: Order): String? {
        try {
            val uploadsDir = java.nio.file.Paths.get("uploads")
            if (!java.nio.file.Files.exists(uploadsDir)) {
                return null
            }

            java.nio.file.Files.newDirectoryStream(uploadsDir, "*.{json,xml}").use { stream ->
                val files = stream.toList()

                // Strategy 1: Match by order source in filename
                if (order.source != null) {
                    val normalizedSource = order.source!!.lowercase().replace(" ", "").replace("_", "")

                    for (filePath in files) {
                        val fileName = filePath.fileName.toString().lowercase()
                        val normalizedFileName = fileName.replace(" ", "").replace("_", "")

                        // Check if the filename contains the source
                        if (normalizedFileName.contains(normalizedSource)) {
                            println("Found file for order ${order.source}: $filePath")
                            return filePath.toString()
                        }
                    }
                }


                // Match by order date (timestamp)
                // This is a fallback if source doesn't work
                for (filePath in files) {
                    val fileName = filePath.fileName.toString()
                    // Check if filename contains the order timestamp
                    if (fileName.contains(order.order_date.toString())) {
                        println("Found file for order by timestamp: $filePath")
                        return filePath.toString()
                    }
                }

                // NO FALLBACK - if we can't find a specific match, return null
                println("⚠️ Could not find specific file for order: ${order.source ?: "unknown"} (date: ${order.order_date})")
                return null
            }
        } catch (e: Exception) {
            System.err.println("Error finding order file: ${e.message}")
            return null
        }
    }

    //Delete the source file for an order.
    fun deleteOrderFile(order: Order, orderToFileMap: MutableMap<Order, String>): Boolean {
        println("🗑️ Attempting to delete file for order: ${order.source ?: "unknown"}")

        // Always save to canceled orders first
        val savedToCanceled = OrderPersistence.saveCanceledOrder(order)
        if (savedToCanceled) {
            println("   ✅ Order saved to canceledOrders.json")
        }

        // Try to get file path from tracking map first
        var filePath = orderToFileMap[order]
        if (filePath != null) {
            println("   📍 Found tracked file path: $filePath")
        } else {
            println("   🔍 File not tracked, searching for it...")
            filePath = findOrderFile(order)
            if (filePath != null) {
                println("   📍 Found file: $filePath")
            } else {
                println("   ⚠️ Could not find source file for order")
            }
        }

        var fileDeleted = false
        if (filePath != null) {
            try {
                val path = java.nio.file.Paths.get(filePath)
                if (java.nio.file.Files.exists(path)) {
                    java.nio.file.Files.delete(path)
                    println("   ✅ Deleted source file: $path")
                    fileDeleted = true
                } else {
                    println("   ⚠️ Source file no longer exists: $path")
                }
            } catch (e: java.io.IOException) {
                System.err.println("   ❌ Error deleting source file $filePath: ${e.message}")
                e.printStackTrace()
            }
        }

        return savedToCanceled || fileDeleted
    }
}
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
                order.status = Order.OrderStatus.PENDING
                pending.add(order)
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

    fun findOrderFile(order: Order): String? {
        try {
            val uploadsDir = java.nio.file.Paths.get("uploads")
            if (java.nio.file.Files.exists(uploadsDir)) {
                java.nio.file.Files.newDirectoryStream(uploadsDir, "*.{json,xml}").use { stream ->
                    for (filePath in stream) {
                        val fileName = filePath.fileName.toString().lowercase()

                        if (order.source != null &&
                            fileName.contains(order.source!!.lowercase().replace(" ", ""))
                        ) {
                            return filePath.toString()
                        }

                        if (fileName.contains("order") || fileName.contains("restaurant")) {
                            return filePath.toString()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Error finding order file: ${e.message}")
        }
        return null
    }

    fun deleteOrderFile(order: Order, orderToFileMap: MutableMap<Order, String>): Boolean {
        val savedToCanceled = OrderPersistence.saveCanceledOrder(order)

        var filePath = orderToFileMap[order]
        if (filePath == null) {
            filePath = findOrderFile(order)
        }

        var fileDeleted = false
        if (filePath != null) {
            try {
                val path = java.nio.file.Paths.get(filePath)
                if (java.nio.file.Files.exists(path)) {
                    java.nio.file.Files.delete(path)
                    println("Deleted source file: $path")
                    fileDeleted = true
                } else {
                    println("Source file not found for deletion: $path")
                }
            } catch (e: java.io.IOException) {
                System.err.println("Error deleting source file $filePath: ${e.message}")
            }
        }

        return savedToCanceled || fileDeleted
    }
}

package com.abc

import javafx.collections.FXCollections
import javafx.collections.ObservableList


class OrderManager {

    // Observable lists for UI binding
    val pendingOrders: ObservableList<Order> = FXCollections.observableArrayList()
    val inProgressOrders: ObservableList<Order> = FXCollections.observableArrayList()
    val completedOrders: ObservableList<Order> = FXCollections.observableArrayList()

    // Track which orders came from which files for deletion
    private val orderToFileMap = mutableMapOf<Order, String>()

    //Strategy pattern helper for executing order state transitions
    private fun executeOrderTransition(
        order: Order,
        sourceList: ObservableList<Order>,
        targetList: ObservableList<Order>?,
        newStatus: Order.OrderStatus?,
        validationMessage: String,
        successMessage: String,
        additionalAction: ((Order) -> Unit)? = null
    ): OperationResult {
        if (!sourceList.contains(order)) {
            return OperationResult.failure(validationMessage)
        }

        // Execute additional action (like file deletion) before moving
        additionalAction?.invoke(order)

        // Update status if provided
        newStatus?.let { order.status = it }

        // Move order between lists
        sourceList.remove(order)
        targetList?.add(order)

        // Persist changes after state transition
        OrderPersistence.saveOrderState(
            pendingOrders.toList(),
            inProgressOrders.toList(),
            completedOrders.toList()
        )

        return OperationResult.success(successMessage)
    }

    //Order State Transitions
    fun startOrder(order: Order): OperationResult {
        return executeOrderTransition(
            order = order,
            sourceList = pendingOrders,
            targetList = inProgressOrders,
            newStatus = Order.OrderStatus.IN_PROGRESS,
            validationMessage = "Order not found in pending list",
            successMessage = "Order moved to In-Progress"
        )
    }

    fun startOrders(orders: List<Order>): BatchOperationResult {
        val results = mutableListOf<String>()
        var successCount = 0

        for (order in orders) {
            val result = startOrder(order)
            if (result.success) {
                successCount++
            } else {
                results.add("Failed: ${order.source} - ${result.message}")
            }
        }

        return BatchOperationResult(
            successCount = successCount,
            failureCount = orders.size - successCount,
            details = results
        )
    }

    fun completeOrder(order: Order): OperationResult {
        return executeOrderTransition(
            order = order,
            sourceList = inProgressOrders,
            targetList = completedOrders,
            newStatus = Order.OrderStatus.COMPLETED,
            validationMessage = "Order not found in in-progress list",
            successMessage = "Order marked as completed"
        )
    }

    fun completeOrders(orders: List<Order>): BatchOperationResult {
        val results = mutableListOf<String>()
        var successCount = 0

        for (order in orders) {
            val result = completeOrder(order)
            if (result.success) {
                successCount++
            } else {
                results.add("Failed: ${order.source} - ${result.message}")
            }
        }

        return BatchOperationResult(
            successCount = successCount,
            failureCount = orders.size - successCount,
            details = results
        )
    }

    fun undoStart(order: Order): OperationResult {
        return executeOrderTransition(
            order = order,
            sourceList = inProgressOrders,
            targetList = pendingOrders,
            newStatus = Order.OrderStatus.PENDING,
            validationMessage = "Order not found in in-progress list",
            successMessage = "Order moved back to pending"
        )
    }

    fun undoStartBatch(orders: List<Order>): BatchOperationResult {
        val results = mutableListOf<String>()
        var successCount = 0

        for (order in orders) {
            val result = undoStart(order)
            if (result.success) {
                successCount++
            } else {
                results.add("Failed: ${order.source} - ${result.message}")
            }
        }

        return BatchOperationResult(
            successCount = successCount,
            failureCount = orders.size - successCount,
            details = results
        )
    }

    fun undoComplete(order: Order): OperationResult {
        return executeOrderTransition(
            order = order,
            sourceList = completedOrders,
            targetList = inProgressOrders,
            newStatus = Order.OrderStatus.IN_PROGRESS,
            validationMessage = "Order not found in completed list",
            successMessage = "Order moved back to in-progress"
        )
    }

    fun undoCompleteBatch(orders: List<Order>): BatchOperationResult {
        val results = mutableListOf<String>()
        var successCount = 0

        for (order in orders) {
            val result = undoComplete(order)
            if (result.success) {
                successCount++
            } else {
                results.add("Failed: ${order.source} - ${result.message}")
            }
        }

        return BatchOperationResult(
            successCount = successCount,
            failureCount = orders.size - successCount,
            details = results
        )
    }

    //Order Deletion
    fun deleteOrder(order: Order): DeletionResult {
        // Find which list contains the order
        val sourceList = when {
            pendingOrders.contains(order) -> pendingOrders
            inProgressOrders.contains(order) -> inProgressOrders
            completedOrders.contains(order) -> completedOrders
            else -> return DeletionResult(
                success = false,
                fileDeleted = false,
                message = "Order not found in any list"
            )
        }

        // Use strategy pattern for deletion
        var fileDeleted = false
        val result = executeOrderTransition(
            order = order,
            sourceList = sourceList,
            targetList = null, // Deleting, not moving
            newStatus = null,  // No status change needed
            validationMessage = "Order not found",
            successMessage = "Order deleted",
            additionalAction = { ord ->
                fileDeleted = OrderFileManager.deleteOrderFile(ord, orderToFileMap)
                orderToFileMap.remove(ord)
            }
        )

        return DeletionResult(
            success = result.success,
            fileDeleted = fileDeleted,
            message = if (fileDeleted) {
                "Order deleted and source file removed"
            } else {
                "Order deleted (source file not found)"
            }
        )
    }

    fun deleteOrders(orders: List<Order>): BatchDeletionResult {
        var filesDeletedCount = 0
        var successCount = 0
        val failures = mutableListOf<String>()

        for (order in orders) {
            val result = deleteOrder(order)
            if (result.success) {
                successCount++
                if (result.fileDeleted) {
                    filesDeletedCount++
                }
            } else {
                failures.add("${order.source}: ${result.message}")
            }
        }

        return BatchDeletionResult(
            successCount = successCount,
            filesDeletedCount = filesDeletedCount,
            failures = failures
        )
    }

    //Order Updates

    fun updateOrderItems(order: Order, newItems: List<Item>) {
        order.items = newItems
        // Persist changes after update
        OrderPersistence.saveOrderState(
            pendingOrders.toList(),
            inProgressOrders.toList(),
            completedOrders.toList()
        )
    }

    // Helper Methods
    //Track the file path associated with an order.
    internal fun trackOrderFile(order: Order) {
        val filePath = OrderFileManager.findOrderFile(order)
        if (filePath != null) {
            orderToFileMap[order] = filePath
        }
    }

    //Check if an order already exists in any list for duplicates

    internal fun orderExists(order: Order): Boolean {
        return pendingOrders.contains(order) ||
                inProgressOrders.contains(order) ||
                completedOrders.contains(order)
    }

    //Result Data Classes
    data class OperationResult(
        val success: Boolean,
        val message: String
    ) {
        companion object {
            fun success(message: String) = OperationResult(true, message)
            fun failure(message: String) = OperationResult(false, message)
        }
    }

    data class BatchOperationResult(
        val successCount: Int,
        val failureCount: Int,
        val details: List<String>
    )

    data class DeletionResult(
        val success: Boolean,
        val fileDeleted: Boolean,
        val message: String
    )

    data class BatchDeletionResult(
        val successCount: Int,
        val filesDeletedCount: Int,
        val failures: List<String>
    )
}
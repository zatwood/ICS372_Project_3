package com.abc

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.scene.control.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("unused")
class OrderTrackingController {

    // Pending Orders Section
    @FXML private lateinit var pendingOrdersTable: TableView<Order>
    @FXML private lateinit var pendingTypeCol: TableColumn<Order, String>
    @FXML private lateinit var pendingSourceCol: TableColumn<Order, String>
    @FXML private lateinit var pendingDateCol: TableColumn<Order, String>
    @FXML private lateinit var pendingTotalCol: TableColumn<Order, String>
    @FXML private lateinit var startOrderBtn: Button
    @FXML private lateinit var deletePendingBtn: Button

    // In-Progress Orders Section
    @FXML private lateinit var inProgressOrdersTable: TableView<Order>
    @FXML private lateinit var inProgressTypeCol: TableColumn<Order, String>
    @FXML private lateinit var inProgressSourceCol: TableColumn<Order, String>
    @FXML private lateinit var inProgressDateCol: TableColumn<Order, String>
    @FXML private lateinit var inProgressTotalCol: TableColumn<Order, String>
    @FXML private lateinit var completeOrderBtn: Button
    @FXML private lateinit var undoStartBtn: Button
    @FXML private lateinit var deleteInProgressBtn: Button

    // Completed Orders Section
    @FXML private lateinit var completedOrdersTable: TableView<Order>
    @FXML private lateinit var completedTypeCol: TableColumn<Order, String>
    @FXML private lateinit var completedSourceCol: TableColumn<Order, String>
    @FXML private lateinit var completedDateCol: TableColumn<Order, String>
    @FXML private lateinit var completedTotalCol: TableColumn<Order, String>
    @FXML private lateinit var undoCompleteBtn: Button
    @FXML private lateinit var deleteCompletedBtn: Button

    // Order Details Section
    @FXML private lateinit var orderTypeLabel: Label
    @FXML private lateinit var orderSourceLabel: Label
    @FXML private lateinit var orderDateLabel: Label
    @FXML private lateinit var orderTotalLabel: Label
    @FXML private lateinit var itemsTable: TableView<Item>
    @FXML private lateinit var itemNameCol: TableColumn<Item, String>
    @FXML private lateinit var itemQuantityCol: TableColumn<Item, Int>
    @FXML private lateinit var itemPriceCol: TableColumn<Item, Double>

    // Auto-refresh controls
    @FXML private lateinit var autoRefreshLabel: Label
    @FXML private lateinit var toggleAutoRefreshBtn: Button

    // added and declared button here and in fxml
    @FXML private lateinit var addItemBtn : Button
    @FXML private lateinit var deleteItemBtn : Button

    // Data collections
    private val pendingOrders: ObservableList<Order> = FXCollections.observableArrayList()
    private val inProgressOrders: ObservableList<Order> = FXCollections.observableArrayList()
    private val completedOrders: ObservableList<Order> = FXCollections.observableArrayList()
    private val currentItems: ObservableList<Item> = FXCollections.observableArrayList()

    // Track which orders came from which files for deletion
    private val orderToFileMap = mutableMapOf<Order, String>()

    private var autoRefreshEnabled = true

    @FXML
    fun initialize() {
        setupOrderTables()
        setupItemsTable()
        setupEventHandlers()

        // Load saved state first
        val hasSavedState = loadSavedOrderState()

        // Only load orders from directory if there's no saved state
        // (i.e., first time running the app or after clearing state)
        // This prevents duplicate orders on restart since saved state
        // already contains previously loaded orders
        if (!hasSavedState) {
            loadOrders()
        }

        updateButtonStates()
        updateAutoRefreshLabel()

        // Start file watcher to monitor for NEW files added after startup
        // The watcher has duplicate detection to prevent re-adding existing orders
        startFileWatcher()
    }

    // Delegate table and item setup to helper
    private fun setupOrderTables() {
        OrderTableHelper.setupOrderTable(
            pendingOrdersTable, pendingTypeCol, pendingSourceCol,
            pendingDateCol, pendingTotalCol, pendingOrders, true
        )

        OrderTableHelper.setupOrderTable(
            inProgressOrdersTable, inProgressTypeCol, inProgressSourceCol,
            inProgressDateCol, inProgressTotalCol, inProgressOrders, true
        )

        OrderTableHelper.setupOrderTable(
            completedOrdersTable, completedTypeCol, completedSourceCol,
            completedDateCol, completedTotalCol, completedOrders, true
        )
    }

    private fun setupItemsTable() {
        OrderTableHelper.setupItemsTable(
            itemsTable,
            itemNameCol,
            itemQuantityCol,
            itemPriceCol,
            currentItems
        ) { updateOrderTotal() }
    }

    private fun setupEventHandlers() {
        OrderTableHelper.setupTableSelectionListeners(pendingOrdersTable) { order ->
            showOrderDetails(order)
            updateButtonStates()
        }
        OrderTableHelper.setupTableSelectionListeners(inProgressOrdersTable) { order ->
            showOrderDetails(order)
            updateButtonStates()
        }
        OrderTableHelper.setupTableSelectionListeners(completedOrdersTable) { order ->
            showOrderDetails(order)
            updateButtonStates()
        }
    }

    // -------------------- UI Handlers (unchanged) --------------------

    @FXML
    private fun handleBatchStart() {
        val selectedOrders = pendingOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select one or more orders to start")
            return
        }

        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch Start"
        confirmAlert.headerText = "Start ${selectedOrders.size} Orders"
        confirmAlert.contentText = "Are you sure you want to start ${selectedOrders.size} selected order(s)?"

        confirmAlert.showAndWait().ifPresent { response ->
            if (response == ButtonType.OK) {
                var movedCount = 0
                for (order in selectedOrders) {
                    order.status = Order.OrderStatus.IN_PROGRESS
                    pendingOrders.remove(order)
                    inProgressOrders.add(order)
                    movedCount++
                }

                clearOrderDetails()
                updateButtonStates()
                saveOrderState()

                showAlert(
                    Alert.AlertType.INFORMATION,
                    "Batch Start Complete",
                    "$movedCount order(s) have been moved to In-Progress"
                )
            }
        }
    }

    @FXML
    private fun handleBatchComplete() {
        val selectedOrders = inProgressOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select one or more orders to complete")
            return
        }

        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch Complete"
        confirmAlert.headerText = "Complete ${selectedOrders.size} Orders"
        confirmAlert.contentText = "Are you sure you want to complete ${selectedOrders.size} selected order(s)?"

        confirmAlert.showAndWait().ifPresent { response ->
            if (response == ButtonType.OK) {
                var movedCount = 0
                for (order in selectedOrders) {
                    order.status = Order.OrderStatus.COMPLETED
                    inProgressOrders.remove(order)
                    completedOrders.add(order)
                    movedCount++
                }

                clearOrderDetails()
                updateButtonStates()
                saveOrderState()

                showAlert(
                    Alert.AlertType.INFORMATION,
                    "Batch Complete",
                    "$movedCount order(s) have been marked as completed"
                )
            }
        }
    }

    @FXML
    private fun handleBatchDeletePending() {
        handleBatchDelete(
            pendingOrdersTable.selectionModel.selectedItems.toList(),
            pendingOrders,
            "Pending"
        )
    }

    @FXML
    private fun handleBatchDeleteInProgress() {
        handleBatchDelete(
            inProgressOrdersTable.selectionModel.selectedItems.toList(),
            inProgressOrders,
            "In-Progress"
        )
    }

    @FXML
    private fun handleBatchDeleteCompleted() {
        handleBatchDelete(
            completedOrdersTable.selectionModel.selectedItems.toList(),
            completedOrders,
            "Completed"
        )
    }

    private fun handleBatchDelete(
        selectedOrders: List<Order>,
        orderList: ObservableList<Order>,
        statusText: String
    ) {
        if (selectedOrders.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select one or more orders to delete")
            return
        }

        val totalAmount = selectedOrders.sumOf { it.calculateTotal() }

        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch Delete"
        confirmAlert.headerText = "Delete ${selectedOrders.size} $statusText Orders"
        confirmAlert.contentText = """
            Are you sure you want to delete ${selectedOrders.size} selected order(s)?
            
            Total Value: $${String.format("%.2f", totalAmount)}
            
            All orders will be saved to canceledOrders.json and source files will be removed if found.
        """.trimIndent()

        val yesButton = ButtonType("Yes, Delete All", ButtonBar.ButtonData.YES)
        val noButton = ButtonType("Cancel", ButtonBar.ButtonData.NO)
        confirmAlert.buttonTypes.setAll(yesButton, noButton)

        confirmAlert.showAndWait().ifPresent { response ->
            if (response == yesButton) {
                var deletedCount = 0
                var filesRemovedCount = 0

                for (order in selectedOrders) {
                    val fileRemoved = OrderFileManager.deleteOrderFile(order, orderToFileMap)
                    if (fileRemoved) filesRemovedCount++

                    orderList.remove(order)
                    orderToFileMap.remove(order)
                    deletedCount++
                }

                clearOrderDetails()
                updateButtonStates()
                saveOrderState()

                val message = """
                    $deletedCount $statusText order(s) deleted
                    $filesRemovedCount source file(s) removed
                    All orders saved to canceledOrders.json
                """.trimIndent()

                showAlert(Alert.AlertType.INFORMATION, "Batch Delete Complete", message)
            }
        }
    }

    // -------------------- File watcher / loading (delegated) --------------------

    private fun startFileWatcher() {
        OrderFileManager.startFileWatcher(
            "uploads",
            { autoRefreshEnabled },
            onNewOrders = { newOrders ->
                Platform.runLater {
                    if (autoRefreshEnabled) {
                        var addedCount = 0
                        for (order in newOrders) {
                            val alreadyExists = pendingOrders.contains(order) ||
                                    inProgressOrders.contains(order) ||
                                    completedOrders.contains(order)

                            if (!alreadyExists) {
                                order.status = Order.OrderStatus.PENDING
                                pendingOrders.add(order)
                                trackOrderFile(order)
                                addedCount++
                            }
                        }

                        if (addedCount > 0) {
                            saveOrderState()
                            showAlert(
                                Alert.AlertType.INFORMATION, "New Orders",
                                String.format("Automatically loaded %d new order(s)", addedCount)
                            )
                        }
                    }
                }
            },
            onReload = { refreshAllOrders() }
        )
    }

    private fun loadOrders() {
        val orders = OrderIn.readOrdersFromDirectory("uploads")

        if (orders.isEmpty()) {
            showAlert(
                Alert.AlertType.INFORMATION, "No Orders Found",
                "No order files were found in the expected directories. The system will watch for new files."
            )
            return
        }

        var newOrdersCount = 0
        for (order in orders) {
            val alreadyExists = pendingOrders.contains(order) ||
                    inProgressOrders.contains(order) ||
                    completedOrders.contains(order)

            if (!alreadyExists) {
                order.status = Order.OrderStatus.PENDING
                pendingOrders.add(order)
                trackOrderFile(order)
                newOrdersCount++
            }
        }

        saveOrderState()

        showAlert(
            Alert.AlertType.INFORMATION, "Orders Loaded",
            String.format("Successfully loaded %d new orders. Auto-refresh is enabled.", newOrdersCount)
        )
    }

    // -------------------- Item handlers --------------------

    @FXML
    private fun handleAddItem() {
        val newItem = Item(name = "New Item", quantity = 1, price = 0.0)
        currentItems.add(newItem)
        itemsTable.selectionModel.select(newItem)
        updateOrderTotal()
    }

    @FXML
    private fun handleDeleteItem() {
        val selectedItem = itemsTable.selectionModel.selectedItem
        if (selectedItem != null) {
            currentItems.remove(selectedItem)
            updateOrderTotal()
        }
    }

    private fun updateOrderTotal() {
        val selectedOrder = pendingOrdersTable.selectionModel.selectedItem ?:
            inProgressOrdersTable.selectionModel.selectedItem ?:
            completedOrdersTable.selectionModel.selectedItem

        if (selectedOrder != null) {
            selectedOrder.items = currentItems.toList()
            orderTotalLabel.text = "Total: ${formatTotal(selectedOrder)}"
            saveOrderState()
        }
    }

    private fun trackOrderFile(order: Order) {
        val filePath = OrderFileManager.findOrderFile(order)
        if (filePath != null) {
            orderToFileMap[order] = filePath
        }
    }

    // -------------------- Single order actions --------------------

    @FXML
    private fun handleStartOrder() {
        val selectedOrders = pendingOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) return

        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
            selected.status = Order.OrderStatus.IN_PROGRESS
            pendingOrders.remove(selected)
            inProgressOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(Alert.AlertType.INFORMATION, "Order Started", "Order has been moved to In-Progress")
            return
        }

        // fallback to batch flow (shouldn't occur because separate batch handler exists)
        handleBatchStart()
    }

    @FXML
    private fun handleCompleteOrder() {
        val selectedOrders = inProgressOrdersTable.selectionModel.selectedItems.toList()
        if (selectedOrders.isEmpty()) return
        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
            selected.status = Order.OrderStatus.COMPLETED
            inProgressOrders.remove(selected)
            completedOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(Alert.AlertType.INFORMATION, "Order Completed", "Order has been marked as completed")
            return
        }
        handleBatchComplete()
    }

    @FXML
    private fun handleUndoStart() {
        val selectedOrders = inProgressOrdersTable.selectionModel.selectedItems.toList()
        if (selectedOrders.isEmpty()) return
        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
            selected.status = Order.OrderStatus.PENDING
            inProgressOrders.remove(selected)
            pendingOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(Alert.AlertType.INFORMATION, "Start Order Undone", "Order has been moved back to pending")
            return
        }
        // reuse existing batch logic
        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch Undo Start"
        confirmAlert.headerText = "Undo Start for ${selectedOrders.size} Orders"
        confirmAlert.contentText = "Are you sure you want to move ${selectedOrders.size} selected order(s) back to pending?"

        confirmAlert.showAndWait().ifPresent { response ->
            if (response == ButtonType.OK) {
                var movedCount = 0
                for (order in selectedOrders) {
                    order.status = Order.OrderStatus.PENDING
                    inProgressOrders.remove(order)
                    pendingOrders.add(order)
                    movedCount++
                }
                clearOrderDetails()
                updateButtonStates()
                saveOrderState()
                showAlert(Alert.AlertType.INFORMATION, "Batch Undo Start Complete", "$movedCount order(s) have been moved back to pending")
            }
        }
    }

    @FXML
    private fun handleUndoComplete() {
        val selectedOrders = completedOrdersTable.selectionModel.selectedItems.toList()
        if (selectedOrders.isEmpty()) return
        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
            selected.status = Order.OrderStatus.IN_PROGRESS
            completedOrders.remove(selected)
            inProgressOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(Alert.AlertType.INFORMATION, "Complete Order Undone", "Order has been moved back to in-progress")
            return
        }
        // reuse batch flow
        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch Undo Complete"
        confirmAlert.headerText = "Undo Complete for ${selectedOrders.size} Orders"
        confirmAlert.contentText = "Are you sure you want to move ${selectedOrders.size} selected order(s) back to in-progress?"

        confirmAlert.showAndWait().ifPresent { response ->
            if (response == ButtonType.OK) {
                var movedCount = 0
                for (order in selectedOrders) {
                    order.status = Order.OrderStatus.IN_PROGRESS
                    completedOrders.remove(order)
                    inProgressOrders.add(order)
                    movedCount++
                }
                clearOrderDetails()
                updateButtonStates()
                saveOrderState()
                showAlert(Alert.AlertType.INFORMATION, "Batch Undo Complete", "$movedCount order(s) have been moved back to in-progress")
            }
        }
    }

    @FXML
    private fun handleDeletePendingOrder() {
        handleDeleteOrder(pendingOrdersTable.selectionModel.selectedItems.toList(), pendingOrders, "Pending")
    }

    @FXML
    private fun handleDeleteInProgressOrder() {
        handleDeleteOrder(inProgressOrdersTable.selectionModel.selectedItems.toList(), inProgressOrders, "In-Progress")
    }

    @FXML
    private fun handleDeleteCompletedOrder() {
        handleDeleteOrder(completedOrdersTable.selectionModel.selectedItems.toList(), completedOrders, "Completed")
    }

    private fun handleDeleteOrder(selectedOrders: List<Order>, orderList: ObservableList<Order>, statusText: String) {
        if (selectedOrders.isEmpty()) return

        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
            val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
            confirmAlert.title = "Confirm Delete"
            confirmAlert.headerText = "Delete $statusText Order"
            confirmAlert.contentText = """
            Are you sure you want to delete this order?

            Type: ${selected.getTypeOrDefault()}
            Source: ${selected.source ?: "Unknown"}
            Total: ${String.format("%.2f", selected.calculateTotal())}

            The order will be saved to canceledOrders.json and the source file will be removed if found.
        """.trimIndent()

            val yesButton = ButtonType("Yes", ButtonBar.ButtonData.YES)
            val noButton = ButtonType("No", ButtonBar.ButtonData.NO)
            confirmAlert.buttonTypes.setAll(yesButton, noButton)

            confirmAlert.showAndWait().ifPresent { response ->
                if (response == yesButton) {
                    val orderProcessed = OrderFileManager.deleteOrderFile(selected, orderToFileMap)
                    orderList.remove(selected)
                    orderToFileMap.remove(selected)

                    clearOrderDetails()
                    updateButtonStates()
                    saveOrderState()

                    var message = "$statusText order has been deleted and saved to canceledOrders.json"
                    message += if (orderProcessed) " (source file removed)" else " (source file could not be found)"

                    showAlert(Alert.AlertType.INFORMATION, "Order Deleted", message)
                }
            }
            return
        }

        // batch delete handled earlier
        handleBatchDelete(selectedOrders, orderList, statusText)
    }

    // -------------------- Refresh / Auto-refresh --------------------

    @FXML
    private fun handleRefresh() {
        val newOrders = OrderIn.readOrdersFromDirectory("uploads")

        var newOrdersCount = 0
        for (order in newOrders) {
            val alreadyExists = pendingOrders.contains(order) ||
                    inProgressOrders.contains(order) ||
                    completedOrders.contains(order)

            if (!alreadyExists) {
                order.status = Order.OrderStatus.PENDING
                pendingOrders.add(order)
                trackOrderFile(order)
                newOrdersCount++
            }
        }

        if (newOrdersCount > 0) saveOrderState()

        updateButtonStates()
        showAlert(
            Alert.AlertType.INFORMATION,
            "Refresh Complete",
            if (newOrdersCount > 0) "Found $newOrdersCount new order(s)" else "No new orders found"
        )
    }

    @FXML
    private fun handleToggleAutoRefresh() {
        autoRefreshEnabled = !autoRefreshEnabled
        updateAutoRefreshLabel()
        showAlert(
            Alert.AlertType.INFORMATION, "Auto Refresh",
            if (autoRefreshEnabled) "Auto refresh enabled - new files will be loaded automatically"
            else "Auto refresh disabled - use manual refresh"
        )
    }

    private fun refreshAllOrders() {
        val allOrders = OrderIn.readOrdersFromDirectory("uploads")
        val existingOrders = mutableListOf<Order>()
        existingOrders.addAll(pendingOrders)
        existingOrders.addAll(inProgressOrders)
        existingOrders.addAll(completedOrders)

        for (order in allOrders) {
            if (!existingOrders.contains(order)) {
                order.status = Order.OrderStatus.PENDING
                pendingOrders.add(order)
            }
        }

        saveOrderState()
        clearOrderDetails()
        updateButtonStates()
    }

    private fun updateAutoRefreshLabel() {
        autoRefreshLabel.text = "Auto-refresh: ${if (autoRefreshEnabled) "ON" else "OFF"}"
        autoRefreshLabel.style = if (autoRefreshEnabled) {
            "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
        } else {
            "-fx-text-fill: #e74c3c; -fx-font-weight: bold;"
        }

        toggleAutoRefreshBtn.text = if (autoRefreshEnabled) "Disable Auto-Refresh" else "Enable Auto-Refresh"
        toggleAutoRefreshBtn.style = if (autoRefreshEnabled) {
            "-fx-background-color: #e74c3c; -fx-text-fill: white;"
        } else {
            "-fx-background-color: #27ae60; -fx-text-fill: white;"
        }
    }

    private fun showOrderDetails(order: Order?) {
        if (order == null) {
            clearOrderDetails()
            return
        }

        orderTypeLabel.text = "Type: ${order.getTypeOrDefault()}"
        orderSourceLabel.text = "Source: ${order.source ?: "Unknown"}"
        orderDateLabel.text = "Date: ${formatDate(order.order_date)}"
        orderTotalLabel.text = "Total: ${formatTotal(order)}"
        currentItems.setAll(order.getItemsOrEmpty())
    }

    private fun clearOrderDetails() {
        orderTypeLabel.text = "Type: "
        orderSourceLabel.text = "Source: "
        orderDateLabel.text = "Date: "
        orderTotalLabel.text = "Total: "
        currentItems.clear()
    }

    private fun updateButtonStates() {
        val pendingSelected = pendingOrdersTable.selectionModel.selectedItem
        startOrderBtn.isDisable = pendingSelected == null
        deletePendingBtn.isDisable = pendingSelected == null

        val inProgressSelected = inProgressOrdersTable.selectionModel.selectedItem
        completeOrderBtn.isDisable = inProgressSelected == null
        undoStartBtn.isDisable = inProgressSelected == null
        deleteInProgressBtn.isDisable = inProgressSelected == null

        val completedSelected = completedOrdersTable.selectionModel.selectedItem
        undoCompleteBtn.isDisable = completedSelected == null
        deleteCompletedBtn.isDisable = completedSelected == null
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(timestamp)
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            dateTime.format(DATE_FORMATTER)
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    private fun formatTotal(order: Order?): String {
        if (order == null) return "$0.00"
        return String.format("$%.2f", order.calculateTotal())
    }

    private fun loadSavedOrderState(): Boolean {
        val savedState = OrderPersistence.loadOrderState()
        if (savedState != null) {
            pendingOrders.addAll(savedState.pendingOrders)
            inProgressOrders.addAll(savedState.inProgressOrders)
            completedOrders.addAll(savedState.completedOrders)

            val totalOrders = savedState.pendingOrders.size +
                    savedState.inProgressOrders.size +
                    savedState.completedOrders.size

            println("Loaded: ${savedState.pendingOrders.size} pending, ${savedState.inProgressOrders.size} in-progress, ${savedState.completedOrders.size} completed orders")

            return totalOrders > 0
        }
        return false
    }

    private fun saveOrderState() {
        val success = OrderPersistence.saveOrderState(
            pendingOrders.toList(),
            inProgressOrders.toList(),
            completedOrders.toList()
        )

        if (!success) {
            System.err.println("Warning: Failed to save order state")
        }
    }

    private fun showAlert(alertType: Alert.AlertType, title: String, message: String) {
        val alert = Alert(alertType)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }


    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
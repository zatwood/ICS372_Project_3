package com.abc

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.beans.property.SimpleStringProperty
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    private fun setupOrderTables() {
        // Configure pending orders table
        setupOrderTable(
            pendingOrdersTable, pendingTypeCol, pendingSourceCol,
            pendingDateCol, pendingTotalCol, pendingOrders
        )

        // Configure in-progress orders table
        setupOrderTable(
            inProgressOrdersTable, inProgressTypeCol, inProgressSourceCol,
            inProgressDateCol, inProgressTotalCol, inProgressOrders
        )

        // Configure completed orders table
        setupOrderTable(
            completedOrdersTable, completedTypeCol, completedSourceCol,
            completedDateCol, completedTotalCol, completedOrders
        )
    }

    /**
     * Common setup method for order tables
     */
    private fun setupOrderTable(
        table: TableView<Order>,
        typeCol: TableColumn<Order, String>,
        sourceCol: TableColumn<Order, String>,
        dateCol: TableColumn<Order, String>,
        totalCol: TableColumn<Order, String>,
        orders: ObservableList<Order>
    ) {
        typeCol.setCellValueFactory { data -> SimpleStringProperty(data.value.getTypeOrDefault()) }
        typeCol.setCellFactory { createTypeCell() }
        sourceCol.setCellValueFactory { data ->
            SimpleStringProperty(data.value.source ?: "Unknown")
        }
        dateCol.setCellValueFactory { data -> SimpleStringProperty(formatDate(data.value.order_date)) }
        totalCol.setCellValueFactory { data -> SimpleStringProperty(formatTotal(data.value)) }
        table.items = orders
    }

    private fun setupItemsTable() {
        itemNameCol.cellValueFactory = PropertyValueFactory("name")
        itemQuantityCol.cellValueFactory = PropertyValueFactory("quantity")
        itemPriceCol.cellValueFactory = PropertyValueFactory("price")

        // Format price column
        itemPriceCol.setCellFactory {
            object : TableCell<Item, Double>() {
                override fun updateItem(price: Double?, empty: Boolean) {
                    super.updateItem(price, empty)
                    text = if (empty || price == null) {
                        null
                    } else {
                        String.format("$%.2f", price)
                    }
                }
            }
        }

        itemsTable.items = currentItems
    }

    private fun setupEventHandlers() {
        // Selection listeners for all tables
        setupTableSelectionListeners(pendingOrdersTable)
        setupTableSelectionListeners(inProgressOrdersTable)
        setupTableSelectionListeners(completedOrdersTable)
    }

    /**
     * Common setup for table selection listeners
     */
    private fun setupTableSelectionListeners(table: TableView<Order>) {
        table.selectionModel.selectedItemProperty().addListener { _, _, newVal ->
            showOrderDetails(newVal)
            updateButtonStates()
        }
    }

    private fun startFileWatcher() {
        // Register as listener for order updates
        OrderIn.addOrderUpdateListener(object : OrderIn.OrderUpdateListener {
            override fun onOrdersUpdated(newOrders: List<Order>) {
                Platform.runLater {
                    if (autoRefreshEnabled) {
                        // Add new orders to pending, avoiding duplicates
                        var addedCount = 0
                        for (order in newOrders) {
                            val alreadyExists = pendingOrders.contains(order) ||
                                    inProgressOrders.contains(order) ||
                                    completedOrders.contains(order)

                            if (!alreadyExists) {
                                order.status = Order.OrderStatus.PENDING
                                pendingOrders.add(order)
                                // Track file association for deletion
                                trackOrderFile(order)
                                addedCount++
                            }
                        }

                        // Save state after adding new orders
                        if (addedCount > 0) {
                            saveOrderState()
                        }

                        if (addedCount > 0) {
                            showAlert(
                                Alert.AlertType.INFORMATION, "New Orders",
                                String.format("Automatically loaded %d new order(s)", addedCount)
                            )
                        }
                    }
                }
            }

            override fun onOrdersReloaded(allOrders: List<Order>) {
                Platform.runLater {
                    refreshAllOrders()
                }
            }
        })

        // Start the file watcher (choose one method)
        val watchDirectory = "uploads"

        // Method 1: Use efficient file system watcher (recommended)
        try {
            OrderIn.startFileWatcher(watchDirectory)
            println("File system watcher started successfully")
        } catch (e: Exception) {
            println("File system watcher not available, using polling: ${e.message}")
            // Method 2: Fallback to polling watcher
            OrderIn.startPollingWatcher(watchDirectory)
        }
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

        // Only add orders that aren't already in any of the lists
        var newOrdersCount = 0
        for (order in orders) {
            val alreadyExists = pendingOrders.contains(order) ||
                    inProgressOrders.contains(order) ||
                    completedOrders.contains(order)

            if (!alreadyExists) {
                order.status = Order.OrderStatus.PENDING
                pendingOrders.add(order)
                // Track file association for deletion
                trackOrderFile(order)
                newOrdersCount++
            }
        }

        // Save state after loading new orders
        saveOrderState()

        showAlert(
            Alert.AlertType.INFORMATION, "Orders Loaded",
            String.format("Successfully loaded %d new orders. Auto-refresh is enabled.", newOrdersCount)
        )
    }

    /**
     * Track which file an order came from for deletion purposes
     */
    private fun trackOrderFile(order: Order) {
        val filePath = findOrderFile(order)
        if (filePath != null) {
            orderToFileMap[order] = filePath
        }
    }

    @FXML
    private fun handleStartOrder() {
        val selected = pendingOrdersTable.selectionModel.selectedItem
        if (selected != null) {
            selected.status = Order.OrderStatus.IN_PROGRESS
            pendingOrders.remove(selected)
            inProgressOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(
                Alert.AlertType.INFORMATION, "Order Started",
                "Order has been moved to In-Progress"
            )
        }
    }

    @FXML
    private fun handleCompleteOrder() {
        val selected = inProgressOrdersTable.selectionModel.selectedItem
        if (selected != null) {
            selected.status = Order.OrderStatus.COMPLETED
            inProgressOrders.remove(selected)
            completedOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(
                Alert.AlertType.INFORMATION, "Order Completed",
                "Order has been marked as completed"
            )
        }
    }

    @FXML
    private fun handleUndoStart() {
        val selected = inProgressOrdersTable.selectionModel.selectedItem
        if (selected != null) {
            selected.status = Order.OrderStatus.PENDING
            inProgressOrders.remove(selected)
            pendingOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(
                Alert.AlertType.INFORMATION, "Start Order Undone",
                "Order has been moved back to pending"
            )
        }
    }

    @FXML
    private fun handleUndoComplete() {
        val selected = completedOrdersTable.selectionModel.selectedItem
        if (selected != null) {
            selected.status = Order.OrderStatus.IN_PROGRESS
            completedOrders.remove(selected)
            inProgressOrders.add(selected)
            showOrderDetails(selected)
            updateButtonStates()
            saveOrderState()
            showAlert(
                Alert.AlertType.INFORMATION, "Complete Order Undone",
                "Order has been moved back to in-progress"
            )
        }
    }

    @FXML
    private fun handleDeletePendingOrder() {
        handleDeleteOrder(
            pendingOrdersTable.selectionModel.selectedItem,
            pendingOrders, "Pending"
        )
    }

    @FXML
    private fun handleDeleteInProgressOrder() {
        handleDeleteOrder(
            inProgressOrdersTable.selectionModel.selectedItem,
            inProgressOrders, "In-Progress"
        )
    }

    @FXML
    private fun handleDeleteCompletedOrder() {
        handleDeleteOrder(
            completedOrdersTable.selectionModel.selectedItem,
            completedOrders, "Completed"
        )
    }

    /**
     * Handler for deleting orders from any status list
     */
    private fun handleDeleteOrder(selected: Order?, orderList: ObservableList<Order>, statusText: String) {
        if (selected != null) {
            val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
            confirmAlert.title = "Confirm Delete"
            confirmAlert.headerText = "Delete $statusText Order"
            confirmAlert.contentText = """
                Are you sure you want to delete this order?

                Type: ${selected.getTypeOrDefault()}
                Source: ${selected.source ?: "Unknown"}
                Total: $${String.format("%.2f", selected.calculateTotal())}

                The order will be saved to canceledOrders.json and the source file will be removed if found.
            """.trimIndent()

            val yesButton = ButtonType("Yes", ButtonBar.ButtonData.YES)
            val noButton = ButtonType("No", ButtonBar.ButtonData.NO)
            confirmAlert.buttonTypes.setAll(yesButton, noButton)

            confirmAlert.showAndWait().ifPresent { response ->
                if (response == yesButton) {
                    // Save to canceled orders and try to delete the source file
                    val orderProcessed = deleteOrderFile(selected)

                    // Remove order from list
                    orderList.remove(selected)

                    // Remove from file tracking map
                    orderToFileMap.remove(selected)

                    clearOrderDetails()
                    updateButtonStates()
                    saveOrderState()

                    var message = "$statusText order has been deleted and saved to canceledOrders.json"
                    message += if (orderProcessed) {
                        " (source file removed)"
                    } else {
                        " (source file could not be found)"
                    }

                    showAlert(Alert.AlertType.INFORMATION, "Order Deleted", message)
                }
            }
        }
    }

    /**
     * Save the order to canceled orders and optionally delete the source file
     */
    private fun deleteOrderFile(order: Order): Boolean {
        // Save the order to canceled orders
        val savedToCanceled = OrderPersistence.saveCanceledOrder(order)

        var filePath = orderToFileMap[order]
        if (filePath == null) {
            // Try to find the file based on order source
            filePath = findOrderFile(order)
        }

        var fileDeleted = false
        if (filePath != null) {
            try {
                val path = Paths.get(filePath)
                if (Files.exists(path)) {
                    Files.delete(path)
                    println("Deleted source file: $path")
                    fileDeleted = true
                } else {
                    println("Source file not found for deletion: $path")
                }
            } catch (e: IOException) {
                System.err.println("Error deleting source file $filePath: ${e.message}")
            }
        }

        return savedToCanceled || fileDeleted
    }

    /**
     * Try to find the source file for an order based on its properties
     */
    private fun findOrderFile(order: Order): String? {
        try {
            val uploadsDir = Paths.get("uploads")
            if (Files.exists(uploadsDir)) {
                Files.newDirectoryStream(uploadsDir, "*.{json,xml}").use { stream ->
                    for (filePath in stream) {
                        val fileName = filePath.fileName.toString().lowercase()

                        // Check if filename contains order source
                        if (order.source != null &&
                            fileName.contains(order.source!!.lowercase().replace(" ", ""))
                        ) {
                            return filePath.toString()
                        }

                        // Additional heuristics for finding the right file
                        if (fileName.contains("order") || fileName.contains("restaurant")) {
                            // For simplicity, return the first matching file
                            // In a real system, you might want more sophisticated matching
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

    @FXML
    private fun handleRefresh() {
        // Check for new orders without re-loading already processed files
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

        if (newOrdersCount > 0) {
            saveOrderState()
        }

        updateButtonStates()
        showAlert(
            Alert.AlertType.INFORMATION,
            "Refresh Complete",
            if (newOrdersCount > 0) "Found $newOrdersCount new order(s)"
            else "No new orders found"
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

        // Create a combined list of existing orders to check against
        val existingOrders = mutableListOf<Order>()
        existingOrders.addAll(pendingOrders)
        existingOrders.addAll(inProgressOrders)
        existingOrders.addAll(completedOrders)

        // Only add orders that aren't already in any list
        for (order in allOrders) {
            if (!existingOrders.contains(order)) {
                order.status = Order.OrderStatus.PENDING
                pendingOrders.add(order)
            }
        }

        // Save state after refresh
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
        // Pending orders buttons
        val pendingSelected = pendingOrdersTable.selectionModel.selectedItem
        startOrderBtn.isDisable = pendingSelected == null
        deletePendingBtn.isDisable = pendingSelected == null

        // In-progress orders buttons
        val inProgressSelected = inProgressOrdersTable.selectionModel.selectedItem
        completeOrderBtn.isDisable = inProgressSelected == null
        undoStartBtn.isDisable = inProgressSelected == null
        deleteInProgressBtn.isDisable = inProgressSelected == null

        // Completed orders buttons
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
            // Load saved orders into the observable lists
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

    private fun createTypeCell(): TableCell<Order, String> {
        return object : TableCell<Order, String>() {
            override fun updateItem(type: String?, empty: Boolean) {
                super.updateItem(type, empty)
                if (empty || type == null) {
                    text = null
                    style = ""
                } else {
                    when (type.lowercase()) {
                        "pick-up", "pickup" -> {
                            text = "📦 Pick-Up"
                            style = "-fx-text-fill: blue; -fx-font-weight: bold;"
                        }
                        "to-go", "togo" -> {
                            text = "🍱 To-Go"
                            style = "-fx-text-fill: purple; -fx-font-weight: bold;"
                        }
                        "delivery" -> {
                            text = "🚚 Delivery"
                            style = "-fx-text-fill: orange; -fx-font-weight: bold;"
                        }
                        else -> {
                            text = type
                            style = "-fx-text-fill: orange; -fx-font-weight: bold;"
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}

package com.abc

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.cell.TextFieldTableCell
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

    //added and declared button here and in fxml
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

    private fun setupOrderTables() {
        // Configure pending orders table with multiple selection
        setupOrderTable(
            pendingOrdersTable, pendingTypeCol, pendingSourceCol,
            pendingDateCol, pendingTotalCol, pendingOrders, true
        )

        // Configure in-progress orders table with multiple selection
        setupOrderTable(
            inProgressOrdersTable, inProgressTypeCol, inProgressSourceCol,
            inProgressDateCol, inProgressTotalCol, inProgressOrders, true
        )

        // Configure completed orders table with multiple selection
        setupOrderTable(
            completedOrdersTable, completedTypeCol, completedSourceCol,
            completedDateCol, completedTotalCol, completedOrders, true
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
        orders: ObservableList<Order>,
        multipleSelection: Boolean = false
    ) {
        // Enable multiple selection mode
        if (multipleSelection) {
            table.selectionModel.selectionMode = SelectionMode.MULTIPLE
        }

        // Add checkbox column for batch selection
        val checkBoxCol = TableColumn<Order, Boolean>("")
        checkBoxCol.minWidth = 40.0
        checkBoxCol.maxWidth = 40.0
        checkBoxCol.isResizable = false
        checkBoxCol.isSortable = false

        checkBoxCol.setCellFactory {
            object : TableCell<Order, Boolean>() {
                private val checkBox = CheckBox()

                init {
                    checkBox.setOnAction {
                        val currentIndex = index
                        if (currentIndex >= 0 && currentIndex < table.items.size) {
                            if (checkBox.isSelected) {
                                table.selectionModel.select(currentIndex)
                            } else {
                                table.selectionModel.clearSelection(currentIndex)
                            }
                        }
                    }
                }

                override fun updateItem(item: Boolean?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || index < 0 || index >= table.items.size) {
                        graphic = null
                        checkBox.isSelected = false
                    } else {
                        checkBox.isSelected = table.selectionModel.isSelected(index)
                        graphic = checkBox
                    }
                }
            }
        }

        // Listen to selection changes to update checkboxes
        table.selectionModel.selectedIndices.addListener { _: javafx.collections.ListChangeListener.Change<out Int> ->
            table.refresh()
        }

        // Add the checkbox column as the first column
        table.columns.add(0, checkBoxCol)

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
        itemsTable.isEditable = true

        itemNameCol.cellValueFactory = PropertyValueFactory("name")
        itemNameCol.cellFactory = TextFieldTableCell.forTableColumn()
        itemNameCol.setOnEditCommit { event ->
            val item = event.rowValue
            item.name = event.newValue
            itemsTable.refresh()
            updateOrderTotal()
        }


        itemQuantityCol.cellValueFactory = PropertyValueFactory("quantity")
        itemQuantityCol.cellFactory = TextFieldTableCell.forTableColumn(javafx.util.converter.IntegerStringConverter())
        itemQuantityCol.setOnEditCommit { event ->
            val item = event.rowValue
            item.quantity = event.newValue
            itemsTable.refresh()
            updateOrderTotal()
        }

        itemPriceCol.cellValueFactory = PropertyValueFactory("price")
        itemPriceCol.cellFactory= TextFieldTableCell.forTableColumn(javafx.util.converter.DoubleStringConverter())
        itemPriceCol.setOnEditCommit { event ->
            val item = event.rowValue
            item.price = event.newValue
            itemsTable.refresh()
            updateOrderTotal()

        }

        /*// made new method updateOrderTotal below to handle update on side panel table
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
        */

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

    // BATCH OPERATIONS - COMPLETE MULTIPLE ORDERS
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

    // BATCH OPERATIONS - DELETE MULTIPLE PENDING ORDERS
    @FXML
    private fun handleBatchDeletePending() {
        handleBatchDelete(
            pendingOrdersTable.selectionModel.selectedItems.toList(),
            pendingOrders,
            "Pending"
        )
    }

    // BATCH OPERATIONS - DELETE MULTIPLE IN-PROGRESS ORDERS
    @FXML
    private fun handleBatchDeleteInProgress() {
        handleBatchDelete(
            inProgressOrdersTable.selectionModel.selectedItems.toList(),
            inProgressOrders,
            "In-Progress"
        )
    }

    // BATCH OPERATIONS - DELETE MULTIPLE COMPLETED ORDERS
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
                    val fileRemoved = deleteOrderFile(order)
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
     * handles for deleting and adding items
     */

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

    //recalculating the order total after adding or deleting items
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
        val selectedOrders = pendingOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            return
        }

        // Single selection
        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
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
            return
        }

        // Batch selection with confirmation
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
    private fun handleCompleteOrder() {
        val selectedOrders = inProgressOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            return
        }

        // Single selection - no confirmation
        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
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
            return
        }

        // Batch selection - with confirmation
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
    private fun handleUndoStart() {
        val selectedOrders = inProgressOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            return
        }

        // Handle single-selection differently
        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
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
            return
        }

        // Handle batch selection with confirmation
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

                showAlert(
                    Alert.AlertType.INFORMATION,
                    "Batch Undo Start Complete",
                    "$movedCount order(s) have been moved back to pending"
                )
            }
        }
    }

    @FXML
    private fun handleUndoComplete() {
        val selectedOrders = completedOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            return
        }

        // Handle single selection differently
        if (selectedOrders.size == 1) {
            val selected = selectedOrders[0]
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
            return
        }

        // Handle batch selection with confirmation
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

                showAlert(
                    Alert.AlertType.INFORMATION,
                    "Batch Undo Complete",
                    "$movedCount order(s) have been moved back to in-progress"
                )
            }
        }
    }

    @FXML
    private fun handleDeletePendingOrder() {
        handleDeleteOrder(
            pendingOrdersTable.selectionModel.selectedItems.toList(),
            pendingOrders,
            "Pending"
        )
    }

    @FXML
    private fun handleDeleteInProgressOrder() {
        handleDeleteOrder(
            inProgressOrdersTable.selectionModel.selectedItems.toList(),
            inProgressOrders,
            "In-Progress"
        )
    }

    @FXML
    private fun handleDeleteCompletedOrder() {
        handleDeleteOrder(
            completedOrdersTable.selectionModel.selectedItems.toList(),
            completedOrders,
            "Completed"
        )
    }

    /**
     * Handler for deleting orders from any status list (handles both single and batch)
     */
    private fun handleDeleteOrder(selectedOrders: List<Order>, orderList: ObservableList<Order>, statusText: String) {
        if (selectedOrders.isEmpty()) {
            return
        }

        // Single order deletion
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
                    val orderProcessed = deleteOrderFile(selected)
                    orderList.remove(selected)
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
            return
        }

        // Batch deletion
        val totalAmount = selectedOrders.sumOf { it.calculateTotal() }
        // Confirmation dialog
        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch Delete"
        confirmAlert.headerText = "Delete ${selectedOrders.size} $statusText Orders"
        confirmAlert.contentText = """
        Are you sure you want to delete ${selectedOrders.size} selected order(s)?
        
        Total Value: ${String.format("%.2f", totalAmount)}
        
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
                    val fileRemoved = deleteOrderFile(order)
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

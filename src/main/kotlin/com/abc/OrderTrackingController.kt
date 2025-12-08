package com.abc

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.scene.control.*

@Suppress("unused")
class OrderTrackingController {

    //FXML Components
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

    // Item management buttons
    @FXML private lateinit var addItemBtn: Button
    @FXML private lateinit var deleteItemBtn: Button

    // ============== Business Logic & Helpers ==============
    private val orderManager = OrderManager()
    private val dialogHelper = OrderDialogHelper()
    private val currentItems: ObservableList<Item> = FXCollections.observableArrayList()
    private var autoRefreshEnabled = true

    //Initialization
    @FXML
    fun initialize() {
        setupOrderTables()
        setupItemsTable()
        setupEventHandlers()

        // Load saved state first, or load from directory
        val savedState = OrderPersistence.loadOrderState()
        if (savedState != null) {
            orderManager.pendingOrders.addAll(savedState.pendingOrders)
            orderManager.inProgressOrders.addAll(savedState.inProgressOrders)
            orderManager.completedOrders.addAll(savedState.completedOrders)

            // Track files for all loaded orders
            (savedState.pendingOrders + savedState.inProgressOrders + savedState.completedOrders)
                .forEach { orderManager.trackOrderFile(it) }
        } else {
            loadOrdersFromFiles(isInitialLoad = true)
        }

        updateButtonStates()
        updateAutoRefreshLabel()
        startFileWatcher()
    }

    private fun setupOrderTables() {
        OrderTableHelper.setupOrderTable(
            pendingOrdersTable, pendingTypeCol, pendingSourceCol,
            pendingDateCol, pendingTotalCol, orderManager.pendingOrders, true
        )

        OrderTableHelper.setupOrderTable(
            inProgressOrdersTable, inProgressTypeCol, inProgressSourceCol,
            inProgressDateCol, inProgressTotalCol, orderManager.inProgressOrders, true
        )

        OrderTableHelper.setupOrderTable(
            completedOrdersTable, completedTypeCol, completedSourceCol,
            completedDateCol, completedTotalCol, orderManager.completedOrders, true
        )
    }

    private fun setupItemsTable() {
        OrderTableHelper.setupItemsTable(
            itemsTable, itemNameCol, itemQuantityCol, itemPriceCol, currentItems
        ) { updateOrderTotal() }
    }

    private fun setupEventHandlers() {
        val updateDetails: (Order?) -> Unit = { order ->
            showOrderDetails(order)
            updateButtonStates()
        }

        OrderTableHelper.setupTableSelectionListeners(pendingOrdersTable, updateDetails)
        OrderTableHelper.setupTableSelectionListeners(inProgressOrdersTable, updateDetails)
        OrderTableHelper.setupTableSelectionListeners(completedOrdersTable, updateDetails)
    }

    //Order Action Handlers
    @FXML
    private fun handleStartOrder() {
        val selectedOrders = pendingOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            dialogHelper.showWarning("No Selection", "Please select one or more orders to start")
            return
        }

        if (selectedOrders.size == 1) {
            val result = orderManager.startOrder(selectedOrders[0])
            showOrderDetails(selectedOrders[0])
            updateButtonStates()
            dialogHelper.showInfo("Start Order", result.message)
        } else if (dialogHelper.confirmBatchAction("Start", selectedOrders.size)) {
            val result = orderManager.startOrders(selectedOrders)
            clearOrderDetails()
            updateButtonStates()
            dialogHelper.showBatchStartResult(result.successCount)
        }
    }

    @FXML
    private fun handleBatchStart() = handleStartOrder()

    @FXML
    private fun handleCompleteOrder() {
        val selectedOrders = inProgressOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            dialogHelper.showWarning("No Selection", "Please select one or more orders to complete")
            return
        }

        if (selectedOrders.size == 1) {
            val result = orderManager.completeOrder(selectedOrders[0])
            showOrderDetails(selectedOrders[0])
            updateButtonStates()
            dialogHelper.showInfo("Complete Order", result.message)
        } else if (dialogHelper.confirmBatchAction("Complete", selectedOrders.size)) {
            val result = orderManager.completeOrders(selectedOrders)
            clearOrderDetails()
            updateButtonStates()
            dialogHelper.showBatchCompleteResult(result.successCount)
        }
    }

    @FXML
    private fun handleBatchComplete() = handleCompleteOrder()

    @FXML
    private fun handleUndoStart() {
        val selectedOrders = inProgressOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            dialogHelper.showWarning("No Selection", "Please select one or more orders to move back")
            return
        }

        if (selectedOrders.size == 1) {
            val result = orderManager.undoStart(selectedOrders[0])
            showOrderDetails(selectedOrders[0])
            updateButtonStates()
            dialogHelper.showInfo("Undo Start", result.message)
        } else if (dialogHelper.confirmBatchAction("move back to pending", selectedOrders.size)) {
            val result = orderManager.undoStartBatch(selectedOrders)
            clearOrderDetails()
            updateButtonStates()
            dialogHelper.showBatchUndoResult(result.successCount, "pending")
        }
    }

    @FXML
    private fun handleUndoComplete() {
        val selectedOrders = completedOrdersTable.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            dialogHelper.showWarning("No Selection", "Please select one or more orders to move back")
            return
        }

        if (selectedOrders.size == 1) {
            val result = orderManager.undoComplete(selectedOrders[0])
            showOrderDetails(selectedOrders[0])
            updateButtonStates()
            dialogHelper.showInfo("Undo Complete", result.message)
        } else if (dialogHelper.confirmBatchAction("move back to in-progress", selectedOrders.size)) {
            val result = orderManager.undoCompleteBatch(selectedOrders)
            clearOrderDetails()
            updateButtonStates()
            dialogHelper.showBatchUndoResult(result.successCount, "in-progress")
        }
    }

    //Delete Handlers
    @FXML
    private fun handleDeletePendingOrder() = handleDeleteOrders(pendingOrdersTable, "Pending")

    @FXML
    private fun handleBatchDeletePending() = handleDeletePendingOrder()

    @FXML
    private fun handleDeleteInProgressOrder() = handleDeleteOrders(inProgressOrdersTable, "In-Progress")

    @FXML
    private fun handleBatchDeleteInProgress() = handleDeleteInProgressOrder()

    @FXML
    private fun handleDeleteCompletedOrder() = handleDeleteOrders(completedOrdersTable, "Completed")

    @FXML
    private fun handleBatchDeleteCompleted() = handleDeleteCompletedOrder()

    private fun handleDeleteOrders(table: TableView<Order>, statusText: String) {
        val selectedOrders = table.selectionModel.selectedItems.toList()

        if (selectedOrders.isEmpty()) {
            dialogHelper.showWarning("No Selection", "Please select one or more orders to delete")
            return
        }

        val totalAmount = selectedOrders.sumOf { it.calculateTotal() }

        if (selectedOrders.size == 1) {
            if (dialogHelper.confirmDeleteSingle(selectedOrders[0])) {
                val result = orderManager.deleteOrder(selectedOrders[0])
                clearOrderDetails()
                updateButtonStates()
                dialogHelper.showDeleteResult(result.fileDeleted, statusText)
            }
        } else if (dialogHelper.confirmDeleteBatch(selectedOrders.size, totalAmount)) {
            val result = orderManager.deleteOrders(selectedOrders)
            clearOrderDetails()
            updateButtonStates()
            dialogHelper.showBatchDeleteResult(result.successCount, result.filesDeletedCount, statusText)
        }
    }

    //Item Management
    @FXML
    private fun handleAddItem() {
        val newItem = Item(name = "New Item", quantity = 1, price = 0.0)
        currentItems.add(newItem)
        itemsTable.selectionModel.select(newItem)
        updateOrderTotal()
    }

    @FXML
    private fun handleDeleteItem() {
        itemsTable.selectionModel.selectedItem?.let { selectedItem ->
            currentItems.remove(selectedItem)
            updateOrderTotal()
        }
    }

    private fun updateOrderTotal() {
        val selectedOrder = pendingOrdersTable.selectionModel.selectedItem
            ?: inProgressOrdersTable.selectionModel.selectedItem
            ?: completedOrdersTable.selectionModel.selectedItem

        selectedOrder?.let {
            orderManager.updateOrderItems(it, currentItems.toList())
            orderTotalLabel.text = "Total: ${OrderFormatters.formatTotal(it)}"
        }
    }

    //File Watching & Loading
    private fun startFileWatcher() {
        OrderFileManager.startFileWatcher(
            "uploads",
            { autoRefreshEnabled },
            onNewOrders = { newOrders ->
                Platform.runLater {
                    if (autoRefreshEnabled) {
                        val addedOrders = newOrders.filter { !orderManager.orderExists(it) }

                        if (addedOrders.isNotEmpty()) {
                            addAndTrackOrders(addedOrders)
                            dialogHelper.showAutoLoadResult(addedOrders.size)
                        }
                    }
                }
            },
            onReload = {
                loadOrdersFromFiles(isInitialLoad = false, showMessage = false) {
                    clearOrderDetails()
                    updateButtonStates()
                }
            }
        )
    }

    private fun loadOrdersFromFiles(
        isInitialLoad: Boolean = false,
        showMessage: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        var newOrdersCount = 0

        OrderFileManager.loadOrders(
            uploadsDir = "uploads",
            pending = orderManager.pendingOrders,
            inProgress = orderManager.inProgressOrders,
            completed = orderManager.completedOrders,
            onAdded = { newOrders ->
                addAndTrackOrders(newOrders)
                newOrdersCount = newOrders.size
            }
        )

        if (showMessage) {
            dialogHelper.showLoadResult(isInitialLoad, newOrdersCount)
        }

        onComplete()
    }

    private fun addAndTrackOrders(orders: List<Order>) {
        orders.forEach { order ->
            order.status = Order.OrderStatus.PENDING
            orderManager.pendingOrders.add(order)
            orderManager.trackOrderFile(order)
        }

        OrderPersistence.saveOrderState(
            orderManager.pendingOrders.toList(),
            orderManager.inProgressOrders.toList(),
            orderManager.completedOrders.toList()
        )
    }

    @FXML
    private fun handleRefresh() {
        loadOrdersFromFiles(isInitialLoad = false, showMessage = true) {
            updateButtonStates()
        }
    }

    //Auto-Refresh
    @FXML
    private fun handleToggleAutoRefresh() {
        autoRefreshEnabled = !autoRefreshEnabled
        updateAutoRefreshLabel()
        dialogHelper.showAutoRefreshToggled(autoRefreshEnabled)
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

    //UI Helper Methods
    private fun showOrderDetails(order: Order?) {
        if (order == null) {
            clearOrderDetails()
            return
        }

        orderTypeLabel.text = "Type: ${OrderFormatters.formatOrderType(order)}"
        orderSourceLabel.text = "Source: ${OrderFormatters.formatOrderSource(order)}"
        orderDateLabel.text = "Date: ${OrderFormatters.formatDate(order.order_date)}"
        orderTotalLabel.text = "Total: ${OrderFormatters.formatTotal(order)}"
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
        startOrderBtn.isDisable = pendingOrdersTable.selectionModel.selectedItem == null
        deletePendingBtn.isDisable = pendingOrdersTable.selectionModel.selectedItem == null

        val inProgressSelected = inProgressOrdersTable.selectionModel.selectedItem
        completeOrderBtn.isDisable = inProgressSelected == null
        undoStartBtn.isDisable = inProgressSelected == null
        deleteInProgressBtn.isDisable = inProgressSelected == null

        val completedSelected = completedOrdersTable.selectionModel.selectedItem
        undoCompleteBtn.isDisable = completedSelected == null
        deleteCompletedBtn.isDisable = completedSelected == null
    }
}
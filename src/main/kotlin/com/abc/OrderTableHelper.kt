package com.abc

import javafx.collections.ObservableList
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.control.cell.TextFieldTableCell
import javafx.beans.property.SimpleStringProperty

object OrderTableHelper {

    fun setupOrderTable(
        table: TableView<Order>,
        typeCol: TableColumn<Order, String>,
        sourceCol: TableColumn<Order, String>,
        dateCol: TableColumn<Order, String>,
        totalCol: TableColumn<Order, String>,
        orders: ObservableList<Order>,
        multipleSelection: Boolean = false
    ) {
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

        table.selectionModel.selectedIndices.addListener { _: javafx.collections.ListChangeListener.Change<out Int> ->
            table.refresh()
        }

        // Add the checkbox column as the first column
        table.columns.add(0, checkBoxCol)

        // Setup column cell value factories - now using OrderFormatters
        typeCol.setCellValueFactory { data -> SimpleStringProperty(data.value.getTypeOrDefault()) }
        typeCol.setCellFactory { createTypeCell() }

        sourceCol.setCellValueFactory { data ->
            SimpleStringProperty(data.value.source ?: "Unknown")
        }

        // Use OrderFormatters for consistent date formatting
        dateCol.setCellValueFactory { data ->
            SimpleStringProperty(OrderFormatters.formatDate(data.value.order_date))
        }

        // Use OrderFormatters for consistent currency formatting
        totalCol.setCellValueFactory { data ->
            SimpleStringProperty(OrderFormatters.formatTotal(data.value))
        }

        table.items = orders
    }

    fun setupItemsTable(
        itemsTable: TableView<Item>,
        itemNameCol: TableColumn<Item, String>,
        itemQuantityCol: TableColumn<Item, Int>,
        itemPriceCol: TableColumn<Item, Double>,
        currentItems: ObservableList<Item>,
        onItemEdited: () -> Unit
    ) {
        itemsTable.isEditable = true

        itemNameCol.cellValueFactory = PropertyValueFactory("name")
        itemNameCol.cellFactory = TextFieldTableCell.forTableColumn()
        itemNameCol.setOnEditCommit { event ->
            val item = event.rowValue
            item.name = event.newValue
            itemsTable.refresh()
            onItemEdited()
        }

        itemQuantityCol.cellValueFactory = PropertyValueFactory("quantity")
        itemQuantityCol.cellFactory = TextFieldTableCell.forTableColumn(javafx.util.converter.IntegerStringConverter())
        itemQuantityCol.setOnEditCommit { event ->
            val item = event.rowValue
            item.quantity = event.newValue
            itemsTable.refresh()
            onItemEdited()
        }

        itemPriceCol.cellValueFactory = PropertyValueFactory("price")
        itemPriceCol.cellFactory = TextFieldTableCell.forTableColumn(javafx.util.converter.DoubleStringConverter())
        itemPriceCol.setOnEditCommit { event ->
            val item = event.rowValue
            item.price = event.newValue
            itemsTable.refresh()
            onItemEdited()
        }

        itemsTable.items = currentItems
    }

    fun setupTableSelectionListeners(table: TableView<Order>, onSelect: (Order?) -> Unit) {
        table.selectionModel.selectedItemProperty().addListener { _, _, newVal ->
            onSelect(newVal)
        }
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
                            text = "🥡 To-Go"
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
}
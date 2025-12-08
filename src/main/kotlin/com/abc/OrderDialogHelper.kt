package com.abc

import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType

//Helper class for displaying alerts and confirmation dialogs in the Order Tracking application.

class OrderDialogHelper {

    //Simple Alerts
    //Show an informational alert.
    fun showInfo(title: String, message: String) {
        showAlert(Alert.AlertType.INFORMATION, title, message)
    }

    //Show a warning alert.
    fun showWarning(title: String, message: String) {
        showAlert(Alert.AlertType.WARNING, title, message)
    }

    //Show a basic alert with custom type.
    fun showAlert(alertType: Alert.AlertType, title: String, message: String) {
        val alert = Alert(alertType)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    //Confirmation Dialogs

    //Confirm a batch action (start, complete, undo, etc.)
    fun confirmBatchAction(actionName: String, count: Int): Boolean {
        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch ${actionName.replaceFirstChar { it.uppercase() }}"
        confirmAlert.headerText = "${actionName.replaceFirstChar { it.uppercase() }} $count Orders"
        confirmAlert.contentText = "Are you sure you want to $actionName $count selected order(s)?"

        val result = confirmAlert.showAndWait()
        return result.isPresent && result.get() == ButtonType.OK
    }

    //Confirm deletion of a single order.
    fun confirmDeleteSingle(order: Order): Boolean {
        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Delete"
        confirmAlert.headerText = "Delete Order"
        confirmAlert.contentText = """
            Are you sure you want to delete this order?

            Type: ${OrderFormatters.formatOrderType(order)}
            Source: ${OrderFormatters.formatOrderSource(order)}
            Total: ${OrderFormatters.formatTotal(order)}

            The order will be saved to canceledOrders.json and the source file will be removed if found.
        """.trimIndent()

        val yesButton = ButtonType("Yes", ButtonBar.ButtonData.YES)
        val noButton = ButtonType("No", ButtonBar.ButtonData.NO)
        confirmAlert.buttonTypes.setAll(yesButton, noButton)

        val result = confirmAlert.showAndWait()
        return result.isPresent && result.get() == yesButton
    }

    //Confirm deletion of multiple orders.
    fun confirmDeleteBatch(count: Int, totalAmount: Double): Boolean {
        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = "Confirm Batch Delete"
        confirmAlert.headerText = "Delete $count Orders"
        confirmAlert.contentText = """
            Are you sure you want to delete $count selected order(s)?
            
            Total Value: ${OrderFormatters.formatCurrency(totalAmount)}
            
            All orders will be saved to canceledOrders.json and source files will be removed if found.
        """.trimIndent()

        val result = confirmAlert.showAndWait()
        return result.isPresent && result.get() == ButtonType.OK
    }

    //Show a generic confirmation dialog.
    fun confirm(title: String, header: String, message: String): Boolean {
        val confirmAlert = Alert(Alert.AlertType.CONFIRMATION)
        confirmAlert.title = title
        confirmAlert.headerText = header
        confirmAlert.contentText = message

        val result = confirmAlert.showAndWait()
        return result.isPresent && result.get() == ButtonType.OK
    }

    //Specialized Messages

    //Show order loading result message.
    fun showLoadResult(isInitialLoad: Boolean, count: Int) {
        if (isInitialLoad) {
            if (count == 0) {
                showInfo(
                    "No Orders Found",
                    "No order files were found. The system will watch for new files."
                )
            } else {
                showInfo(
                    "Orders Loaded",
                    "Successfully loaded $count new order${if (count == 1) "" else "s"}. Auto-refresh is enabled."
                )
            }
        } else {
            // Refresh
            showInfo(
                "Refresh Complete",
                if (count > 0) {
                    "Found $count new order${if (count == 1) "" else "s"}"
                } else {
                    "No new orders found"
                }
            )
        }
    }

    //Show message about new orders detected by file watcher.
    fun showAutoLoadResult(count: Int) {
        showInfo(
            "New Orders",
            "Automatically loaded $count new order${if (count == 1) "" else "s"}"
        )
    }

    //Show auto-refresh toggle result.
    fun showAutoRefreshToggled(enabled: Boolean) {
        showInfo(
            "Auto Refresh",
            if (enabled) {
                "Auto refresh enabled - new files will be loaded automatically"
            } else {
                "Auto refresh disabled - use manual refresh"
            }
        )
    }

    //Show result of batch order start operation.
    fun showBatchStartResult(successCount: Int) {
        showInfo(
            "Batch Start Complete",
            "$successCount order${if (successCount == 1) "" else "s"} moved to In-Progress"
        )
    }

    //Show result of batch order completion operation.
    fun showBatchCompleteResult(successCount: Int) {
        showInfo(
            "Batch Complete",
            "$successCount order${if (successCount == 1) "" else "s"} marked as completed"
        )
    }

    //Show result of batch undo operation.
    fun showBatchUndoResult(successCount: Int, targetStatus: String) {
        showInfo(
            "Batch Undo Complete",
            "$successCount order${if (successCount == 1) "" else "s"} moved back to $targetStatus"
        )
    }

    //Show result of order deletion.
    fun showDeleteResult(fileDeleted: Boolean, statusText: String) {
        val message = if (fileDeleted) {
            "$statusText order deleted and saved to canceledOrders.json (source file removed)"
        } else {
            "$statusText order deleted and saved to canceledOrders.json (source file not found)"
        }
        showInfo("Delete Order", message)
    }

    //Show result of batch deletion.
    fun showBatchDeleteResult(successCount: Int, filesDeletedCount: Int, statusText: String) {
        val message = """
            $successCount $statusText order${if (successCount == 1) "" else "s"} deleted
            $filesDeletedCount source file${if (filesDeletedCount == 1) "" else "s"} removed
            All orders saved to canceledOrders.json
        """.trimIndent()
        showInfo("Batch Delete Complete", message)
    }
}
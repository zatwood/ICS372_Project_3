package com.abc

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


//Utility for formatting order data consistently across the app
object OrderFormatters {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    //Format a timestamp to a readable date string.
    fun formatDate(timestamp: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(timestamp)
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            dateTime.format(DATE_FORMATTER)
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    // Format an order's total as currency.
    fun formatTotal(order: Order?): String {
        return if (order == null) {
            "$0.00"
        } else {
            formatCurrency(order.calculateTotal())
        }
    }

    //Format a dollar amount as currency.
    fun formatCurrency(amount: Double): String {
        return String.format("$%.2f", amount)
    }

    //Get the order type or "Unknown" if not set.
    fun formatOrderType(order: Order): String {
        return order.getTypeOrDefault()
    }

    // Get the order source or "Unknown" if not set.
    fun formatOrderSource(order: Order): String {
        return order.source ?: "Unknown"
    }
}
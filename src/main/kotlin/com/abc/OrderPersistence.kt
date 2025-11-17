package com.abc

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object OrderPersistence {
    private val mapper = ObjectMapper().apply {
        configure(SerializationFeature.INDENT_OUTPUT, true)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private const val PERSISTENCE_FILE = "orders_state.json"
    private const val CANCELED_ORDERS_FILE = "canceledOrders.json"

    data class OrderState(
        var pendingOrders: List<Order> = emptyList(),
        var inProgressOrders: List<Order> = emptyList(),
        var completedOrders: List<Order> = emptyList()
    )

    fun saveOrderState(pendingOrders: List<Order>, inProgressOrders: List<Order>, completedOrders: List<Order>): Boolean {
        return try {
            mapper.writeValue(File(PERSISTENCE_FILE), OrderState(pendingOrders, inProgressOrders, completedOrders))
            println("Order state saved")
            true
        } catch (e: Exception) {
            System.err.println("Error saving order state: ${e.message}")
            false
        }
    }

    fun loadOrderState(): OrderState? {
        return try {
            val filePath = Paths.get(PERSISTENCE_FILE)
            if (!Files.exists(filePath)) {
                return null
            }

            val state = mapper.readValue(filePath.toFile(), OrderState::class.java)
            println("Loaded: ${state.pendingOrders.size} pending, ${state.inProgressOrders.size} in-progress, ${state.completedOrders.size} completed orders")
            state
        } catch (e: Exception) {
            System.err.println("Error loading order state: ${e.message}")
            null
        }
    }

    fun hasSavedState(): Boolean {
        return Files.exists(Paths.get(PERSISTENCE_FILE))
    }

    fun clearSavedState() {
        try {
            Files.deleteIfExists(Paths.get(PERSISTENCE_FILE))
            println("Saved order state cleared")
        } catch (e: Exception) {
            System.err.println("Error clearing saved state: ${e.message}")
        }
    }

    fun saveCanceledOrder(order: Order): Boolean {
        return try {
            val canceledOrders = loadCanceledOrders().toMutableList()
            canceledOrders.add(order)

            mapper.writeValue(File(CANCELED_ORDERS_FILE), canceledOrders)
            println("Canceled order saved: ${order.source}")
            true
        } catch (e: Exception) {
            System.err.println("Error saving canceled order: ${e.message}")
            false
        }
    }

    fun loadCanceledOrders(): List<Order> {
        return try {
            val filePath = Paths.get(CANCELED_ORDERS_FILE)
            if (!Files.exists(filePath)) {
                return emptyList()
            }

            val canceledOrders = mapper.readValue(
                filePath.toFile(),
                mapper.typeFactory.constructCollectionType(List::class.java, Order::class.java)
            ) as? List<Order> ?: emptyList()

            println("Loaded ${canceledOrders.size} canceled orders")
            canceledOrders
        } catch (e: Exception) {
            System.err.println("Error loading canceled orders: ${e.message}")
            emptyList()
        }
    }

    fun hasCanceledOrders(): Boolean {
        return Files.exists(Paths.get(CANCELED_ORDERS_FILE))
    }

    fun clearCanceledOrders() {
        try {
            Files.deleteIfExists(Paths.get(CANCELED_ORDERS_FILE))
            println("Canceled orders cleared")
        } catch (e: Exception) {
            System.err.println("Error clearing canceled orders: ${e.message}")
        }
    }
}

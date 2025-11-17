package com.abc

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Order(
    var type: String? = null,
    var order_date: Long = 0,
    var items: List<Item>? = null,
    var source: String? = null,
    var status: OrderStatus = OrderStatus.PENDING
) {
    enum class OrderStatus {
        PENDING, IN_PROGRESS, COMPLETED
    }

    @JsonIgnore
    fun getTypeOrDefault(): String = type?.trim() ?: "Unknown"

    @JsonIgnore
    fun getItemsOrEmpty(): List<Item> = items ?: emptyList()

    @JsonIgnore
    fun calculateTotal(): Double {
        return getItemsOrEmpty().sumOf { it.quantity * it.price }
    }

    @JsonIgnore
    fun isValid(): Boolean {
        return order_date > 0 && getItemsOrEmpty().isNotEmpty()
    }

    override fun toString(): String {
        return "Order{type='${getTypeOrDefault()}', source='$source', date=$order_date, items=${getItemsOrEmpty().size}, total=${'$'}%.2f}".format(calculateTotal())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as Order

        // Compare all fields including items list content
        if (order_date != other.order_date) return false
        if (type != other.type) return false
        if (source != other.source) return false
        if (status != other.status) return false

        val thisItems = getItemsOrEmpty()
        val otherItems = other.getItemsOrEmpty()

        if (thisItems.size != otherItems.size) return false

        // Compare each item in the list
        for (i in thisItems.indices) {
            val thisItem = thisItems[i]
            val otherItem = otherItems[i]
            if (thisItem.name != otherItem.name ||
                thisItem.quantity != otherItem.quantity ||
                thisItem.price != otherItem.price) {
                return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + (source?.hashCode() ?: 0)
        result = 31 * result + order_date.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + getItemsOrEmpty().hashCode()
        return result
    }
}

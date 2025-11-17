package com.abc

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlin.math.max

@JsonIgnoreProperties(ignoreUnknown = true)
class Item(
    name: String? = null,
    quantity: Int = 0,
    price: Double = 0.0
) {
    var name: String? = name?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Item"
        set(value) {
            field = value?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Item"
        }

    var quantity: Int = max(quantity, 0)
        set(value) {
            field = max(value, 0)
        }

    var price: Double = max(price, 0.0)
        set(value) {
            field = max(value, 0.0)
        }

    @get:JsonIgnore
    val lineTotal: Double
        get() = quantity * price

    override fun toString(): String {
        return "Item{name='$name', quantity=$quantity, price=${'$'}%.2f}".format(price)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as Item
        return name == other.name && quantity == other.quantity && price == other.price
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + quantity
        result = 31 * result + price.hashCode()
        return result
    }
}

package com.oriondev.moneywallet.next.core

import org.json.JSONObject

/**
 * Icon description compatible with the legacy JSON format:
 * {"type":"color","color":"#RRGGBB","name":"AB"} or {"type":"resource","resource":"ic_icon_cocktail"}.
 * A [Symbol] is a coloured letter icon with an extra "symbol" key: the legacy app ignores the key
 * and shows the letters, this app draws the Material symbol on the colour.
 */
sealed class IconSpec {
    data class Letter(val color: String, val text: String) : IconSpec()
    data class Resource(val name: String) : IconSpec()
    data class Symbol(val key: String, val color: String, val text: String = key.take(2).uppercase()) : IconSpec()

    fun encode(): String = when (this) {
        is Letter -> JSONObject().put("type", "color").put("color", color).put("name", text).toString()
        is Resource -> JSONObject().put("type", "resource").put("resource", name).toString()
        is Symbol -> JSONObject().put("type", "color").put("color", color).put("name", text).put("symbol", key).toString()
    }

    companion object {
        fun parse(raw: String?): IconSpec {
            if (raw.isNullOrBlank()) return Letter("#5C6BC0", "?")
            return try {
                val o = JSONObject(raw)
                when (o.optString("type")) {
                    "resource" -> Resource(o.optString("resource"))
                    else -> if (o.optString("symbol").isNotEmpty()) {
                        Symbol(o.optString("symbol"), o.optString("color", "#5C6BC0"), o.optString("name", "?").take(2))
                    } else Letter(o.optString("color", "#5C6BC0"), o.optString("name", "?").take(2))
                }
            } catch (e: Exception) {
                Letter("#5C6BC0", raw.take(2))
            }
        }

        fun letterFor(name: String, color: String): IconSpec = Letter(
            color,
            name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
        )

        val palette = listOf(
            "#006C4C", "#3F51B5", "#E91E63", "#FF9800", "#009688", "#9C27B0", "#F44336", "#2196F3",
            "#795548", "#607D8B", "#4CAF50", "#FFC107", "#00BCD4", "#673AB7", "#8BC34A", "#FF5722",
        )
    }
}

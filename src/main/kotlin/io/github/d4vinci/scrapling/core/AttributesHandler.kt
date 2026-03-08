package io.github.d4vinci.scrapling.core

import kotlinx.serialization.json.JsonElement
import org.jsoup.nodes.Attributes

class AttributesHandler private constructor(
    private val values: Map<String, String>,
) {
    companion object {
        fun empty(): AttributesHandler = AttributesHandler(emptyMap())

        fun from(attributes: Attributes): AttributesHandler =
            AttributesHandler(attributes.associate { it.key to it.value })
    }

    operator fun get(name: String): TextHandler? = values[name]?.let(::TextHandler)

    fun searchValues(query: String, ignoreCase: Boolean = true): Map<String, TextHandler> =
        values.filterValues { it.contains(query, ignoreCase) }.mapValues { TextHandler(it.value) }

    fun jsonString(name: String): JsonElement? = get(name)?.json()

    fun asMap(): Map<String, String> = values.toMap()
}

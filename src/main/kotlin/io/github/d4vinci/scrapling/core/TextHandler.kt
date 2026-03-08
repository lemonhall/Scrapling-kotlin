package io.github.d4vinci.scrapling.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class TextHandler(val value: String) {
    fun clean(): TextHandler = TextHandler(value.normalizeWhitespace())

    fun get(): String = value

    fun getAll(): List<String> = listOf(value)

    fun json(): JsonElement? = runCatching { Json.parseToJsonElement(value) }.getOrNull()

    fun re(pattern: Regex, groupIndex: Int = 0): List<String> =
        pattern.findAll(value).mapNotNull { match -> match.groups[groupIndex]?.value ?: match.value }.toList()

    fun re(pattern: String, groupIndex: Int = 0): List<String> = re(Regex(pattern), groupIndex)

    fun reFirst(pattern: Regex, groupIndex: Int = 0): String? = re(pattern, groupIndex).firstOrNull()

    fun reFirst(pattern: String, groupIndex: Int = 0): String? = reFirst(Regex(pattern), groupIndex)

    override fun toString(): String = value
}

internal fun String.normalizeWhitespace(): String = trim().replace(Regex("\\s+"), " ")


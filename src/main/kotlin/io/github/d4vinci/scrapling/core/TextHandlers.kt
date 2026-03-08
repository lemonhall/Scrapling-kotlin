package io.github.d4vinci.scrapling.core

class TextHandlers(
    private val values: List<TextHandler>,
) : List<TextHandler> by values {
    fun extract(): List<String> = values.map { it.value }

    fun re(pattern: Regex, groupIndex: Int = 0): List<String> = values.flatMap { it.re(pattern, groupIndex) }

    fun re(pattern: String, groupIndex: Int = 0): List<String> = re(Regex(pattern), groupIndex)

    fun reFirst(pattern: Regex, groupIndex: Int = 0): String? = re(pattern, groupIndex).firstOrNull()

    fun reFirst(pattern: String, groupIndex: Int = 0): String? = reFirst(Regex(pattern), groupIndex)
}


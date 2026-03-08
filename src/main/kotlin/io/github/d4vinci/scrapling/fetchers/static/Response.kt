package io.github.d4vinci.scrapling.fetchers.static

import io.github.d4vinci.scrapling.parser.Selector
import io.github.d4vinci.scrapling.parser.Selectors

class Response(
    val url: String,
    val content: ByteArray,
    val status: Int,
    val reason: String,
    val cookies: Map<String, String>,
    val headers: Map<String, String>,
    val requestHeaders: Map<String, String>,
    val method: String,
    val history: List<Response> = emptyList(),
    val meta: Map<String, Any> = emptyMap(),
    selectorConfig: SelectorConfig = SelectorConfig(url = url),
) {
    private val selector = Selector(
        content = content.toString(Charsets.UTF_8),
        url = selectorConfig.adaptiveDomain ?: selectorConfig.url,
        adaptive = selectorConfig.adaptive,
        storageSystem = selectorConfig.storageSystem,
    )

    val body: ByteArray
        get() = content

    fun css(
        selectorExpression: String,
        identifier: String = "",
        adaptive: Boolean = false,
        autoSave: Boolean = false,
        percentage: Int = 0,
    ): Selectors = selector.css(selectorExpression, identifier, adaptive, autoSave, percentage)

    fun xpath(
        selectorExpression: String,
        identifier: String = "",
        adaptive: Boolean = false,
        autoSave: Boolean = false,
        percentage: Int = 0,
        variables: Map<String, Any?> = emptyMap(),
    ): Selectors = selector.xpath(selectorExpression, identifier, adaptive, autoSave, percentage, variables)

    fun getAllText() = selector.getAllText()
}

data class SelectorConfig(
    val url: String = "",
    val adaptiveDomain: String? = null,
    val adaptive: Boolean = false,
    val storageSystem: io.github.d4vinci.scrapling.core.storage.StorageSystem? = null,
)


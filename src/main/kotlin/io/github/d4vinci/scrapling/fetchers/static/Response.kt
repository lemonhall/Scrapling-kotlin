package io.github.d4vinci.scrapling.fetchers.static

import io.github.d4vinci.scrapling.parser.Selector
import io.github.d4vinci.scrapling.parser.Selectors
import io.github.d4vinci.scrapling.spiders.Request
import java.net.URI
import kotlin.reflect.KFunction

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
    var meta: Map<String, Any> = emptyMap(),
    selectorConfig: SelectorConfig = SelectorConfig(url = url),
) {
    val selectorConfig: SelectorConfig = selectorConfig

    private val selector = Selector(
        content = content.toString(Charsets.UTF_8),
        url = selectorConfig.adaptiveDomain ?: selectorConfig.url,
        adaptive = selectorConfig.adaptive,
        storageSystem = selectorConfig.storageSystem,
    )

    var request: Request? = null

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

    fun follow(
        url: String,
        sid: String = "",
        callback: KFunction<*>? = null,
        priority: Int? = null,
        dontFilter: Boolean = false,
        meta: Map<String, Any?> = emptyMap(),
        refererFlow: Boolean = true,
        sessionOptions: Map<String, Any?> = emptyMap(),
    ): Request {
        val currentRequest = request ?: error("This response has no request set yet.")
        val mergedSessionOptions = currentRequest.sessionOptions.toMutableMap().apply {
            putAll(sessionOptions)
        }

        if (refererFlow) {
            val headers = mergedSessionOptions.stringMap("headers").toMutableMap()
            headers["referer"] = this.url
            mergedSessionOptions["headers"] = headers

            val extraHeaders = mergedSessionOptions.stringMap("extraHeaders").toMutableMap()
            extraHeaders["referer"] = this.url
            mergedSessionOptions["extraHeaders"] = extraHeaders
            mergedSessionOptions["googleSearch"] = false
        }

        return Request(
            url = joinUrl(url),
            sid = sid.ifBlank { currentRequest.sid },
            callback = callback ?: currentRequest.callback,
            priority = priority ?: currentRequest.priority,
            dontFilter = dontFilter,
            meta = this.meta.mapValues { (_, value) -> value as Any? } + meta,
            sessionOptions = mergedSessionOptions,
        )
    }

    internal fun bindRequest(request: Request): Response {
        this.request = request
        this.meta = request.meta.filterValues { value -> value != null }.mapValues { (_, value) -> value as Any } + meta
        return this
    }

    private fun joinUrl(target: String): String {
        if (url.isBlank()) return target
        return runCatching { URI(url).resolve(target).toString() }.getOrElse { target }
    }
}

data class SelectorConfig(
    val url: String = "",
    val adaptiveDomain: String? = null,
    val adaptive: Boolean = false,
    val storageSystem: io.github.d4vinci.scrapling.core.storage.StorageSystem? = null,
)

private fun MutableMap<String, Any?>.stringMap(key: String): Map<String, String> =
    (this[key] as? Map<*, *>)
        ?.mapNotNull { (entryKey, entryValue) ->
            val normalizedKey = entryKey?.toString() ?: return@mapNotNull null
            normalizedKey to (entryValue?.toString() ?: "")
        }
        ?.toMap()
        .orEmpty()

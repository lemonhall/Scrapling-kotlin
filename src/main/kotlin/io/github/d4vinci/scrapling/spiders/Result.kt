package io.github.d4vinci.scrapling.spiders

import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ItemList : ArrayList<Map<String, Any?>>() {
    fun toJson(path: Path, indent: Boolean = false) {
        path.parent?.createDirectories()
        path.writeText(json(indent).encodeToString<JsonElement>(JsonArray(map(::toJsonElement))), Charsets.UTF_8)
    }

    fun toJson(path: String, indent: Boolean = false) {
        toJson(Path.of(path), indent)
    }

    fun toJsonl(path: Path) {
        path.parent?.createDirectories()
        val content = joinToString(separator = "\n", postfix = if (isEmpty()) "" else "\n") { item ->
            json(false).encodeToString<JsonElement>(toJsonElement(item))
        }
        path.writeText(content, Charsets.UTF_8)
    }

    fun toJsonl(path: String) {
        toJsonl(Path.of(path))
    }
}

data class CrawlStats(
    var requestsCount: Int = 0,
    var concurrentRequests: Int = 0,
    var concurrentRequestsPerDomain: Int = 0,
    var failedRequestsCount: Int = 0,
    var offsiteRequestsCount: Int = 0,
    var responseBytes: Int = 0,
    var itemsScraped: Int = 0,
    var itemsDropped: Int = 0,
    var startTime: Double = 0.0,
    var endTime: Double = 0.0,
    var downloadDelay: Double = 0.0,
    var blockedRequestsCount: Int = 0,
    val customStats: MutableMap<String, Any?> = linkedMapOf(),
    val responseStatusCount: MutableMap<String, Int> = linkedMapOf(),
    val domainsResponseBytes: MutableMap<String, Int> = linkedMapOf(),
    val sessionsRequestsCount: MutableMap<String, Int> = linkedMapOf(),
    val proxies: MutableList<Any> = mutableListOf(),
    var logLevelsCounter: Map<String, Int> = emptyMap(),
) {
    val elapsedSeconds: Double
        get() = endTime - startTime

    val requestsPerSecond: Double
        get() = if (elapsedSeconds == 0.0) 0.0 else requestsCount / elapsedSeconds

    fun incrementStatus(status: Int) {
        val key = "status_$status"
        responseStatusCount[key] = responseStatusCount.getOrDefault(key, 0) + 1
    }

    fun incrementResponseBytes(domain: String, count: Int) {
        responseBytes += count
        domainsResponseBytes[domain] = domainsResponseBytes.getOrDefault(domain, 0) + count
    }

    fun incrementRequestsCount(sid: String) {
        requestsCount += 1
        sessionsRequestsCount[sid] = sessionsRequestsCount.getOrDefault(sid, 0) + 1
    }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "items_scraped" to itemsScraped,
        "items_dropped" to itemsDropped,
        "elapsed_seconds" to elapsedSeconds.round2(),
        "download_delay" to downloadDelay.round2(),
        "concurrent_requests" to concurrentRequests,
        "concurrent_requests_per_domain" to concurrentRequestsPerDomain,
        "requests_count" to requestsCount,
        "requests_per_second" to requestsPerSecond.round2(),
        "sessions_requests_count" to sessionsRequestsCount,
        "failed_requests_count" to failedRequestsCount,
        "offsite_requests_count" to offsiteRequestsCount,
        "blocked_requests_count" to blockedRequestsCount,
        "response_status_count" to responseStatusCount,
        "response_bytes" to responseBytes,
        "domains_response_bytes" to domainsResponseBytes,
        "proxies" to proxies,
        "custom_stats" to customStats,
        "log_count" to logLevelsCounter,
    )
}

data class CrawlResult(
    val stats: CrawlStats,
    val items: ItemList,
    val paused: Boolean = false,
) : Iterable<Map<String, Any?>> {
    val completed: Boolean
        get() = !paused

    override fun iterator(): Iterator<Map<String, Any?>> = items.iterator()

    operator fun component4(): Boolean = completed
}

@OptIn(ExperimentalSerializationApi::class)
private fun json(indent: Boolean): Json = Json {
    prettyPrint = indent
    if (indent) {
        prettyPrintIndent = "  "
    }
    encodeDefaults = true
    explicitNulls = true
}

private fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(
        value.entries
            .associate { entry -> entry.key.toString() to toJsonElement(entry.value) }
            .toSortedMap(),
    )
    is Iterable<*> -> JsonArray(value.map(::toJsonElement))
    is Array<*> -> JsonArray(value.map(::toJsonElement))
    else -> JsonPrimitive(value.toString())
}

private fun Double.round2(): Double = kotlin.math.round(this * 100.0) / 100.0

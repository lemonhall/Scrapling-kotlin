package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ObjectInputStream
import java.io.Serializable
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions

class Request(
    val url: String,
    val sid: String = "",
    @Transient var callback: KFunction<*>? = null,
    val priority: Int = 0,
    val dontFilter: Boolean = false,
    meta: Map<String, Any?> = emptyMap(),
    var retryCount: Int = 0,
    sessionOptions: Map<String, Any?> = emptyMap(),
) : Comparable<Request>, Serializable {
    val meta: MutableMap<String, Any?> = meta.toMutableMap()
    val sessionOptions: MutableMap<String, Any?> = sessionOptions.toMutableMap()

    @Transient
    private var fingerprint: ByteArray? = null

    private var callbackName: String? = callback?.name

    fun copy(): Request = Request(
        url = url,
        sid = sid,
        callback = callback,
        priority = priority,
        dontFilter = dontFilter,
        meta = meta.toMap(),
        retryCount = retryCount,
        sessionOptions = sessionOptions.toMap(),
    )

    val domain: String
        get() = runCatching { URI(url).rawAuthority.orEmpty() }.getOrDefault("")

    fun updateFingerprint(
        includeKwargs: Boolean = false,
        includeHeaders: Boolean = false,
        keepFragments: Boolean = false,
    ): ByteArray {
        fingerprint?.let { return it }

        val payload = linkedMapOf<String, JsonElement>(
            "sid" to JsonPrimitive(sid),
            "body" to JsonPrimitive(requestBodyValue(sessionOptions)),
            "method" to JsonPrimitive((sessionOptions["method"] as? String ?: "GET").uppercase()),
            "url" to JsonPrimitive(canonicalizeUrl(url, keepFragments)),
        )
        if (includeKwargs) {
            payload["kwargs"] = JsonArray(
                sessionOptions.keys
                    .filter { key -> key.lowercase() !in setOf("data", "json") }
                    .map { key -> JsonPrimitive(key.lowercase()) }
                    .sortedBy(JsonPrimitive::content),
            )
        }
        if (includeHeaders) {
            val headers = (sessionOptions["headers"] as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    val normalizedKey = key?.toString()?.lowercase() ?: return@mapNotNull null
                    normalizedKey to JsonPrimitive(value?.toString()?.lowercase().orEmpty())
                }
                ?.sortedBy { (key, _) -> key }
                ?.associate { (key, value) -> key to value }
                .orEmpty()
            payload["headers"] = JsonObject(headers)
        }

        val digest = MessageDigest.getInstance("SHA-1")
        val encoded = stableJson.encodeToString(JsonObject(payload))
        val bytes = digest.digest(encoded.toByteArray(Charsets.UTF_8))
        fingerprint = bytes
        return bytes
    }

    fun callbackName(): String? = callbackName

    fun restoreCallback(spider: Spider) {
        if (!callbackName.isNullOrBlank()) {
            callback = spider::class.memberFunctions.firstOrNull { function -> function.name == callbackName } ?: spider::parse
        }
    }

    override fun compareTo(other: Request): Int = priority.compareTo(other.priority)

    override fun equals(other: Any?): Boolean {
        if (other !is Request) {
            return false
        }
        val left = fingerprint ?: error("Cannot compare requests before generating their fingerprints!")
        val right = other.fingerprint ?: error("Cannot compare requests before generating their fingerprints!")
        return left.contentEquals(right)
    }

    override fun hashCode(): Int = fingerprint?.contentHashCode() ?: url.hashCode()

    override fun toString(): String = url

    fun debugString(): String = "<Request($url) priority=$priority callback=${callback?.name ?: "None"}>"

    @Suppress("unused")
    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        fingerprint = null
    }

    companion object {
        private val stableJson = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = true
        }

        private fun requestBodyValue(sessionOptions: Map<String, Any?>): String {
            val data = sessionOptions["data"]
            if (data != null) {
                return when (data) {
                    is ByteArray -> data.toHexString()
                    is String -> data
                    is Map<*, *> -> data.entries
                        .sortedBy { entry -> entry.key?.toString().orEmpty() }
                        .joinToString("&") { (key, value) ->
                            "${encode(key?.toString().orEmpty())}=${encode(value?.toString().orEmpty())}"
                        }
                    is Iterable<*> -> data.joinToString("&") { value -> encode(value?.toString().orEmpty()) }
                    else -> data.toString()
                }
            }
            return stableValue(sessionOptions["json"])
        }

        private fun canonicalizeUrl(rawUrl: String, keepFragments: Boolean): String {
            val uri = URI(rawUrl)
            val query = uri.rawQuery
                ?.split('&')
                ?.filter { it.isNotBlank() }
                ?.sorted()
                ?.joinToString("&")
            return URI(
                uri.scheme?.lowercase(),
                uri.rawUserInfo,
                uri.host?.lowercase(),
                uri.port,
                uri.rawPath,
                query,
                if (keepFragments) uri.rawFragment else null,
            ).toString()
        }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

        private fun stableValue(value: Any?): String = stableJson.encodeToString(toJsonElement(value))

        private fun toJsonElement(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is ByteArray -> JsonPrimitive(value.toHexString())
            is Map<*, *> -> JsonObject(
                value.entries
                    .sortedBy { entry -> entry.key?.toString().orEmpty() }
                    .associate { entry -> entry.key?.toString().orEmpty() to toJsonElement(entry.value) },
            )
            is Iterable<*> -> JsonArray(value.map(::toJsonElement))
            is Array<*> -> JsonArray(value.map(::toJsonElement))
            else -> JsonPrimitive(value.toString())
        }
    }
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

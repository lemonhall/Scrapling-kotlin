package io.github.d4vinci.scrapling.ai

import io.github.d4vinci.scrapling.core.shell.ExtractionType
import io.github.d4vinci.scrapling.fetchers.browser.AsyncDynamicFetcher
import io.github.d4vinci.scrapling.fetchers.browser.AsyncStealthyFetcher
import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.static.FetcherClient
import io.github.d4vinci.scrapling.fetchers.static.Impersonation
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class DefaultStaticToolExecutor : StaticToolExecutor {
    private val client = FetcherClient()

    override fun execute(method: String, url: String, options: RequestOptions): Response = when (method.uppercase()) {
        "GET" -> client.get(url, options)
        "POST" -> client.post(url, options.data, options)
        "PUT" -> client.put(url, options.data, options)
        "DELETE" -> client.delete(url, options)
        else -> error("Unsupported method: $method")
    }
}

internal class DefaultAsyncBrowserToolExecutor : AsyncBrowserToolExecutor {
    override suspend fun execute(mode: BrowserToolMode, url: String, options: BrowserFetchOptions): Response = when (mode) {
        BrowserToolMode.DYNAMIC -> AsyncDynamicFetcher.fetch(url, options)
        BrowserToolMode.STEALTHY -> AsyncStealthyFetcher.fetch(url, options)
    }
}

internal fun responseToToolResult(value: Any): McpToolCallResult {
    val structured = when (value) {
        is ResponseModel -> value.toJsonElement()
        is List<*> -> JsonArray(value.filterIsInstance<ResponseModel>().map(ResponseModel::toJsonElement))
        else -> JsonNull
    }
    return McpToolCallResult(
        content = listOf(McpTextContent(text = structured.toString())),
        structuredContent = structured,
        isError = false,
    )
}

internal fun jsonRpcResult(id: JsonElement, result: JsonElement): String = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put("id", id)
    put("result", result)
}.toString()

internal fun jsonRpcError(id: JsonElement?, code: Int, message: String): String = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put("id", id ?: JsonNull)
    putJsonObject("error") {
        put("code", JsonPrimitive(code))
        put("message", JsonPrimitive(message))
    }
}.toString()

internal fun ToolDescriptor.toMcpTool(): McpToolDefinition = McpToolDefinition(
    name = name,
    title = name,
    description = description,
    inputSchema = toolInputSchema(name),
)

internal fun toolInputSchema(name: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    putJsonObject("properties") {
        when (name) {
            "get", "fetch", "stealthy_fetch" -> putJsonObject("url") { put("type", JsonPrimitive("string")) }
            "bulk_get", "bulk_fetch", "bulk_stealthy_fetch" -> {
                putJsonObject("urls") {
                    put("type", JsonPrimitive("array"))
                    putJsonObject("items") { put("type", JsonPrimitive("string")) }
                }
            }
        }
    }
    putJsonArray("required") {
        when (name) {
            "get", "fetch", "stealthy_fetch" -> add(JsonPrimitive("url"))
            "bulk_get", "bulk_fetch", "bulk_stealthy_fetch" -> add(JsonPrimitive("urls"))
        }
    }
}

internal fun JsonObject.requiredString(key: String): String = this[key]?.jsonPrimitive?.contentOrNull
    ?: error("Missing required argument: $key")

internal fun JsonObject.requiredStringList(key: String): List<String> = this[key]?.jsonArray?.map { element ->
    element.jsonPrimitive.content
} ?: error("Missing required argument: $key")

internal fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.optionalBoolean(key: String, default: Boolean): Boolean = this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: default

internal fun JsonObject.optionalInt(key: String, default: Int): Int = this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: default

internal fun JsonObject.optionalDouble(key: String, default: Double): Double = this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: default

internal fun JsonObject.optionalNullableDouble(key: String): Double? = this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

internal fun JsonObject.optionalStringMap(key: String): Map<String, String> = (this[key] as? JsonObject)
    ?.mapValues { (_, value) -> value.jsonPrimitive.content }
    .orEmpty()

internal fun JsonObject.optionalNullableStringMap(key: String): Map<String, String>? = (this[key] as? JsonObject)
    ?.mapValues { (_, value) -> value.jsonPrimitive.content }

internal fun JsonObject.impersonation(key: String): Impersonation {
    val value = this[key] ?: return Impersonation.Single("chrome")
    return when (value) {
        is JsonPrimitive -> Impersonation.parse(value.content)
        is JsonArray -> {
            val browsers = value.map { element -> element.jsonPrimitive.content }
            if (browsers.size == 1) Impersonation.Single(browsers.single()) else Impersonation.Multiple(browsers)
        }
        else -> Impersonation.Single("chrome")
    }
}

internal fun JsonObject.extractionType(key: String, default: ExtractionType): ExtractionType = this[key]?.jsonPrimitive?.contentOrNull
    ?.let { value -> ExtractionType.valueOf(value.uppercase()) }
    ?: default

internal fun ResponseModel.toJsonElement(): JsonElement = buildJsonObject {
    put("status", JsonPrimitive(status))
    putJsonArray("content") { content.forEach { item -> add(JsonPrimitive(item)) } }
    put("url", JsonPrimitive(url))
}

internal fun McpInitializeResult.toJsonElement(): JsonElement = buildJsonObject {
    put("protocolVersion", JsonPrimitive(protocolVersion))
    putJsonObject("capabilities") {
        putJsonObject("tools") {
            put("listChanged", JsonPrimitive(capabilities.tools.listChanged))
        }
    }
    putJsonObject("serverInfo") {
        put("name", JsonPrimitive(serverInfo.name))
        put("version", JsonPrimitive(serverInfo.version))
    }
}

internal fun McpToolListResult.toJsonElement(): JsonElement = buildJsonObject {
    put("tools", JsonArray(tools.map(McpToolDefinition::toJsonElement)))
}

internal fun McpToolDefinition.toJsonElement(): JsonElement = buildJsonObject {
    put("name", JsonPrimitive(name))
    put("title", JsonPrimitive(title))
    put("description", JsonPrimitive(description))
    put("inputSchema", inputSchema)
}

internal fun McpToolCallResult.toJsonElement(): JsonElement = buildJsonObject {
    put("content", JsonArray(content.map(McpTextContent::toJsonElement)))
    put("structuredContent", structuredContent)
    put("isError", JsonPrimitive(isError))
}

internal fun McpTextContent.toJsonElement(): JsonElement = buildJsonObject {
    put("type", JsonPrimitive(type))
    put("text", JsonPrimitive(text))
}

@Serializable
internal data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: McpServerCapabilities,
    val serverInfo: McpServerInfo,
)

@Serializable
internal data class McpServerCapabilities(
    val tools: McpToolCapabilities = McpToolCapabilities(),
)

@Serializable
internal data class McpToolCapabilities(
    val listChanged: Boolean = false,
)

@Serializable
internal data class McpServerInfo(
    val name: String,
    val version: String,
)

@Serializable
internal data class McpToolDefinition(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: JsonObject,
)

@Serializable
internal data class McpToolListResult(
    val tools: List<McpToolDefinition>,
)

@Serializable
internal data class McpToolCallResult(
    val content: List<McpTextContent>,
    val structuredContent: JsonElement,
    val isError: Boolean = false,
)

@Serializable
internal data class McpTextContent(
    val type: String = "text",
    val text: String,
)

internal const val MCP_PROTOCOL_VERSION = "2025-06-18"
internal const val MCP_SERVER_VERSION = "0.1.0"

@OptIn(ExperimentalSerializationApi::class)
internal val mcpJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
    explicitNulls = false
}

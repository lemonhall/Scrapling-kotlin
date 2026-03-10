package io.github.d4vinci.scrapling.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.d4vinci.scrapling.core.shell.ContentExtractor
import io.github.d4vinci.scrapling.core.shell.ExtractionType
import io.github.d4vinci.scrapling.fetchers.browser.AsyncDynamicFetcher
import io.github.d4vinci.scrapling.fetchers.browser.AsyncStealthyFetcher
import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.browser.BrowserProxyUrl
import io.github.d4vinci.scrapling.fetchers.static.FetcherClient
import io.github.d4vinci.scrapling.fetchers.static.Impersonation
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
data class ResponseModel(
    val status: Int,
    val content: List<String>,
    val url: String,
)

@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
)

@Serializable
data class McpLaunchResult(
    val transport: String,
    val host: String,
    val port: Int,
    val tools: List<ToolDescriptor>,
)

fun interface StaticToolExecutor {
    fun execute(method: String, url: String, options: RequestOptions): Response
}

fun interface AsyncBrowserToolExecutor {
    suspend fun execute(mode: BrowserToolMode, url: String, options: BrowserFetchOptions): Response
}

enum class BrowserToolMode {
    DYNAMIC,
    STEALTHY,
}

fun interface McpServerRuntime {
    fun serve(serverName: String, launch: McpLaunchResult, handler: McpProtocolHandler, sink: (String) -> Unit)
}

fun interface McpProtocolHandler {
    fun handle(request: String): String?
}

class ScraplingMcpServer(
    private val staticExecutor: StaticToolExecutor = DefaultStaticToolExecutor(),
    private val browserExecutor: AsyncBrowserToolExecutor = DefaultAsyncBrowserToolExecutor(),
    private val runtime: McpServerRuntime = JdkMcpServerRuntime(),
) {
    fun get(
        url: String,
        impersonate: Impersonation = Impersonation.Single("chrome"),
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        cssSelector: String? = null,
        mainContentOnly: Boolean = true,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        timeout: Int = 30,
        followRedirects: Boolean = true,
        maxRedirects: Int = 30,
        retries: Int = 3,
        retryDelay: Int = 1,
        proxy: String? = null,
        proxyAuth: Map<String, String>? = null,
        auth: Map<String, String>? = null,
        verify: Boolean = true,
        http3: Boolean = false,
        stealthyHeaders: Boolean = true,
    ): ResponseModel {
        val response = staticExecutor.execute(
            method = "GET",
            url = url,
            options = RequestOptions(
                params = params,
                headers = headers,
                cookies = cookies,
                impersonate = impersonate,
                timeout = timeout,
                followRedirects = followRedirects,
                maxRedirects = maxRedirects,
                retries = retries,
                retryDelay = retryDelay,
                proxy = proxy,
                proxyAuth = proxyAuth,
                auth = auth,
                verify = verify,
                http3 = http3,
                stealthyHeaders = stealthyHeaders,
            ),
        )
        return responseModel(response, extractionType, cssSelector, mainContentOnly)
    }

    suspend fun bulkGet(
        urls: Collection<String>,
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        cssSelector: String? = null,
        mainContentOnly: Boolean = true,
    ): List<ResponseModel> = coroutineScope {
        urls.map { url ->
            async {
                get(
                    url = url,
                    extractionType = extractionType,
                    cssSelector = cssSelector,
                    mainContentOnly = mainContentOnly,
                )
            }
        }.awaitAll()
    }

    suspend fun fetch(
        url: String,
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        cssSelector: String? = null,
        mainContentOnly: Boolean = true,
        headless: Boolean = true,
        disableResources: Boolean = false,
        networkIdle: Boolean = false,
        timeout: Double = 30_000.0,
        wait: Double? = null,
        waitSelector: String? = null,
        locale: String? = null,
        realChrome: Boolean = false,
        proxy: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ResponseModel = browserFetch(
        mode = BrowserToolMode.DYNAMIC,
        url = url,
        extractionType = extractionType,
        cssSelector = cssSelector,
        mainContentOnly = mainContentOnly,
        options = BrowserFetchOptions(
            headless = headless,
            disableResources = disableResources,
            networkIdle = networkIdle,
            timeout = timeout,
            wait = wait,
            waitSelector = waitSelector,
            locale = locale,
            realChrome = realChrome,
            proxy = proxy?.let(::BrowserProxyUrl),
            extraHeaders = extraHeaders,
        ),
    )

    suspend fun bulkFetch(
        urls: Collection<String>,
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        headless: Boolean = true,
    ): List<ResponseModel> = coroutineScope {
        urls.map { url -> async { fetch(url, extractionType = extractionType, headless = headless) } }.awaitAll()
    }

    suspend fun stealthyFetch(
        url: String,
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        cssSelector: String? = null,
        mainContentOnly: Boolean = true,
        headless: Boolean = true,
        disableResources: Boolean = false,
        blockWebRtc: Boolean = true,
        solveCloudflare: Boolean = false,
        allowWebgl: Boolean = true,
        networkIdle: Boolean = false,
        realChrome: Boolean = false,
        hideCanvas: Boolean = false,
        timeout: Double = 30_000.0,
        wait: Double? = null,
        waitSelector: String? = null,
        proxy: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ResponseModel = browserFetch(
        mode = BrowserToolMode.STEALTHY,
        url = url,
        extractionType = extractionType,
        cssSelector = cssSelector,
        mainContentOnly = mainContentOnly,
        options = BrowserFetchOptions(
            headless = headless,
            disableResources = disableResources,
            blockWebRtc = blockWebRtc,
            solveCloudflare = solveCloudflare,
            allowWebgl = allowWebgl,
            networkIdle = networkIdle,
            realChrome = realChrome,
            hideCanvas = hideCanvas,
            timeout = timeout,
            wait = wait,
            waitSelector = waitSelector,
            proxy = proxy?.let(::BrowserProxyUrl),
            extraHeaders = extraHeaders,
        ),
    )

    suspend fun bulkStealthyFetch(
        urls: Collection<String>,
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        headless: Boolean = true,
    ): List<ResponseModel> = coroutineScope {
        urls.map { url -> async { stealthyFetch(url, extractionType = extractionType, headless = headless) } }.awaitAll()
    }

    fun toolDescriptors(): List<ToolDescriptor> = listOf(
        ToolDescriptor("get", "Make GET HTTP request to a URL and return structured output."),
        ToolDescriptor("bulk_get", "Make GET HTTP requests to multiple URLs and return structured output."),
        ToolDescriptor("fetch", "Open a browser and fetch structured content using the dynamic fetcher."),
        ToolDescriptor("bulk_fetch", "Open a browser and fetch structured content from multiple URLs."),
        ToolDescriptor("stealthy_fetch", "Open a stealth browser and fetch structured content."),
        ToolDescriptor("bulk_stealthy_fetch", "Open a stealth browser and fetch structured content from multiple URLs."),
    )

    fun serve(
        http: Boolean,
        host: String,
        port: Int,
        sink: (String) -> Unit = ::println,
    ): McpLaunchResult {
        val launch = McpLaunchResult(
            transport = if (http) "streamable-http" else "stdio",
            host = host,
            port = port,
            tools = toolDescriptors(),
        )
        runtime.serve(serverName = "Scrapling", launch = launch, handler = protocolHandler("Scrapling", launch), sink = sink)
        return launch
    }

    private fun protocolHandler(serverName: String, launch: McpLaunchResult): McpProtocolHandler = McpProtocolHandler { rawRequest ->
        val requestText = rawRequest.trimStart('\uFEFF').trim()
        if (requestText.isBlank()) {
            return@McpProtocolHandler null
        }

        val request = runCatching { mcpJson.parseToJsonElement(requestText).jsonObject }
            .getOrElse { error ->
                return@McpProtocolHandler jsonRpcError(id = null, code = -32700, message = error.message ?: "Invalid JSON")
            }

        val method = request["method"]?.jsonPrimitive?.contentOrNull
            ?: return@McpProtocolHandler jsonRpcError(request["id"], -32600, "Missing JSON-RPC method")

        val id = request["id"]
        when (method) {
            "initialize" -> {
                if (id == null) {
                    return@McpProtocolHandler jsonRpcError(null, -32600, "initialize requires an id")
                }
                return@McpProtocolHandler jsonRpcResult(
                    id = id,
                    result = McpInitializeResult(
                        protocolVersion = MCP_PROTOCOL_VERSION,
                        capabilities = McpServerCapabilities(),
                        serverInfo = McpServerInfo(name = serverName, version = MCP_SERVER_VERSION),
                    ).toJsonElement(),
                )
            }

            "notifications/initialized" -> return@McpProtocolHandler null

            "tools/list" -> {
                if (id == null) {
                    return@McpProtocolHandler jsonRpcError(null, -32600, "tools/list requires an id")
                }
                val tools = launch.tools.map(ToolDescriptor::toMcpTool)
                return@McpProtocolHandler jsonRpcResult(id, McpToolListResult(tools).toJsonElement())
            }

            "tools/call" -> {
                if (id == null) {
                    return@McpProtocolHandler jsonRpcError(null, -32600, "tools/call requires an id")
                }
                return@McpProtocolHandler handleToolCall(id, request["params"])
            }

            else -> return@McpProtocolHandler jsonRpcError(id, -32601, "Method not found: $method")
        }
    }

    private fun handleToolCall(id: JsonElement, params: JsonElement?): String {
        val paramsObject = params?.jsonObject ?: return jsonRpcError(id, -32602, "tools/call params must be an object")
        val toolName = paramsObject["name"]?.jsonPrimitive?.contentOrNull
            ?: return jsonRpcError(id, -32602, "tools/call requires a tool name")
        val arguments = paramsObject["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        val result = runCatching { executeTool(toolName, arguments) }
            .getOrElse { error ->
                McpToolCallResult(
                    content = listOf(McpTextContent(text = error.message ?: "Tool execution failed")),
                    structuredContent = JsonNull,
                    isError = true,
                )
            }

        return jsonRpcResult(id, result.toJsonElement())
    }

    private fun executeTool(name: String, arguments: JsonObject): McpToolCallResult = when (name) {
        "get" -> responseToToolResult(
            get(
                url = arguments.requiredString("url"),
                impersonate = arguments.impersonation("impersonate"),
                extractionType = arguments.extractionType("extractionType", ExtractionType.MARKDOWN),
                cssSelector = arguments.optionalString("cssSelector"),
                mainContentOnly = arguments.optionalBoolean("mainContentOnly", true),
                params = arguments.optionalStringMap("params"),
                headers = arguments.optionalStringMap("headers"),
                cookies = arguments.optionalStringMap("cookies"),
                timeout = arguments.optionalInt("timeout", 30),
                followRedirects = arguments.optionalBoolean("followRedirects", true),
                maxRedirects = arguments.optionalInt("maxRedirects", 30),
                retries = arguments.optionalInt("retries", 3),
                retryDelay = arguments.optionalInt("retryDelay", 1),
                proxy = arguments.optionalString("proxy"),
                proxyAuth = arguments.optionalNullableStringMap("proxyAuth"),
                auth = arguments.optionalNullableStringMap("auth"),
                verify = arguments.optionalBoolean("verify", true),
                http3 = arguments.optionalBoolean("http3", false),
                stealthyHeaders = arguments.optionalBoolean("stealthyHeaders", true),
            ),
        )

        "bulk_get" -> responseToToolResult(
            runBlocking {
                bulkGet(
                    urls = arguments.requiredStringList("urls"),
                    extractionType = arguments.extractionType("extractionType", ExtractionType.MARKDOWN),
                    cssSelector = arguments.optionalString("cssSelector"),
                    mainContentOnly = arguments.optionalBoolean("mainContentOnly", true),
                )
            },
        )

        "fetch" -> responseToToolResult(
            runBlocking {
                fetch(
                    url = arguments.requiredString("url"),
                    extractionType = arguments.extractionType("extractionType", ExtractionType.MARKDOWN),
                    cssSelector = arguments.optionalString("cssSelector"),
                    mainContentOnly = arguments.optionalBoolean("mainContentOnly", true),
                    headless = arguments.optionalBoolean("headless", true),
                    disableResources = arguments.optionalBoolean("disableResources", false),
                    networkIdle = arguments.optionalBoolean("networkIdle", false),
                    realChrome = arguments.optionalBoolean("realChrome", false),
                    timeout = arguments.optionalDouble("timeout", 30_000.0),
                    wait = arguments.optionalNullableDouble("wait"),
                    waitSelector = arguments.optionalString("waitSelector"),
                    proxy = arguments.optionalString("proxy"),
                    extraHeaders = arguments.optionalStringMap("extraHeaders"),
                )
            },
        )

        "bulk_fetch" -> responseToToolResult(
            runBlocking {
                bulkFetch(
                    urls = arguments.requiredStringList("urls"),
                    extractionType = arguments.extractionType("extractionType", ExtractionType.MARKDOWN),
                    headless = arguments.optionalBoolean("headless", true),
                )
            },
        )

        "stealthy_fetch" -> responseToToolResult(
            runBlocking {
                stealthyFetch(
                    url = arguments.requiredString("url"),
                    extractionType = arguments.extractionType("extractionType", ExtractionType.MARKDOWN),
                    cssSelector = arguments.optionalString("cssSelector"),
                    mainContentOnly = arguments.optionalBoolean("mainContentOnly", true),
                    headless = arguments.optionalBoolean("headless", true),
                    disableResources = arguments.optionalBoolean("disableResources", false),
                    networkIdle = arguments.optionalBoolean("networkIdle", false),
                    realChrome = arguments.optionalBoolean("realChrome", false),
                    blockWebRtc = arguments.optionalBoolean("blockWebRtc", false),
                    solveCloudflare = arguments.optionalBoolean("solveCloudflare", false),
                    hideCanvas = arguments.optionalBoolean("hideCanvas", false),
                    allowWebgl = arguments.optionalBoolean("allowWebgl", true),
                    timeout = arguments.optionalDouble("timeout", 30_000.0),
                    wait = arguments.optionalNullableDouble("wait"),
                    waitSelector = arguments.optionalString("waitSelector"),
                    proxy = arguments.optionalString("proxy"),
                    extraHeaders = arguments.optionalStringMap("extraHeaders"),
                )
            },
        )

        "bulk_stealthy_fetch" -> responseToToolResult(
            runBlocking {
                bulkStealthyFetch(
                    urls = arguments.requiredStringList("urls"),
                    extractionType = arguments.extractionType("extractionType", ExtractionType.MARKDOWN),
                    headless = arguments.optionalBoolean("headless", true),
                )
            },
        )

        else -> error("Unsupported tool: $name")
    }

    private suspend fun browserFetch(
        mode: BrowserToolMode,
        url: String,
        extractionType: ExtractionType,
        cssSelector: String?,
        mainContentOnly: Boolean,
        options: BrowserFetchOptions,
    ): ResponseModel {
        val response = browserExecutor.execute(mode, url, options)
        return responseModel(response, extractionType, cssSelector, mainContentOnly)
    }

    private fun responseModel(
        response: Response,
        extractionType: ExtractionType,
        cssSelector: String?,
        mainContentOnly: Boolean,
    ): ResponseModel = ResponseModel(
        status = response.status,
        content = ContentExtractor.extract(response, extractionType, cssSelector, mainContentOnly),
        url = response.url,
    )
}

package io.github.d4vinci.scrapling.ai

import io.github.d4vinci.scrapling.core.shell.ContentExtractor
import io.github.d4vinci.scrapling.core.shell.ExtractionType
import io.github.d4vinci.scrapling.fetchers.browser.AsyncDynamicFetcher
import io.github.d4vinci.scrapling.fetchers.browser.AsyncStealthyFetcher
import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.browser.BrowserProxyUrl
import io.github.d4vinci.scrapling.fetchers.static.FetcherClient
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class ResponseModel(
    val status: Int,
    val content: List<String>,
    val url: String,
)

data class ToolDescriptor(
    val name: String,
    val description: String,
)

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

class ScraplingMcpServer(
    private val staticExecutor: StaticToolExecutor = DefaultStaticToolExecutor(),
    private val browserExecutor: AsyncBrowserToolExecutor = DefaultAsyncBrowserToolExecutor(),
) {
    fun get(
        url: String,
        extractionType: ExtractionType = ExtractionType.MARKDOWN,
        cssSelector: String? = null,
        mainContentOnly: Boolean = true,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        timeout: Int = 30,
        followRedirects: Boolean = true,
        retries: Int = 3,
        proxy: String? = null,
        stealthyHeaders: Boolean = true,
    ): ResponseModel {
        val response = staticExecutor.execute(
            method = "GET",
            url = url,
            options = RequestOptions(
                params = params,
                headers = headers,
                cookies = cookies,
                timeout = timeout,
                retries = retries,
                proxy = proxy,
                followRedirects = followRedirects,
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
        urls.map { url -> async { get(url, extractionType, cssSelector, mainContentOnly) } }.awaitAll()
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
        sink("Scrapling MCP server ready via ${launch.transport} on ${launch.host}:${launch.port}")
        return launch
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

private class DefaultStaticToolExecutor : StaticToolExecutor {
    private val client = FetcherClient()

    override fun execute(method: String, url: String, options: RequestOptions): Response = when (method.uppercase()) {
        "GET" -> client.get(url, options)
        "POST" -> client.post(url, options.data, options)
        "PUT" -> client.put(url, options.data, options)
        "DELETE" -> client.delete(url, options)
        else -> error("Unsupported method: $method")
    }
}

private class DefaultAsyncBrowserToolExecutor : AsyncBrowserToolExecutor {
    override suspend fun execute(mode: BrowserToolMode, url: String, options: BrowserFetchOptions): Response = when (mode) {
        BrowserToolMode.DYNAMIC -> AsyncDynamicFetcher.fetch(url, options)
        BrowserToolMode.STEALTHY -> AsyncStealthyFetcher.fetch(url, options)
    }
}

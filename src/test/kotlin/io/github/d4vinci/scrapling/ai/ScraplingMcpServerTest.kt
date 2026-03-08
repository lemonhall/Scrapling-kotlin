package io.github.d4vinci.scrapling.ai

import io.github.d4vinci.scrapling.core.shell.ExtractionType
import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScraplingMcpServerTest {
    @Test
    fun toolDescriptorsExposeExpectedTools() {
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
        )

        val names = server.toolDescriptors().map(ToolDescriptor::name)

        assertEquals(
            listOf("get", "bulk_get", "fetch", "bulk_fetch", "stealthy_fetch", "bulk_stealthy_fetch"),
            names,
        )
    }

    @Test
    fun getReturnsStructuredResponseModel() {
        lateinit var capturedOptions: RequestOptions
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, options ->
                capturedOptions = options
                htmlResponse(url)
            },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
        )

        val result = server.get(
            url = "https://example.com/page",
            extractionType = ExtractionType.MARKDOWN,
            cssSelector = "h1",
            headers = mapOf("User-Agent" to "Test"),
            cookies = mapOf("session" to "abc"),
            params = mapOf("page" to "1"),
        )

        assertEquals(200, result.status)
        assertEquals("https://example.com/page", result.url)
        assertTrue(result.content.single().contains("Title"))
        assertEquals("Test", capturedOptions.headers["User-Agent"])
        assertEquals("abc", capturedOptions.cookies["session"])
        assertEquals("1", capturedOptions.params["page"])
    }

    @Test
    fun bulkGetReturnsMultipleResponses() = runTest {
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
        )

        val result = server.bulkGet(listOf("https://example.com/1", "https://example.com/2"), ExtractionType.HTML)

        assertEquals(2, result.size)
        assertTrue(result.all { response -> response.status == 200 })
    }

    @Test
    fun fetchUsesDynamicBrowserModeAndMapsOptions() = runTest {
        var capturedMode: BrowserToolMode? = null
        lateinit var capturedOptions: BrowserFetchOptions
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { mode, url, options ->
                capturedMode = mode
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val result = server.fetch(
            url = "https://example.com/page",
            extractionType = ExtractionType.TEXT,
            headless = false,
            disableResources = true,
            proxy = "http://proxy:8080",
        )

        assertEquals(BrowserToolMode.DYNAMIC, capturedMode)
        assertEquals(200, result.status)
        assertTrue(result.content.single().contains("Title Text content"))
        assertEquals(false, capturedOptions.headless)
        assertTrue(capturedOptions.disableResources)
        assertEquals("http://proxy:8080", (capturedOptions.proxy as io.github.d4vinci.scrapling.fetchers.browser.BrowserProxyUrl).value)
    }

    @Test
    fun stealthyFetchUsesStealthMode() = runTest {
        var capturedMode: BrowserToolMode? = null
        lateinit var capturedOptions: BrowserFetchOptions
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { mode, url, options ->
                capturedMode = mode
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val result = server.stealthyFetch(
            url = "https://example.com/page",
            extractionType = ExtractionType.TEXT,
            hideCanvas = true,
            solveCloudflare = true,
        )

        assertEquals(BrowserToolMode.STEALTHY, capturedMode)
        assertEquals(200, result.status)
        assertTrue(capturedOptions.hideCanvas)
        assertTrue(capturedOptions.solveCloudflare)
    }

    @Test
    fun serveReturnsLaunchDescriptor() {
        val output = mutableListOf<String>()
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
        )

        val launch = server.serve(http = true, host = "127.0.0.1", port = 8123, sink = output::add)

        assertEquals("streamable-http", launch.transport)
        assertEquals(6, launch.tools.size)
        assertTrue(output.single().contains("127.0.0.1:8123"))
    }

    private fun htmlResponse(url: String): Response = Response(
        url = url,
        content = "<html><body><div class='content'><h1>Title</h1><p>Text content</p></div></body></html>".toByteArray(Charsets.UTF_8),
        status = 200,
        reason = "OK",
        cookies = emptyMap(),
        headers = emptyMap(),
        requestHeaders = emptyMap(),
        method = "GET",
    )
}

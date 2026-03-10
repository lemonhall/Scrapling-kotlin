package io.github.d4vinci.scrapling.ai

import io.github.d4vinci.scrapling.core.shell.ExtractionType
import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.static.Impersonation
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun getMapsHighFrequencyStaticOptions() {
        lateinit var capturedOptions: RequestOptions
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, options ->
                capturedOptions = options
                htmlResponse(url)
            },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
        )

        server.get(
            url = "https://example.com/page",
            impersonate = Impersonation.Multiple(listOf("chrome", "firefox")),
            maxRedirects = 12,
            retries = 4,
            retryDelay = 2,
            proxy = "http://proxy:8080",
            proxyAuth = mapOf("username" to "proxy-user", "password" to "proxy-pass"),
            auth = mapOf("username" to "api-user", "password" to "api-pass"),
            verify = false,
            http3 = true,
            stealthyHeaders = false,
        )

        assertEquals(Impersonation.Multiple(listOf("chrome", "firefox")), capturedOptions.impersonate)
        assertEquals(12, capturedOptions.maxRedirects)
        assertEquals(4, capturedOptions.retries)
        assertEquals(2, capturedOptions.retryDelay)
        assertEquals("http://proxy:8080", capturedOptions.proxy)
        assertEquals(mapOf("username" to "proxy-user", "password" to "proxy-pass"), capturedOptions.proxyAuth)
        assertEquals(mapOf("username" to "api-user", "password" to "api-pass"), capturedOptions.auth)
        assertEquals(false, capturedOptions.verify)
        assertEquals(true, capturedOptions.http3)
        assertEquals(false, capturedOptions.stealthyHeaders)
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
            runtime = McpServerRuntime { _, launch, _, sink ->
                sink("runtime ${launch.transport} ${launch.host}:${launch.port}")
            },
        )

        val launch = server.serve(http = true, host = "127.0.0.1", port = 8123, sink = output::add)

        assertEquals("streamable-http", launch.transport)
        assertEquals(6, launch.tools.size)
        assertTrue(output.single().contains("127.0.0.1:8123"))
    }

    @Test
    fun serveDelegatesLaunchToRuntimeWithRegisteredTools() {
        var capturedServerName = ""
        var capturedLaunch: McpLaunchResult? = null
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
            runtime = McpServerRuntime { serverName, launch, _, _ ->
                capturedServerName = serverName
                capturedLaunch = launch
            },
        )

        val launch = server.serve(http = false, host = "127.0.0.1", port = 9000)

        assertEquals("Scrapling", capturedServerName)
        assertEquals(launch, capturedLaunch)
        assertEquals("stdio", capturedLaunch?.transport)
        assertEquals(listOf("get", "bulk_get", "fetch", "bulk_fetch", "stealthy_fetch", "bulk_stealthy_fetch"), capturedLaunch?.tools?.map(ToolDescriptor::name))
    }

    @Test
    fun httpRuntimeServesJsonRpcToolListUntilInterrupted() {
        val port = ServerSocket(0).use { it.localPort }
        val ready = mutableListOf<String>()
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
            runtime = JdkMcpServerRuntime(),
        )
        val thread = Thread {
            server.serve(http = true, host = "127.0.0.1", port = port, sink = ready::add)
        }

        thread.start()
        val body = eventuallyPost(
            url = "http://127.0.0.1:$port/mcp",
            payload = """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""",
        )

        assertTrue(body.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(body.contains("\"tools\""))
        assertTrue(body.contains("\"name\":\"get\""))
        assertTrue(ready.single().contains("127.0.0.1:$port"))

        thread.interrupt()
        thread.join(5_000)
        assertFalse(thread.isAlive)
    }

    @Test
    fun stdioRuntimeHandlesInitializeToolListAndToolCallWithBomInput() {
        val output = ByteArrayOutputStream()
        val server = ScraplingMcpServer(
            staticExecutor = StaticToolExecutor { _, url, _ -> htmlResponse(url) },
            browserExecutor = AsyncBrowserToolExecutor { _, url, _ -> htmlResponse(url) },
            runtime = JdkMcpServerRuntime(
                input = ByteArrayInputStream(
                    (
                        "\uFEFF{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}\n" +
                            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}\n" +
                            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n" +
                            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get\",\"arguments\":{\"url\":\"https://example.com/page\",\"extractionType\":\"text\"}}}\n"
                    ).toByteArray(Charsets.UTF_8),
                ),
                output = output,
            ),
        )

        server.serve(http = false, host = "127.0.0.1", port = 0, sink = {})

        val text = output.toString(Charsets.UTF_8)
        assertTrue(text.contains("\"protocolVersion\":\"2025-06-18\""))
        assertTrue(text.contains("\"serverInfo\""))
        assertTrue(text.contains("\"tools\""))
        assertTrue(text.contains("\"name\":\"get\""))
        assertTrue(text.contains("\"structuredContent\""))
        assertTrue(text.contains("\"url\":\"https://example.com/page\""))
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

    private fun eventuallyRequest(url: String): String {
        val client = HttpClient.newHttpClient()
        repeat(40) {
            val body = runCatching {
                client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            }.getOrNull()?.takeIf { it.statusCode() == 200 }?.body()
            if (body != null) {
                return body
            }
            Thread.sleep(100)
        }
        error("HTTP runtime did not become ready for $url")
    }

    private fun eventuallyPost(url: String, payload: String): String {
        repeat(40) {
            val body = runCatching {
                val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
            if (body != null) {
                return body
            }
            Thread.sleep(100)
        }
        error("HTTP runtime did not become ready for $url")
    }
}

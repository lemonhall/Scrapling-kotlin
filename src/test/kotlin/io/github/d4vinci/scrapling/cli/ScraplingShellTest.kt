package io.github.d4vinci.scrapling.cli

import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScraplingShellTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun namespaceContainsExpectedHelpers() {
        val shell = ScraplingShell()

        assertTrue(shell.namespace().containsKey("get"))
        assertTrue(shell.namespace().containsKey("post"))
        assertTrue(shell.namespace().containsKey("put"))
        assertTrue(shell.namespace().containsKey("delete"))
        assertTrue(shell.namespace().containsKey("fetch"))
        assertTrue(shell.namespace().containsKey("stealthy_fetch"))
        assertTrue(shell.namespace().containsKey("page"))
        assertTrue(shell.namespace().containsKey("response"))
        assertTrue(shell.namespace().containsKey("pages"))
        assertTrue(shell.namespace().containsKey("uncurl"))
        assertTrue(shell.namespace().containsKey("curl2fetcher"))
        assertTrue(shell.namespace().containsKey("help"))
    }

    @Test
    fun uncurlCommandPrintsParsedRequestJson() {
        val shell = ScraplingShell()

        val payload = json.parseToJsonElement(shell.execute("""uncurl("curl https://example.com")""")).jsonObject

        assertEquals("get", payload["method"]?.jsonPrimitive?.content)
        assertEquals("https://example.com", payload["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun curl2fetcherExecutesParsedRequestThroughInjectedExecutor() {
        var capturedMethod = ""
        var capturedUrl = ""
        lateinit var capturedOptions: RequestOptions
        val shell = ScraplingShell(
            staticExecutor = { method, url, options ->
                capturedMethod = method
                capturedUrl = url
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val payload = json.parseToJsonElement(
            shell.execute("""curl2fetcher("curl https://example.com/form -X POST -H 'User-Agent: TestAgent/1.0' -d 'foo=bar'")"""),
        ).jsonObject

        assertEquals("POST", capturedMethod)
        assertEquals("https://example.com/form", capturedUrl)
        assertEquals("TestAgent/1.0", capturedOptions.headers["User-Agent"])
        assertEquals("bar", capturedOptions.data["foo"], "captured data = ${capturedOptions.data}")
        assertEquals("https://example.com/form", payload["url"]?.jsonPrimitive?.content)
        assertEquals("200", payload["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun helpCommandListsShellHelpers() {
        val shell = ScraplingShell()

        val help = shell.execute("help()")

        assertTrue(help.contains("uncurl"))
        assertTrue(help.contains("curl2fetcher"))
        assertTrue(help.contains("namespace"))
    }

    @Test
    fun getCommandExecutesInjectedStaticExecutorAndTracksCurrentPage() {
        var capturedMethod = ""
        var capturedUrl = ""
        val shell = ScraplingShell(
            staticExecutor = { method, url, _ ->
                capturedMethod = method
                capturedUrl = url
                htmlResponse(url = url, status = 201, reason = "Created", method = method)
            },
        )

        val payload = json.parseToJsonElement(shell.execute("""get("https://example.com/articles")""")).jsonObject

        assertEquals("GET", capturedMethod)
        assertEquals("https://example.com/articles", capturedUrl)
        assertEquals("https://example.com/articles", payload["url"]?.jsonPrimitive?.content)
        assertEquals("201", payload["status"]?.jsonPrimitive?.content)
        assertEquals("https://example.com/articles", shell.execute("page.url"))
        assertEquals("201", shell.execute("response.status"))
        assertEquals("1", shell.execute("len(pages)"))
    }

    @Test
    fun fetchAndStealthyFetchUseInjectedBrowserExecutorAndTrackHistory() {
        val modes = mutableListOf<BrowserFetchMode>()
        val shell = ScraplingShell(
            browserExecutor = { mode, url, _ ->
                modes += mode
                htmlResponse(url = url, status = if (mode == BrowserFetchMode.DYNAMIC) 202 else 203, reason = "OK", method = mode.name)
            },
        )

        shell.execute("""fetch("https://example.com/dynamic")""")
        shell.execute("""stealthy_fetch("https://example.com/stealth")""")

        assertEquals(listOf(BrowserFetchMode.DYNAMIC, BrowserFetchMode.STEALTHY), modes)
        assertEquals("https://example.com/stealth", shell.execute("page.url"))
        assertEquals("https://example.com/dynamic", shell.execute("pages[0].url"))
        assertEquals("https://example.com/stealth", shell.execute("pages[-1].url"))
    }

    @Test
    fun pagesHistoryKeepsOnlyLastFiveResponses() {
        val shell = ScraplingShell(
            staticExecutor = { method, url, _ -> htmlResponse(url = url, method = method) },
        )

        repeat(6) { index ->
            shell.execute("""get("https://example.com/$index")""")
        }

        val pages = json.parseToJsonElement(shell.execute("pages")).jsonArray

        assertEquals(5, pages.size)
        assertEquals("https://example.com/1", pages.first().jsonObject["url"]?.jsonPrimitive?.content)
        assertEquals("https://example.com/5", pages.last().jsonObject["url"]?.jsonPrimitive?.content)
        assertEquals("5", shell.execute("len(pages)"))
    }

    @Test
    fun viewCommandUsesInjectedViewerForCurrentPage() {
        val shell = ScraplingShell(
            staticExecutor = { method, url, _ -> htmlResponse(url = url, method = method) },
            viewer = { response -> "viewed:${response.url}" },
        )

        shell.execute("""get("https://example.com/view")""")

        assertEquals("viewed:https://example.com/view", shell.execute("view(page)"))
    }

    private fun htmlResponse(
        url: String,
        status: Int = 200,
        reason: String = "OK",
        method: String = "GET",
    ): Response = Response(
        url = url,
        content = "<html><body><h1>Shell</h1></body></html>".toByteArray(Charsets.UTF_8),
        status = status,
        reason = reason,
        cookies = emptyMap(),
        headers = emptyMap(),
        requestHeaders = emptyMap(),
        method = method,
    )
}

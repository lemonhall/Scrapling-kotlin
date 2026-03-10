package io.github.d4vinci.scrapling.cli

import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.static.Impersonation
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScraplingCliStaticExtractTest {
    @Test
    fun extractGetWritesMarkdownFileAndParsesStaticOptions() {
        val output = mutableListOf<String>()
        val tempFile = Files.createTempFile("scrapling-cli", ".md")
        lateinit var capturedOptions: RequestOptions
        var capturedMethod = ""
        val cli = ScraplingCli(
            out = output::add,
            err = output::add,
            staticExecutor = StaticExtractExecutor { method, url, options ->
                capturedMethod = method
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val exitCode = cli.run(
            listOf(
                "extract", "get", "https://example.com/page", tempFile.toString(),
                "-H", "User-Agent: Test",
                "--cookies", "session=abc123",
                "-p", "page=1",
                "-s", "h1",
            ),
        )

        assertEquals(0, exitCode)
        assertEquals("GET", capturedMethod)
        assertEquals("Test", capturedOptions.headers["User-Agent"])
        assertEquals("abc123", capturedOptions.cookies["session"])
        assertEquals("1", capturedOptions.params["page"])
        assertTrue(tempFile.toFile().readText(Charsets.UTF_8).contains("Title"))
        assertTrue(output.joinToString("\n").contains("Content successfully saved"))
    }

    @Test
    fun extractGetParsesSingleImpersonateOption() {
        val tempFile = Files.createTempFile("scrapling-cli", ".md")
        lateinit var capturedOptions: RequestOptions
        val cli = ScraplingCli(
            out = {},
            err = {},
            staticExecutor = StaticExtractExecutor { _, url, options ->
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val exitCode = cli.run(
            listOf(
                "extract", "get", "https://example.com/page", tempFile.toString(),
                "--impersonate", "chrome",
            ),
        )

        assertEquals(0, exitCode)
        val impersonation = capturedOptions.impersonate
        assertTrue(impersonation is Impersonation.Single)
        assertEquals("chrome", impersonation.browser)
    }

    @Test
    fun extractGetParsesCommaSeparatedImpersonateOption() {
        val tempFile = Files.createTempFile("scrapling-cli", ".md")
        lateinit var capturedOptions: RequestOptions
        val cli = ScraplingCli(
            out = {},
            err = {},
            staticExecutor = StaticExtractExecutor { _, url, options ->
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val exitCode = cli.run(
            listOf(
                "extract", "get", "https://example.com/page", tempFile.toString(),
                "--impersonate", "chrome,firefox,safari",
            ),
        )

        assertEquals(0, exitCode)
        val impersonation = capturedOptions.impersonate
        assertTrue(impersonation is Impersonation.Multiple)
        assertEquals(listOf("chrome", "firefox", "safari"), impersonation.browsers)
    }

    @Test
    fun extractPostWritesHtmlFile() {
        val output = mutableListOf<String>()
        val tempFile = Files.createTempFile("scrapling-cli", ".html")
        lateinit var capturedOptions: RequestOptions
        var capturedMethod = ""
        val cli = ScraplingCli(
            out = output::add,
            err = output::add,
            staticExecutor = StaticExtractExecutor { method, url, options ->
                capturedMethod = method
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val exitCode = cli.run(
            listOf(
                "extract", "post", "https://example.com/form", tempFile.toString(),
                "-d", "key=value",
                "-j", "{\"data\":\"test\"}",
            ),
        )

        assertEquals(0, exitCode)
        assertEquals("POST", capturedMethod)
        assertEquals("value", capturedOptions.data["key"])
        assertEquals("{\"data\":\"test\"}", capturedOptions.json)
        assertTrue(tempFile.toFile().readText(Charsets.UTF_8).contains("<h1>Title</h1>"))
    }

    @Test
    fun extractFetchWritesTextFileAndMapsBrowserOptions() {
        val output = mutableListOf<String>()
        val tempFile = Files.createTempFile("scrapling-cli", ".txt")
        lateinit var capturedOptions: BrowserFetchOptions
        var capturedMode: BrowserFetchMode? = null
        val cli = ScraplingCli(
            out = output::add,
            err = output::add,
            browserExecutor = BrowserExtractExecutor { mode, url, options ->
                capturedMode = mode
                capturedOptions = options
                htmlResponse(url)
            },
        )

        val exitCode = cli.run(
            listOf(
                "extract", "fetch", "https://example.com/page", tempFile.toString(),
                "--headful",
                "--disable-resources",
                "--network-idle",
                "--timeout", "1234",
                "--wait", "321",
                "-s", ".content",
                "-H", "Referer: https://google.com",
            ),
        )

        assertEquals(0, exitCode)
        assertEquals(BrowserFetchMode.DYNAMIC, capturedMode)
        assertFalse(capturedOptions.headless)
        assertTrue(capturedOptions.disableResources)
        assertTrue(capturedOptions.networkIdle)
        assertEquals(1234.0, capturedOptions.timeout)
        assertEquals(321.0, capturedOptions.wait)
        assertEquals("https://google.com", capturedOptions.extraHeaders["Referer"])
        val content = tempFile.toFile().readText(Charsets.UTF_8)
        assertTrue(content.contains("Text content"))
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


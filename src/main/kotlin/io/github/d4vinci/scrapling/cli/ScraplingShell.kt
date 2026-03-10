package io.github.d4vinci.scrapling.cli

import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.browser.DynamicFetcher
import io.github.d4vinci.scrapling.fetchers.browser.StealthyFetcher
import io.github.d4vinci.scrapling.fetchers.static.FetcherClient
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.awt.Desktop
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ScraplingShell(
    private val code: String = "",
    private val logLevel: String = "debug",
    private val sink: (String) -> Unit = ::println,
    private val curlParser: CurlCommandParser = CurlCommandParser(),
    private val staticExecutor: (String, String, RequestOptions) -> Response = defaultStaticExecutor(),
    private val browserExecutor: (BrowserFetchMode, String, BrowserFetchOptions) -> Response = defaultBrowserExecutor(),
    private val viewer: (Response) -> String = defaultViewer(),
) {
    var page: Response? = null
        private set

    val response: Response?
        get() = page

    val pages: MutableList<Response> = mutableListOf()

    fun namespace(): Map<String, String> = linkedMapOf(
        "get" to "Static GET request helper",
        "post" to "Static POST request helper",
        "put" to "Static PUT request helper",
        "delete" to "Static DELETE request helper",
        "fetch" to "Browser dynamic fetch helper",
        "stealthy_fetch" to "Browser stealth fetch helper",
        "post" to "Static POST request helper",
        "Fetcher" to "Static fetcher client",
        "AsyncFetcher" to "Async static fetcher client",
        "FetcherSession" to "Static fetcher session helper",
        "DynamicFetcher" to "Browser dynamic fetch helper",
        "DynamicSession" to "Browser dynamic session helper",
        "AsyncDynamicSession" to "Async browser dynamic session helper",
        "StealthyFetcher" to "Browser stealth fetch helper",
        "StealthySession" to "Browser stealth session helper",
        "AsyncStealthySession" to "Async browser stealth session helper",
        "page" to "The most recent response object",
        "response" to "Alias of page",
        "pages" to "The last five response objects",
        "view" to "Open local content for inspection",
        "uncurl" to "Convert a curl command into a request",
        "curl2fetcher" to "Execute a curl command through the static fetcher",
        "help" to "Show shell helper summary",
    )

    fun start() {
        if (code.isBlank()) {
            sink("Scrapling shell started (log level: ${logLevel.lowercase()})")
            sink("Available helpers: ${namespace().keys.joinToString(", ")}")
            return
        }
        val result = execute(code)
        if (result.isNotBlank()) {
            sink(result)
        }
    }

    fun execute(command: String): String = runCatching {
        splitStatements(command.trim()).fold("") { _, statement ->
            executeInternal(statement)
        }
    }.getOrElse { error ->
        "Error: ${error.message ?: error::class.simpleName.orEmpty()}"
    }

    private fun executeInternal(command: String): String = when {
        command.isBlank() -> ""
        command == "namespace" || command == "namespace()" -> namespace().keys.joinToString(System.lineSeparator())
        command == "help" || command == "help()" -> helpText()
        command == "page" || command == "response" -> currentPage().toShellJson()
        command == "pages" -> shellJson.encodeToString(pages.map(Response::toShellResult))
        command == "pages.size" || command == "len(pages)" -> pages.size.toString()
        command.startsWith("page.") -> readPageProperty(currentPage(), command.removePrefix("page."))
        command.startsWith("response.") -> readPageProperty(currentPage(), command.removePrefix("response."))
        pageIndexPattern.matches(command) -> readHistoryProperty(command)
        command.startsWith("view(") -> executeView(command)
        command.startsWith("uncurl(") -> shellJson.encodeToString(curlParser.parse(readSingleArgument(command, "uncurl")))
        command.startsWith("get(") -> executeStaticHelper("GET", command)
        command.startsWith("post(") -> executeStaticHelper("POST", command)
        command.startsWith("put(") -> executeStaticHelper("PUT", command)
        command.startsWith("delete(") -> executeStaticHelper("DELETE", command)
        command.startsWith("fetch(") -> executeBrowserHelper(BrowserFetchMode.DYNAMIC, command, "fetch")
        command.startsWith("stealthy_fetch(") -> executeBrowserHelper(BrowserFetchMode.STEALTHY, command, "stealthy_fetch")
        command.startsWith("stealthy-fetch(") -> executeBrowserHelper(BrowserFetchMode.STEALTHY, command, "stealthy-fetch")
        command.startsWith("curl2fetcher(") -> executeCurl2Fetcher(readSingleArgument(command, "curl2fetcher"))
        else -> "Executed: $command"
    }

    private fun helpText(): String = buildString {
        appendLine("Available shell helpers:")
        appendLine("- get(\"https://...\")")
        appendLine("- post(\"https://...\")")
        appendLine("- put(\"https://...\")")
        appendLine("- delete(\"https://...\")")
        appendLine("- fetch(\"https://...\")")
        appendLine("- stealthy_fetch(\"https://...\")")
        appendLine("- page / response / pages / len(pages)")
        appendLine("- view(page)")
        appendLine("- namespace()")
        appendLine("- help()")
        appendLine("- uncurl(\"curl ...\")")
        appendLine("- curl2fetcher(\"curl ...\")")
    }

    private fun executeStaticHelper(method: String, command: String): String {
        val url = readSingleArgument(command, method.lowercase())
        val response = staticExecutor(method, url, RequestOptions())
        return updatePage(response).toShellJson()
    }

    private fun executeBrowserHelper(mode: BrowserFetchMode, command: String, functionName: String): String {
        val url = readSingleArgument(command, functionName)
        val response = browserExecutor(mode, url, BrowserFetchOptions())
        return updatePage(response).toShellJson()
    }

    private fun executeCurl2Fetcher(curlCommand: String): String {
        val request = curlParser.parse(curlCommand)
        val method = request.method.lowercase()
        require(method in supportedMethods) { "Request method \"$method\" isn't supported by Scrapling yet" }

        val options = curlParser.toRequestOptions(request)
        val effectiveOptions = if (method in setOf("get", "delete")) {
            options.copy(data = emptyMap(), json = null)
        } else {
            options
        }
        val response = staticExecutor(method.uppercase(), request.url, effectiveOptions)
        return updatePage(response).toShellJson()
    }

    private fun executeView(command: String): String {
        val argument = command.removePrefix("view(").removeSuffix(")").trim()
        val target = when (argument) {
            "", "page", "response" -> currentPage()
            else -> error("view(...) currently supports page/response only")
        }
        return viewer(target)
    }

    private fun readPageProperty(page: Response, property: String): String = when (property) {
        "url" -> page.url
        "status" -> page.status.toString()
        "reason" -> page.reason
        "method" -> page.method
        else -> error("Unsupported page property: $property")
    }

    private fun readHistoryProperty(command: String): String {
        val match = checkNotNull(pageIndexPattern.matchEntire(command))
        val index = match.groupValues[1].toInt()
        val property = match.groupValues[2]
        val resolvedIndex = if (index >= 0) index else pages.size + index
        val page = pages.getOrElse(resolvedIndex) { error("pages[$index] is out of range") }
        return readPageProperty(page, property)
    }

    private fun currentPage(): Response = page ?: error("No page has been fetched yet.")

    private fun updatePage(result: Response): Response = result.also { response ->
        page = response
        pages += response
        while (pages.size > 5) {
            pages.removeAt(0)
        }
    }

    private fun readSingleArgument(command: String, functionName: String): String {
        require(command.endsWith(")")) { "$functionName call must end with ')'." }
        val raw = command.removePrefix("$functionName(").removeSuffix(")").trim()
        require(raw.isNotEmpty()) { "$functionName requires a curl command." }
        val unescaped = raw
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
        return if (
            (unescaped.startsWith('"') && unescaped.endsWith('"')) ||
            (unescaped.startsWith('\'') && unescaped.endsWith('\''))
        ) {
            unescaped.substring(1, unescaped.length - 1)
        } else {
            unescaped
        }
    }

    private fun splitStatements(command: String): List<String> {
        if (command.isBlank()) return emptyList()

        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        var escaped = false

        command.forEach { character ->
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }

                character == '\\' -> {
                    current.append(character)
                    escaped = true
                }

                quote != null -> {
                    current.append(character)
                    if (character == quote) {
                        quote = null
                    }
                }

                character == '\'' || character == '"' -> {
                    current.append(character)
                    quote = character
                }

                character == '(' -> {
                    current.append(character)
                    depth += 1
                }

                character == ')' -> {
                    current.append(character)
                    depth = (depth - 1).coerceAtLeast(0)
                }

                character == ';' && depth == 0 -> {
                    val statement = current.toString().trim()
                    if (statement.isNotBlank()) {
                        statements += statement
                    }
                    current.clear()
                }

                else -> current.append(character)
            }
        }

        val tail = current.toString().trim()
        if (tail.isNotBlank()) {
            statements += tail
        }
        return statements
    }

    companion object {
        private val supportedMethods = setOf("get", "post", "put", "delete")
        private val pageIndexPattern = Regex("""pages\[(-?\d+)]\.(url|status|reason|method)""")

        private fun defaultStaticExecutor(): (String, String, RequestOptions) -> Response {
            val client = FetcherClient()
            return { method, url, options ->
                when (method.uppercase()) {
                    "GET" -> client.get(url, options)
                    "POST" -> client.post(url, options.data, options)
                    "PUT" -> client.put(url, options.data, options)
                    "DELETE" -> client.delete(url, options)
                    else -> error("Unsupported static fetch method: $method")
                }
            }
        }

        private fun defaultBrowserExecutor(): (BrowserFetchMode, String, BrowserFetchOptions) -> Response = { mode, url, options ->
            when (mode) {
                BrowserFetchMode.DYNAMIC -> DynamicFetcher.fetch(url, options)
                BrowserFetchMode.STEALTHY -> StealthyFetcher.fetch(url, options)
            }
        }

        private fun defaultViewer(): (Response) -> String = { response ->
            val output = Files.createTempFile("scrapling_view_", ".html")
            Files.writeString(output, response.body.toString(Charsets.UTF_8), StandardCharsets.UTF_8)
            val uri = output.toUri()
            if (Desktop.isDesktopSupported()) {
                runCatching { Desktop.getDesktop().browse(uri) }
            }
            uri.toString()
        }
    }
}

@Serializable
private data class ShellFetchResult(
    val url: String,
    val status: Int,
    val reason: String,
    val method: String,
)

private fun Response.toShellResult(): ShellFetchResult = ShellFetchResult(
    url = url,
    status = status,
    reason = reason,
    method = method,
)

private fun Response.toShellJson(): String = shellJson.encodeToString(toShellResult())

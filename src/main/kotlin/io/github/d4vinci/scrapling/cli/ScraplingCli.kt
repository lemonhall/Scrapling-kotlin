package io.github.d4vinci.scrapling.cli

import com.microsoft.playwright.CLI
import io.github.d4vinci.scrapling.ai.ScraplingMcpServer
import io.github.d4vinci.scrapling.core.shell.ContentExtractor
import io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchOptions
import io.github.d4vinci.scrapling.fetchers.browser.BrowserProxyUrl
import io.github.d4vinci.scrapling.fetchers.browser.DynamicFetcher
import io.github.d4vinci.scrapling.fetchers.browser.StealthyFetcher
import io.github.d4vinci.scrapling.fetchers.static.FetcherClient
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

enum class BrowserFetchMode {
    DYNAMIC,
    STEALTHY,
}

fun interface StaticExtractExecutor {
    fun execute(method: String, url: String, options: RequestOptions): Response
}

fun interface BrowserExtractExecutor {
    fun execute(mode: BrowserFetchMode, url: String, options: BrowserFetchOptions): Response
}

fun interface DependencyInstaller {
    fun install(force: Boolean): String
}

class PlaywrightDependencyInstaller(
    private val markerPath: Path = Path.of(".scrapling_dependencies_installed"),
    private val installAction: (Array<String>) -> Unit = { args -> CLI.main(args) },
) : DependencyInstaller {
    override fun install(force: Boolean): String {
        if (!force && markerPath.exists()) {
            return "The dependencies are already installed"
        }
        installAction(arrayOf("install", "chromium"))
        markerPath.parent?.createDirectories()
        markerPath.writeText("chromium installed\n", Charsets.UTF_8)
        return if (force) "Playwright Chromium reinstalled" else "Playwright Chromium installed"
    }
}

class ScraplingCli(
    private val out: (String) -> Unit = ::println,
    private val err: (String) -> Unit = { message -> System.err.println(message) },
    private val installer: DependencyInstaller = PlaywrightDependencyInstaller(),
    private val staticExecutor: StaticExtractExecutor = DefaultStaticExtractExecutor(),
    private val browserExecutor: BrowserExtractExecutor = DefaultBrowserExtractExecutor(),
    private val shellFactory: (String, String, (String) -> Unit) -> ScraplingShell = { code, level, sink ->
        ScraplingShell(code = code, logLevel = level, sink = sink)
    },
    private val mcpFactory: () -> ScraplingMcpServer = { ScraplingMcpServer() },
) {
    fun run(args: Array<String>): Int = run(args.toList())

    fun run(args: List<String>): Int = try {
        when {
            args.isEmpty() || args.first() in setOf("help", "--help", "-h") -> {
                out(mainHelp())
                0
            }
            args.first() == "install" -> handleInstall(args.drop(1))
            args.first() == "shell" -> handleShell(args.drop(1))
            args.first() == "mcp" -> handleMcp(args.drop(1))
            args.first() == "extract" -> handleExtract(args.drop(1))
            else -> throw CliUsageException("Unknown command: ${args.first()}")
        }
    } catch (error: CliUsageException) {
        err(error.message ?: "Unknown CLI usage error")
        2
    }

    private fun handleInstall(args: List<String>): Int {
        val force = args.contains("--force") || args.contains("-f")
        out(installer.install(force))
        return 0
    }

    private fun handleShell(args: List<String>): Int {
        var code = ""
        var level = "debug"
        var index = 0
        while (index < args.size) {
            when (args[index]) {
                "-c", "--code" -> {
                    code = args.valueAfter(index, "shell code")
                    index += 2
                }
                "-L", "--loglevel" -> {
                    level = args.valueAfter(index, "log level")
                    index += 2
                }
                "--help", "-h" -> {
                    out(shellHelp())
                    return 0
                }
                else -> throw CliUsageException("Unknown shell option: ${args[index]}")
            }
        }
        shellFactory(code, level, out).start()
        return 0
    }

    private fun handleMcp(args: List<String>): Int {
        var http = false
        var host = "0.0.0.0"
        var port = 8000
        var index = 0
        while (index < args.size) {
            when (args[index]) {
                "--http" -> {
                    http = true
                    index += 1
                }
                "--host" -> {
                    host = args.valueAfter(index, "host")
                    index += 2
                }
                "--port" -> {
                    port = args.valueAfter(index, "port").toInt()
                    index += 2
                }
                "--help", "-h" -> {
                    out(mcpHelp())
                    return 0
                }
                else -> throw CliUsageException("Unknown mcp option: ${args[index]}")
            }
        }
        mcpFactory().serve(http, host, port, out)
        return 0
    }

    private fun handleExtract(args: List<String>): Int {
        if (args.isEmpty() || args.first() in setOf("--help", "-h", "help")) {
            out(extractHelp())
            return 0
        }
        return when (val command = args.first()) {
            "get", "post", "put", "delete" -> handleStaticExtract(command.uppercase(), args.drop(1))
            "fetch" -> handleBrowserExtract(BrowserFetchMode.DYNAMIC, args.drop(1))
            "stealthy-fetch", "stealthy_fetch" -> handleBrowserExtract(BrowserFetchMode.STEALTHY, args.drop(1))
            else -> throw CliUsageException("Unknown extract subcommand: $command")
        }
    }

    private fun handleStaticExtract(method: String, args: List<String>): Int {
        if (args.size < 2) {
            throw CliUsageException("extract ${method.lowercase()} requires <url> <output_file>")
        }
        val url = args[0]
        val outputFile = args[1]
        val parsed = StaticExtractArgs()
        var index = 2
        while (index < args.size) {
            when (args[index]) {
                "-H", "--headers" -> {
                    parsed.headers += args.valueAfter(index, "header")
                    index += 2
                }
                "--cookies" -> {
                    parsed.cookieHeader = args.valueAfter(index, "cookies")
                    index += 2
                }
                "-p", "--params" -> {
                    parsed.params += args.valueAfter(index, "param")
                    index += 2
                }
                "-d", "--data" -> {
                    parsed.data += args.valueAfter(index, "data")
                    index += 2
                }
                "-j", "--json" -> {
                    parsed.json = args.valueAfter(index, "json")
                    Json.parseToJsonElement(parsed.json!!)
                    index += 2
                }
                "--timeout" -> {
                    parsed.timeout = args.valueAfter(index, "timeout").toInt()
                    index += 2
                }
                "--proxy" -> {
                    parsed.proxy = args.valueAfter(index, "proxy")
                    index += 2
                }
                "-s", "--selector", "--css-selector" -> {
                    parsed.cssSelector = args.valueAfter(index, "selector")
                    index += 2
                }
                "--help", "-h" -> {
                    out(extractHelp())
                    return 0
                }
                else -> throw CliUsageException("Unknown extract option: ${args[index]}")
            }
        }
        val (headers, cookiesFromHeaders) = parseHeaders(parsed.headers)
        val cookies = cookiesFromHeaders + parseCookies(parsed.cookieHeader)
        val response = staticExecutor.execute(
            method = method,
            url = url,
            options = RequestOptions(
                data = parseAssignments(parsed.data),
                json = parsed.json,
                headers = headers,
                cookies = cookies,
                params = parseAssignments(parsed.params),
                timeout = parsed.timeout,
                proxy = parsed.proxy,
            ),
        )
        val outputPath = ContentExtractor.writeContentToFile(response, outputFile, parsed.cssSelector)
        out("Content successfully saved to '${outputPath.toAbsolutePath()}'")
        return 0
    }

    private fun handleBrowserExtract(mode: BrowserFetchMode, args: List<String>): Int {
        if (args.size < 2) {
            throw CliUsageException("extract ${mode.cliName()} requires <url> <output_file>")
        }
        val url = args[0]
        val outputFile = args[1]
        val parsed = BrowserExtractArgs()
        var index = 2
        while (index < args.size) {
            when (args[index]) {
                "--headful" -> {
                    parsed.headless = false
                    index += 1
                }
                "--disable-resources" -> {
                    parsed.disableResources = true
                    index += 1
                }
                "--network-idle" -> {
                    parsed.networkIdle = true
                    index += 1
                }
                "--real-chrome" -> {
                    parsed.realChrome = true
                    index += 1
                }
                "--block-webrtc" -> {
                    parsed.blockWebRtc = true
                    index += 1
                }
                "--solve-cloudflare" -> {
                    parsed.solveCloudflare = true
                    index += 1
                }
                "--hide-canvas" -> {
                    parsed.hideCanvas = true
                    index += 1
                }
                "--no-webgl" -> {
                    parsed.allowWebgl = false
                    index += 1
                }
                "--timeout" -> {
                    parsed.timeout = args.valueAfter(index, "timeout").toDouble()
                    index += 2
                }
                "--wait" -> {
                    parsed.wait = args.valueAfter(index, "wait").toDouble()
                    index += 2
                }
                "--wait-selector" -> {
                    parsed.waitSelector = args.valueAfter(index, "wait-selector")
                    index += 2
                }
                "--locale" -> {
                    parsed.locale = args.valueAfter(index, "locale")
                    index += 2
                }
                "--proxy" -> {
                    parsed.proxy = args.valueAfter(index, "proxy")
                    index += 2
                }
                "-H", "--headers" -> {
                    parsed.extraHeaders += args.valueAfter(index, "header")
                    index += 2
                }
                "-s", "--selector", "--css-selector" -> {
                    parsed.cssSelector = args.valueAfter(index, "selector")
                    index += 2
                }
                "--help", "-h" -> {
                    out(extractHelp())
                    return 0
                }
                else -> throw CliUsageException("Unknown browser extract option: ${args[index]}")
            }
        }
        val (headers, _) = parseHeaders(parsed.extraHeaders, parseCookieHeader = false)
        val response = browserExecutor.execute(
            mode = mode,
            url = url,
            options = BrowserFetchOptions(
                headless = parsed.headless,
                disableResources = parsed.disableResources,
                networkIdle = parsed.networkIdle,
                timeout = parsed.timeout,
                wait = parsed.wait,
                waitSelector = parsed.waitSelector,
                locale = parsed.locale,
                realChrome = parsed.realChrome,
                proxy = parsed.proxy?.let(::BrowserProxyUrl),
                extraHeaders = headers,
                blockWebRtc = parsed.blockWebRtc,
                solveCloudflare = parsed.solveCloudflare,
                hideCanvas = parsed.hideCanvas,
                allowWebgl = parsed.allowWebgl,
            ),
        )
        val outputPath = ContentExtractor.writeContentToFile(response, outputFile, parsed.cssSelector)
        out("Content successfully saved to '${outputPath.toAbsolutePath()}'")
        return 0
    }

    fun mainHelp(): String = buildString {
        appendLine("Scrapling Kotlin CLI")
        appendLine("Commands:")
        appendLine("  install      Install browser dependencies")
        appendLine("  shell        Interactive scraping console")
        appendLine("  extract      Fetch pages and save markdown/html/text")
        appendLine("  mcp          Run the Scrapling MCP server")
    }

    fun extractHelp(): String = buildString {
        appendLine("extract subcommands:")
        appendLine("  get <url> <output_file>")
        appendLine("  post <url> <output_file>")
        appendLine("  put <url> <output_file>")
        appendLine("  delete <url> <output_file>")
        appendLine("  fetch <url> <output_file>")
        appendLine("  stealthy-fetch <url> <output_file>")
    }

    fun shellHelp(): String = "shell options: -c/--code, -L/--loglevel"

    fun mcpHelp(): String = "mcp options: --http, --host, --port"
}

private class DefaultStaticExtractExecutor : StaticExtractExecutor {
    private val client = FetcherClient()

    override fun execute(method: String, url: String, options: RequestOptions): Response = when (method) {
        "GET" -> client.get(url, options)
        "POST" -> client.post(url, options.data, options)
        "PUT" -> client.put(url, options.data, options)
        "DELETE" -> client.delete(url, options)
        else -> error("Unsupported method: $method")
    }
}

private class DefaultBrowserExtractExecutor : BrowserExtractExecutor {
    override fun execute(mode: BrowserFetchMode, url: String, options: BrowserFetchOptions): Response = when (mode) {
        BrowserFetchMode.DYNAMIC -> DynamicFetcher.fetch(url, options)
        BrowserFetchMode.STEALTHY -> StealthyFetcher.fetch(url, options)
    }
}

private class StaticExtractArgs {
    val headers: MutableList<String> = mutableListOf()
    var cookieHeader: String = ""
    val params: MutableList<String> = mutableListOf()
    val data: MutableList<String> = mutableListOf()
    var json: String? = null
    var timeout: Int? = 30
    var proxy: String? = null
    var cssSelector: String? = null
}

private class BrowserExtractArgs {
    var headless: Boolean = true
    var disableResources: Boolean = false
    var networkIdle: Boolean = false
    var realChrome: Boolean = false
    var blockWebRtc: Boolean = false
    var solveCloudflare: Boolean = false
    var hideCanvas: Boolean = false
    var allowWebgl: Boolean = true
    var timeout: Double = 30_000.0
    var wait: Double? = null
    var waitSelector: String? = null
    var locale: String? = null
    var proxy: String? = null
    val extraHeaders: MutableList<String> = mutableListOf()
    var cssSelector: String? = null
}

private class CliUsageException(message: String) : RuntimeException(message)

private fun BrowserFetchMode.cliName(): String = when (this) {
    BrowserFetchMode.DYNAMIC -> "fetch"
    BrowserFetchMode.STEALTHY -> "stealthy-fetch"
}

private fun List<String>.valueAfter(index: Int, label: String): String = getOrNull(index + 1)
    ?: throw CliUsageException("Missing value for $label")

private fun parseAssignments(values: List<String>): Map<String, String> = values.associate { entry ->
    val separator = entry.indexOf('=')
    require(separator > 0) { "Invalid key=value pair: $entry" }
    entry.substring(0, separator) to entry.substring(separator + 1)
}

private fun parseCookies(cookieHeader: String): Map<String, String> = cookieHeader
    .split(';')
    .mapNotNull { part ->
        val trimmed = part.trim()
        if (trimmed.isBlank()) {
            null
        } else {
            val separator = trimmed.indexOf('=')
            require(separator > 0) { "Invalid cookie pair: $trimmed" }
            trimmed.substring(0, separator).trim() to trimmed.substring(separator + 1).trim()
        }
    }
    .toMap()

private fun parseHeaders(
    headerLines: List<String>,
    parseCookieHeader: Boolean = true,
): Pair<Map<String, String>, Map<String, String>> {
    val headers = linkedMapOf<String, String>()
    val cookies = linkedMapOf<String, String>()
    headerLines.forEach { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) {
            throw CliUsageException("Could not parse header without colon: '$line'.")
        }
        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim()
        if (parseCookieHeader && key.equals("cookie", ignoreCase = true)) {
            cookies += parseCookies(value)
        } else {
            headers[key] = value
        }
    }
    return headers to cookies
}

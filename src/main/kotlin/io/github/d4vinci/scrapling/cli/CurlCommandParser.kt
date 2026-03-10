package io.github.d4vinci.scrapling.cli

import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonElement.Companion.serializer
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Serializable
data class ParsedCurlRequest(
    val method: String,
    val url: String,
    val params: Map<String, String> = emptyMap(),
    val data: String? = null,
    @SerialName("json_data")
    val jsonData: JsonElement? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val proxy: Map<String, String>? = null,
    @SerialName("follow_redirects")
    val followRedirects: Boolean = true,
)

class CurlCommandParser(
    private val json: Json = shellJson,
) {
    fun parse(curlCommand: String): ParsedCurlRequest {
        val tokens = tokenize(normalize(curlCommand))
        require(tokens.isNotEmpty()) { "Curl command is missing a URL." }

        var url: String? = null
        var explicitMethod: String? = null
        var forceGet = false
        val headerLines = mutableListOf<String>()
        var cookieHeader: String? = null
        var data: String? = null
        var dataRaw: String? = null
        var dataBinary: String? = null
        val dataUrlEncoded = mutableListOf<String>()
        var proxy: String? = null
        var proxyUser: String? = null
        var index = 0

        while (index < tokens.size) {
            when (val token = tokens[index]) {
                "-X", "--request" -> {
                    explicitMethod = tokens.valueAfter(index, token)
                    index += 2
                }
                "-H", "--header" -> {
                    headerLines += tokens.valueAfter(index, token)
                    index += 2
                }
                "-A", "--user-agent" -> {
                    headerLines += "User-Agent: ${tokens.valueAfter(index, token)}"
                    index += 2
                }
                "-d", "--data" -> {
                    data = tokens.valueAfter(index, token)
                    index += 2
                }
                "--data-raw" -> {
                    dataRaw = tokens.valueAfter(index, token).removePrefix("$")
                    index += 2
                }
                "--data-binary" -> {
                    dataBinary = tokens.valueAfter(index, token)
                    index += 2
                }
                "--data-urlencode" -> {
                    dataUrlEncoded += tokens.valueAfter(index, token)
                    index += 2
                }
                "-G", "--get" -> {
                    forceGet = true
                    index += 1
                }
                "-b", "--cookie" -> {
                    cookieHeader = tokens.valueAfter(index, token)
                    index += 2
                }
                "-x", "--proxy" -> {
                    proxy = tokens.valueAfter(index, token)
                    index += 2
                }
                "-U", "--proxy-user" -> {
                    proxyUser = tokens.valueAfter(index, token)
                    index += 2
                }
                "-k", "--insecure", "--compressed", "-i", "--include", "-s", "--silent", "-v", "--verbose", "-L", "--location" -> {
                    index += 1
                }
                else -> {
                    when {
                        token.startsWith("-") -> throw IllegalArgumentException("Unknown/Unsupported curl arguments: [$token]")
                        url == null -> {
                            url = token
                            index += 1
                        }
                        else -> throw IllegalArgumentException("Unknown/Unsupported curl arguments: [$token]")
                    }
                }
            }
        }

        val resolvedUrl = requireNotNull(url) { "Curl command is missing a URL." }
        val method = when {
            forceGet -> "get"
            !explicitMethod.isNullOrBlank() -> explicitMethod.trim().lowercase()
            data != null || dataRaw != null || dataBinary != null || dataUrlEncoded.isNotEmpty() -> "post"
            else -> "get"
        }

        val (headers, cookiesFromHeaders) = parseHeaderLines(headerLines)
        val cookies = LinkedHashMap(cookiesFromHeaders)
        if (!cookieHeader.isNullOrBlank()) {
            cookies.putAll(parseCookieHeader(cookieHeader))
        }

        var params = emptyMap<String, String>()
        var body = dataBinary ?: dataRaw ?: data ?: dataUrlEncoded.takeIf { it.isNotEmpty() }?.joinToString("&")
        var jsonData: JsonElement? = body?.let { decodeJsonOrNull(it) }
        if (jsonData != null) {
            body = null
        }

        if (method == "get" && body != null) {
            params = parseEncodedPairs(body)
            body = null
        }

        val proxyMap = buildProxyMap(proxy, proxyUser)
        return ParsedCurlRequest(
            method = method,
            url = resolvedUrl,
            params = params,
            data = body,
            jsonData = jsonData,
            headers = headers,
            cookies = cookies,
            proxy = proxyMap.ifEmpty { null },
            followRedirects = true,
        )
    }

    fun toRequestOptions(request: ParsedCurlRequest): RequestOptions {
        val formData = request.data?.let(::parseEncodedPairs).orEmpty()
        val jsonPayload = request.jsonData?.let { json.encodeToString(JsonElement.serializer(), it) }
        return RequestOptions(
            data = formData,
            json = jsonPayload,
            headers = request.headers,
            cookies = request.cookies,
            params = request.params,
            proxies = request.proxy.orEmpty(),
            proxy = request.proxy?.values?.firstOrNull(),
            followRedirects = request.followRedirects,
        )
    }

    private fun decodeJsonOrNull(raw: String): JsonElement? = runCatching {
        json.parseToJsonElement(raw)
    }.getOrNull()?.takeIf { element ->
        element is JsonObject || element is JsonArray
    }

    private fun normalize(curlCommand: String): String {
        val trimmed = curlCommand.trim().replace("\\\r\n", " ").replace("\\\n", " ")
        require(trimmed.startsWith("curl ") || trimmed == "curl" || trimmed.startsWith("curl.exe ") || trimmed == "curl.exe") {
            "Input must be a curl command."
        }
        return trimmed.replaceFirst(Regex("^curl(?:\\.exe)?\\s*"), "").trim()
    }

    private fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false

        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.setLength(0)
            }
        }

        command.forEach { ch ->
            if (escaping) {
                current.append(ch)
                escaping = false
                return@forEach
            }
            when {
                ch == '\\' && quote != '\'' -> escaping = true
                quote == null && ch.isWhitespace() -> flush()
                quote == null && (ch == '"' || ch == '\'') -> quote = ch
                quote != null && ch == quote -> quote = null
                else -> current.append(ch)
            }
        }
        require(!escaping) { "Dangling escape in curl command." }
        require(quote == null) { "Unterminated quoted string in curl command." }
        flush()
        return tokens
    }

    private fun parseHeaderLines(lines: List<String>): Pair<Map<String, String>, Map<String, String>> {
        val headers = linkedMapOf<String, String>()
        val cookies = linkedMapOf<String, String>()
        lines.forEach { line ->
            val separator = line.indexOf(':')
            require(separator >= 0) { "Could not parse header without colon: $line" }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (key.equals("Cookie", ignoreCase = true)) {
                cookies.putAll(parseCookieHeader(value))
            } else {
                headers[key] = value
            }
        }
        return headers to cookies
    }

    private fun parseCookieHeader(cookieHeader: String): Map<String, String> = cookieHeader
        .split(';')
        .mapNotNull { chunk ->
            val trimmed = chunk.trim()
            if (trimmed.isBlank()) {
                null
            } else {
                val separator = trimmed.indexOf('=')
                if (separator < 0) {
                    trimmed to ""
                } else {
                    trimmed.substring(0, separator).trim() to trimmed.substring(separator + 1).trim()
                }
            }
        }
        .toMap(linkedMapOf())

    private fun parseEncodedPairs(raw: String): Map<String, String> = raw
        .trim()
        .trim('"', '\'')
        .split('&')
        .mapNotNull { segment ->
            val cleaned = segment.trim().trim('"', '\'')
            if (cleaned.isBlank()) {
                null
            } else {
                val separator = cleaned.indexOf('=')
                if (separator < 0) {
                    decode(cleaned) to ""
                } else {
                    decode(cleaned.substring(0, separator)) to decode(cleaned.substring(separator + 1))
                }
            }
        }
        .toMap(linkedMapOf())

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun buildProxyMap(proxy: String?, proxyUser: String?): Map<String, String> {
        if (proxy.isNullOrBlank()) return emptyMap()
        val normalized = if (proxyUser.isNullOrBlank()) proxy else attachProxyUser(proxy, proxyUser)
        return linkedMapOf("http" to normalized, "https" to normalized)
    }

    private fun attachProxyUser(proxy: String, proxyUser: String): String = runCatching {
        val uri = URI.create(proxy)
        if (uri.host == null) {
            if (proxy.contains("://")) {
                proxy.replaceFirst("://", "://$proxyUser@")
            } else {
                "http://$proxyUser@$proxy"
            }
        } else {
            URI(
                uri.scheme ?: "http",
                proxyUser,
                uri.host,
                uri.port,
                uri.path,
                uri.query,
                uri.fragment,
            ).toString()
        }
    }.getOrElse {
        if (proxy.contains("://")) {
            proxy.replaceFirst("://", "://$proxyUser@")
        } else {
            "http://$proxyUser@$proxy"
        }
    }

    private fun List<String>.valueAfter(index: Int, flag: String): String =
        getOrNull(index + 1) ?: throw IllegalArgumentException("Missing value for curl flag: $flag")
}

internal val shellJson: Json = Json {
    prettyPrint = true
}

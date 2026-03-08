package io.github.d4vinci.scrapling.fetchers.static

import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class JdkHttpTransport(
    cookieManager: CookieManager? = null,
) : HttpTransport {
    private val sharedCookieManager = cookieManager
    private val redirectingClient = newClient(HttpClient.Redirect.NORMAL)
    private val nonRedirectingClient = newClient(HttpClient.Redirect.NEVER)

    override fun request(method: String, url: String, options: RequestOptions): RawHttpResponse {
        var attempt = 0
        var lastFailure: IOException? = null
        val retries = options.retries.coerceAtLeast(0)

        while (attempt <= retries) {
            try {
                return execute(method, url, options)
            } catch (exception: IOException) {
                lastFailure = exception
                if (attempt == retries) {
                    throw exception
                }
                attempt += 1
            }
        }

        throw checkNotNull(lastFailure)
    }

    private fun execute(method: String, url: String, options: RequestOptions): RawHttpResponse {
        val requestHeaders = mergeRequestHeaders(options)
        val request = buildRequest(method, url, options, requestHeaders)
        val response = httpClient(options.followRedirects).send(request, HttpResponse.BodyHandlers.ofByteArray())

        return RawHttpResponse(
            url = response.uri().toString(),
            body = response.body(),
            status = response.statusCode(),
            reason = reasonPhrase(response.statusCode()),
            headers = flattenHeaders(response.headers()),
            cookies = extractCookies(response.headers()),
            requestHeaders = requestHeaders.toMap(),
            method = method,
        )
    }

    private fun buildRequest(
        method: String,
        url: String,
        options: RequestOptions,
        requestHeaders: LinkedHashMap<String, String>,
    ): HttpRequest {
        val builder = HttpRequest.newBuilder(buildUri(url, options.params))
        options.timeout?.let { builder.timeout(Duration.ofSeconds(it.toLong())) }

        val bodyPublisher = buildBodyPublisher(method, options, requestHeaders)
        requestHeaders.forEach { (key, value) -> builder.header(key, value) }
        builder.method(method, bodyPublisher)
        return builder.build()
    }

    private fun buildBodyPublisher(
        method: String,
        options: RequestOptions,
        requestHeaders: LinkedHashMap<String, String>,
    ): HttpRequest.BodyPublisher {
        if (method == "POST" || method == "PUT") {
            options.json?.let {
                requestHeaders.putIfAbsent("Content-Type", "application/json; charset=utf-8")
                return HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8)
            }

            if (options.data.isNotEmpty()) {
                requestHeaders.putIfAbsent("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                return HttpRequest.BodyPublishers.ofString(formEncode(options.data), StandardCharsets.UTF_8)
            }
        }

        return HttpRequest.BodyPublishers.noBody()
    }

    private fun mergeRequestHeaders(options: RequestOptions): LinkedHashMap<String, String> {
        val headers = linkedMapOf<String, String>()
        if (options.stealthyHeaders) {
            headers.putAll(defaultStealthyHeaders())
        }
        headers.putAll(options.headers)
        if (options.cookies.isNotEmpty()) {
            headers["Cookie"] = options.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
        return LinkedHashMap(headers)
    }

    private fun buildUri(url: String, params: Map<String, String>): URI {
        val base = URI.create(url)
        if (params.isEmpty()) {
            return base
        }

        val queryParts = mutableListOf<String>()
        base.rawQuery?.takeIf { it.isNotBlank() }?.let(queryParts::add)
        queryParts += params.entries.map { (key, value) -> "${encode(key)}=${encode(value)}" }

        return URI(base.scheme, base.authority, base.path, queryParts.joinToString("&"), base.fragment)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun formEncode(data: Map<String, String>): String =
        data.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    private fun defaultStealthyHeaders(): Map<String, String> =
        linkedMapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "User-Agent" to DEFAULT_USER_AGENT,
        )

    private fun flattenHeaders(headers: HttpHeaders): Map<String, String> =
        headers.map().mapValues { (_, values) -> values.joinToString(", ") }

    private fun extractCookies(headers: HttpHeaders): Map<String, String> =
        headers.allValues("Set-Cookie")
            .flatMap { headerValue -> HttpCookie.parse(headerValue) }
            .associate { cookie -> cookie.name to cookie.value }

    private fun httpClient(followRedirects: Boolean): HttpClient =
        if (followRedirects) redirectingClient else nonRedirectingClient

    private fun newClient(redirect: HttpClient.Redirect): HttpClient {
        val builder = HttpClient.newBuilder()
            .followRedirects(redirect)
            .connectTimeout(Duration.ofSeconds(30))

        sharedCookieManager?.let(builder::cookieHandler)
        return builder.build()
    }

    private fun reasonPhrase(status: Int): String = STATUS_REASONS[status] ?: ""

    companion object {
        val sessionTransport: JdkHttpTransport
            get() = JdkHttpTransport(CookieManager(null, CookiePolicy.ACCEPT_ALL))

        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

        private val STATUS_REASONS = mapOf(
            200 to "OK",
            201 to "Created",
            204 to "No Content",
            301 to "Moved Permanently",
            302 to "Found",
            303 to "See Other",
            307 to "Temporary Redirect",
            308 to "Permanent Redirect",
            400 to "Bad Request",
            401 to "Unauthorized",
            403 to "Forbidden",
            404 to "Not Found",
            500 to "Internal Server Error",
            501 to "Not Implemented",
            502 to "Bad Gateway",
            503 to "Service Unavailable",
        )
    }
}

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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AsyncJdkHttpTransport(
    cookieManager: CookieManager? = null,
    private val retryPause: AsyncRetryPause = DefaultAsyncRetryPause,
) : AsyncHttpTransport {
    private val clientPool = StaticHttpClientPool(cookieManager)

    override suspend fun request(method: String, url: String, options: RequestOptions): RawHttpResponse {
        options.validateTransportSupport()
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
                if (options.retryDelay > 0) {
                    retryPause.pause(options.retryDelay.coerceAtLeast(0))
                }
            }
        }

        throw checkNotNull(lastFailure)
    }

    private suspend fun execute(method: String, url: String, options: RequestOptions): RawHttpResponse {
        val proxyUrl = options.resolveProxy(url)
        val requestHeaders = mergeRequestHeaders(options, proxyUrl)
        val request = buildRequest(method, url, options, requestHeaders)
        val response = httpClient(options.followRedirects, proxyUrl)
            .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .await()

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

    private fun mergeRequestHeaders(options: RequestOptions, proxyUrl: String?): LinkedHashMap<String, String> {
        val headers = linkedMapOf<String, String>()
        if (options.stealthyHeaders) {
            headers.putAll(defaultStealthyHeaders(options))
        }
        options.authorizationHeader()?.let { headers.putIfAbsent("Authorization", it) }
        options.resolvedProxyAuthorizationHeader(proxyUrl)?.let { headers.putIfAbsent("Proxy-Authorization", it) }
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

    private fun defaultStealthyHeaders(options: RequestOptions): Map<String, String> =
        linkedMapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "User-Agent" to options.resolvedUserAgent(),
        )

    private fun flattenHeaders(headers: HttpHeaders): Map<String, String> =
        headers.map().mapValues { (_, values) -> values.joinToString(", ") }

    private fun extractCookies(headers: HttpHeaders): Map<String, String> =
        headers.allValues("Set-Cookie")
            .flatMap { headerValue -> HttpCookie.parse(headerValue) }
            .associate { cookie -> cookie.name to cookie.value }

    private fun httpClient(followRedirects: Boolean, proxyUrl: String?): HttpClient =
        clientPool.client(followRedirects, proxyUrl)

    private fun reasonPhrase(status: Int): String = STATUS_REASONS[status] ?: ""

    private suspend fun <T> CompletableFuture<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            whenComplete { result, throwable ->
                if (throwable == null) {
                    continuation.resume(result)
                } else {
                    val cause = if (throwable is CompletionException && throwable.cause != null) {
                        throwable.cause!!
                    } else {
                        throwable
                    }
                    continuation.resumeWithException(cause)
                }
            }
            continuation.invokeOnCancellation { cancel(true) }
        }

    companion object {
        val sessionTransport: AsyncJdkHttpTransport
            get() = AsyncJdkHttpTransport(CookieManager(null, CookiePolicy.ACCEPT_ALL))

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

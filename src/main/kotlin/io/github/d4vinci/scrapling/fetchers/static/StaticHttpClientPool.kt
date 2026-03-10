package io.github.d4vinci.scrapling.fetchers.static

import java.net.CookieManager
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

internal class StaticHttpClientPool(
    private val sharedCookieManager: CookieManager? = null,
) {
    private val clients = ConcurrentHashMap<HttpClientConfig, HttpClient>()

    fun client(followRedirects: Boolean, proxyUrl: String?): HttpClient =
        clients.computeIfAbsent(HttpClientConfig(followRedirects, proxyUrl)) { config ->
            buildClient(config)
        }

    private fun buildClient(config: HttpClientConfig): HttpClient {
        val builder = HttpClient.newBuilder()
            .followRedirects(if (config.followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(30))

        sharedCookieManager?.let(builder::cookieHandler)
        config.proxyUrl?.let { builder.proxy(ProxySelector.of(parseProxyAddress(it))) }
        return builder.build()
    }

    private fun parseProxyAddress(proxyUrl: String): InetSocketAddress {
        val uri = URI.create(proxyUrl)
        val host = requireNotNull(uri.host) { "Proxy URL must include a host." }
        val port = if (uri.port != -1) {
            uri.port
        } else {
            when (uri.scheme?.lowercase()) {
                "http" -> 80
                "https" -> 443
                else -> error("Unsupported proxy scheme: ${uri.scheme}")
            }
        }
        return InetSocketAddress(host, port)
    }
}

private data class HttpClientConfig(
    val followRedirects: Boolean,
    val proxyUrl: String?,
)

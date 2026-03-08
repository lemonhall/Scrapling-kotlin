package io.github.d4vinci.scrapling.fetchers.static

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

class RecordingProxyServer private constructor(
    private val server: HttpServer,
) : AutoCloseable {
    private val requests = mutableListOf<ObservedProxyRequest>()

    fun proxyUrl(): String = "http://127.0.0.1:${server.address.port}"

    fun requestCount(): Int = requests.size

    fun lastRequest(): ObservedProxyRequest? = requests.lastOrNull()

    override fun close() {
        server.stop(0)
    }

    private fun registerContexts() {
        server.createContext("/") { exchange ->
            requests += ObservedProxyRequest(
                method = exchange.requestMethod,
                uri = exchange.requestURI.toString(),
                headers = exchange.requestHeaders.entries.associate { entry -> entry.key to entry.value.joinToString(", ") },
            )
            respond(exchange, 200, "<html><body><h1>proxied</h1></body></html>")
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.encodeToByteArray()
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        fun start(): RecordingProxyServer {
            val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val server = RecordingProxyServer(httpServer)
            server.registerContexts()
            httpServer.start()
            return server
        }
    }
}

data class ObservedProxyRequest(
    val method: String,
    val uri: String,
    val headers: Map<String, String>,
)

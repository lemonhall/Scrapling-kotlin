package io.github.d4vinci.scrapling.fetchers.static

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import java.net.http.HttpTimeoutException
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaticFetchersJdkTransportTest {
    private lateinit var server: LocalTestServer

    @BeforeTest
    fun setUp() {
        server = LocalTestServer.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun fetcherClientUsesRealHttpTransportForGetRequests() {
        val client = FetcherClient()

        val response = client.get(
            server.url("/html"),
            RequestOptions(
                params = mapOf("q" to "kotlin"),
                headers = mapOf("X-Test" to "yes"),
            ),
        )

        assertEquals(200, response.status)
        assertEquals("kotlin", response.css("h1::text").get()?.value)
        assertEquals("yes", response.requestHeaders["X-Test"])
        assertTrue(response.requestHeaders.containsKey("User-Agent"))

        val request = server.lastRequest("/html")
        assertNotNull(request)
        assertEquals("GET", request.method)
        assertEquals("q=kotlin", request.query)
        assertEquals("yes", request.headers.entries.first { (key, _) -> key.equals("X-Test", ignoreCase = true) }.value)
    }

    @Test
    fun fetcherClientUsesRealHttpTransportForBodyMethodsAndRedirects() {
        val client = FetcherClient()

        val postResponse = client.post(server.url("/submit"), data = mapOf("key" to "value"))
        val putResponse = client.put(server.url("/submit"), data = mapOf("mode" to "replace"))
        val deleteResponse = client.delete(server.url("/delete"))
        val redirected = client.get(server.url("/redirect"))
        val notRedirected = client.get(server.url("/redirect"), RequestOptions(followRedirects = false))

        assertEquals(200, postResponse.status)
        assertEquals(200, putResponse.status)
        assertEquals(204, deleteResponse.status)
        assertEquals(200, redirected.status)
        assertEquals(server.url("/final"), redirected.url)
        assertEquals(302, notRedirected.status)

        val postRequest = server.lastRequest("/submit", "POST")
        assertNotNull(postRequest)
        assertEquals("key=value", postRequest.body)

        val putRequest = server.lastRequest("/submit", "PUT")
        assertNotNull(putRequest)
        assertEquals("mode=replace", putRequest.body)

        val deleteRequest = server.lastRequest("/delete")
        assertNotNull(deleteRequest)
        assertEquals("DELETE", deleteRequest.method)
    }

    @Test
    fun fetcherClientRetriesTimedOutRequestsWhenRetryBudgetExists() {
        val client = FetcherClient()

        val response = client.get(
            server.url("/flaky-timeout"),
            RequestOptions(timeout = 1, retries = 1),
        )

        assertEquals(200, response.status)
        assertEquals("attempt-2", response.css("h1::text").get()?.value)
        assertEquals(2, server.requestCount("/flaky-timeout"))
    }

    @Test
    fun fetcherClientSurfacesTimeoutWhenRetryBudgetIsExhausted() {
        val client = FetcherClient()

        assertFailsWith<HttpTimeoutException> {
            client.get(
                server.url("/slow"),
                RequestOptions(timeout = 1, retries = 0),
            )
        }
    }

    @Test
    fun fetcherSessionReusesCookiesAcrossRealRequests() {
        val session = FetcherSession(timeout = 45, retries = 5)
        session.open()
        try {
            val first = session.get(server.url("/set-cookie"))
            val second = session.get(server.url("/echo-cookie"))

            assertEquals(200, first.status)
            assertEquals(200, second.status)
            assertEquals("session=abc", second.css("h1::text").get()?.value)

            val echoRequest = server.lastRequest("/echo-cookie")
            assertNotNull(echoRequest)
            assertTrue((echoRequest.headers["Cookie"] ?: "").contains("session=abc"))
        } finally {
            session.close()
        }
    }

    private class LocalTestServer private constructor(
        private val server: HttpServer,
    ) : AutoCloseable {
        private val requests = mutableListOf<ObservedRequest>()
        private val pathCounts = mutableMapOf<String, Int>()

        fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

        fun lastRequest(path: String, method: String? = null): ObservedRequest? =
            requests.lastOrNull { it.path == path && (method == null || it.method == method) }

        fun requestCount(path: String): Int = pathCounts[path] ?: 0

        override fun close() {
            server.stop(0)
        }

        private fun registerContexts() {
            server.createContext("/html") { exchange ->
                record(exchange)
                val queryValue = exchange.requestURI.query?.substringAfter("q=") ?: "missing"
                respond(exchange, 200, html(queryValue), mapOf("Content-Type" to "text/html; charset=utf-8"))
            }
            server.createContext("/submit") { exchange ->
                val request = record(exchange)
                respond(exchange, 200, html(request.body), mapOf("Content-Type" to "text/html; charset=utf-8"))
            }
            server.createContext("/delete") { exchange ->
                record(exchange)
                respond(exchange, 204, byteArrayOf())
            }
            server.createContext("/redirect") { exchange ->
                record(exchange)
                exchange.responseHeaders.add("Location", url("/final"))
                respond(exchange, 302, byteArrayOf())
            }
            server.createContext("/final") { exchange ->
                record(exchange)
                respond(exchange, 200, html("redirected"), mapOf("Content-Type" to "text/html; charset=utf-8"))
            }
            server.createContext("/set-cookie") { exchange ->
                record(exchange)
                exchange.responseHeaders.add("Set-Cookie", "session=abc; Path=/")
                respond(exchange, 200, html("cookie-set"), mapOf("Content-Type" to "text/html; charset=utf-8"))
            }
            server.createContext("/echo-cookie") { exchange ->
                val request = record(exchange)
                respond(exchange, 200, html(request.headers["Cookie"] ?: "missing"), mapOf("Content-Type" to "text/html; charset=utf-8"))
            }
            server.createContext("/slow") { exchange ->
                record(exchange)
                Thread.sleep(1_500)
                respond(exchange, 200, html("slow"), mapOf("Content-Type" to "text/html; charset=utf-8"))
            }
            server.createContext("/flaky-timeout") { exchange ->
                record(exchange)
                val attempt = requestCount("/flaky-timeout")
                if (attempt == 1) {
                    Thread.sleep(1_500)
                }
                respond(exchange, 200, html("attempt-$attempt"), mapOf("Content-Type" to "text/html; charset=utf-8"))
            }
        }

        private fun record(exchange: HttpExchange): ObservedRequest {
            val request = ObservedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.query,
                headers = exchange.requestHeaders.entries.associate { entry -> entry.key to entry.value.joinToString(", ") },
                body = exchange.requestBody.readAllBytes().decodeToString(),
            )
            requests += request
            pathCounts[request.path] = (pathCounts[request.path] ?: 0) + 1
            return request
        }

        private fun respond(
            exchange: HttpExchange,
            status: Int,
            body: String,
            headers: Map<String, String> = emptyMap(),
        ) = respond(exchange, status, body.encodeToByteArray(), headers)

        private fun respond(
            exchange: HttpExchange,
            status: Int,
            body: ByteArray,
            headers: Map<String, String> = emptyMap(),
        ) {
            headers.forEach { (key, value) -> exchange.responseHeaders.add(key, value) }
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        companion object {
            fun start(): LocalTestServer {
                val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                val server = LocalTestServer(httpServer)
                server.registerContexts()
                httpServer.start()
                return server
            }

            private fun html(text: String): String = "<html><body><h1>$text</h1></body></html>"
        }
    }

    private data class ObservedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val headers: Map<String, String>,
        val body: String,
    )
}

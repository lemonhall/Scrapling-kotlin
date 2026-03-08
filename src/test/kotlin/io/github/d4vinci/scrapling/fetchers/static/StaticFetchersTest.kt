package io.github.d4vinci.scrapling.fetchers.static

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaticFetchersTest {
    @Test
    fun responseFactoryCreatesParserAwareResponse() {
        val raw = RawHttpResponse(
            url = "https://example.com",
            body = "<html><body><h1>Test</h1></body></html>".encodeToByteArray(),
            status = 200,
            reason = "OK",
            headers = mapOf("Content-Type" to "text/html"),
            cookies = mapOf("session" to "abc"),
            requestHeaders = mapOf("User-Agent" to "Test"),
            method = "GET",
        )

        val response = ResponseFactory.fromRaw(raw)

        assertEquals(200, response.status)
        assertEquals("https://example.com", response.url)
        assertEquals("Test", response.css("h1::text").get()?.value)
        assertEquals("abc", response.cookies["session"])
    }

    @Test
    fun fetcherClientSupportsBasicHttpVerbsThroughStubTransport() {
        val transport = RecordingTransport()
        val client = FetcherClient(transport)

        assertEquals(200, client.get("https://example.com/get").status)
        assertEquals(201, client.post("https://example.com/post", data = mapOf("key" to "value")).status)
        assertEquals(202, client.put("https://example.com/put", data = mapOf("key" to "value")).status)
        assertEquals(204, client.delete("https://example.com/delete").status)
        assertEquals(listOf("GET", "POST", "PUT", "DELETE"), transport.calls.map { it.method })
    }

    @Test
    fun fetcherSessionTracksDefaultsAndRejectsDoubleOpen() {
        val session = FetcherSession(
            transport = RecordingTransport(),
            timeout = 30,
            retries = 3,
            stealthyHeaders = true,
        )

        assertEquals(30, session.defaultTimeout)
        assertEquals(3, session.defaultRetries)
        assertTrue(session.stealthyHeaders)

        session.open()
        assertTrue(session.isOpen)
        assertFailsWith<RuntimeException> { session.open() }
        session.close()
    }

    @Test
    fun fetcherSessionUsesDefaultsWhenMakingRequests() {
        val transport = RecordingTransport()
        val session = FetcherSession(
            transport = transport,
            timeout = 45,
            retries = 5,
            stealthyHeaders = true,
        )

        session.open()
        val response = session.get("https://example.com/get")

        assertEquals(200, response.status)
        val call = transport.calls.single()
        assertEquals(45, call.options.timeout)
        assertEquals(5, call.options.retries)
        assertTrue(call.options.stealthyHeaders)
        session.close()
    }

    private class RecordingTransport : HttpTransport {
        val calls = mutableListOf<RecordedCall>()

        override fun request(method: String, url: String, options: RequestOptions): RawHttpResponse {
            calls += RecordedCall(method, url, options)
            val status = when (method) {
                "GET" -> 200
                "POST" -> 201
                "PUT" -> 202
                "DELETE" -> 204
                else -> 500
            }
            return RawHttpResponse(
                url = url,
                body = "<html><body><h1>${method.lowercase()}</h1></body></html>".encodeToByteArray(),
                status = status,
                reason = "OK",
                headers = mapOf("Content-Type" to "text/html"),
                requestHeaders = options.headers,
                method = method,
            )
        }
    }

    private data class RecordedCall(
        val method: String,
        val url: String,
        val options: RequestOptions,
    )
}

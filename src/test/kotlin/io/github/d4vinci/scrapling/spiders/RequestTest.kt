package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestTest {
    @Test
    fun basicRequestCreationMatchesDefaults() {
        val request = Request("https://example.com")

        assertEquals("https://example.com", request.url)
        assertEquals("", request.sid)
        assertEquals(0, request.priority)
        assertEquals(false, request.dontFilter)
        assertEquals(emptyMap<String, Any?>(), request.meta)
        assertEquals(0, request.retryCount)
        assertEquals(emptyMap<String, Any?>(), request.sessionOptions)
    }

    @Test
    fun requestMetaUsesIndependentMaps() {
        val first = Request("https://example.com")
        val second = Request("https://example.com")

        first.meta["key"] = "value"

        assertEquals(mapOf<String, Any?>("key" to "value"), first.meta)
        assertEquals(emptyMap<String, Any?>(), second.meta)
    }

    @Test
    fun requestDomainExtractsAuthority() {
        assertEquals("www.example.com", Request("https://www.example.com/path").domain)
        assertEquals("localhost:8080", Request("http://localhost:8080/api").domain)
    }

    @Test
    fun fingerprintIsStableAndReturnsBytes() {
        val first = Request("https://example.com", sessionOptions = mapOf("data" to mapOf("a" to "1")))
        val second = Request("https://example.com", sessionOptions = mapOf("data" to mapOf("a" to "1")))

        val left = first.updateFingerprint()
        val right = second.updateFingerprint()

        assertEquals(20, left.size)
        assertContentEquals(left, right)
    }

    @Test
    fun differentUrlsProduceDifferentFingerprints() {
        val first = Request("https://example.com/1")
        val second = Request("https://example.com/2")

        val left = first.updateFingerprint()
        val right = second.updateFingerprint()

        assertTrue(!left.contentEquals(right))
    }

    @Test
    fun copyCreatesIndependentRequest() {
        val request = Request(
            url = "https://example.com",
            sid = "session",
            priority = 5,
            dontFilter = true,
            meta = mapOf("original" to true),
            retryCount = 2,
            sessionOptions = mapOf("proxy" to "http://proxy:8080"),
        )

        val copy = request.copy()

        assertNotSame(request, copy)
        assertNotSame(request.meta, copy.meta)
        assertNotSame(request.sessionOptions, copy.sessionOptions)
        assertEquals(request.url, copy.url)
        assertEquals(request.sid, copy.sid)
        assertEquals(request.priority, copy.priority)
        assertEquals(request.dontFilter, copy.dontFilter)
        assertEquals(request.meta, copy.meta)
        assertEquals(request.retryCount, copy.retryCount)
        assertEquals(request.sessionOptions, copy.sessionOptions)
    }

    @Test
    fun equalityUsesFingerprintAfterGeneration() {
        val first = Request("https://example.com", sessionOptions = mapOf("data" to mapOf("a" to "1")))
        val second = Request("https://example.com", sessionOptions = mapOf("data" to mapOf("a" to "1")))

        first.updateFingerprint()
        second.updateFingerprint()

        assertEquals(first, second)
    }

    @Test
    fun equalityBeforeFingerprintFailsFast() {
        val first = Request("https://example.com")
        val second = Request("https://example.com")

        assertFailsWith<IllegalStateException> {
            first == second
        }
    }

    @Test
    fun requestStringRepresentationsAreUseful() {
        val request = Request("https://example.com")

        assertEquals("https://example.com", request.toString())
        assertTrue(request.debugString().contains("Request(https://example.com)"))
        assertTrue(request.debugString().contains("callback=None"))
    }

    @Test
    fun requestSerializationKeepsCallbackNameButDropsCallable() {
        val spider = object : Spider() {
            override val name: String
                get() = "request-test"
            override suspend fun parse(response: Response): Flow<SpiderOutput> = emptyFlow()
            suspend fun custom(response: Response): Flow<SpiderOutput> = emptyFlow()
        }
        val request = Request("https://example.com", callback = spider::custom)

        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { stream -> stream.writeObject(request) }
            output.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { stream ->
            stream.readObject() as Request
        }

        assertEquals("custom", restored.callbackName())
        assertNull(restored.callback)
        restored.restoreCallback(spider)
        assertEquals("custom", restored.callback?.name)
    }
}

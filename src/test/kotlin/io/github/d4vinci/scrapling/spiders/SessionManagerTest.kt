package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionManagerTest {
    private class FakeSession(
        private val label: String = "fake",
    ) : SpiderSession {
        override var isOpen: Boolean = false
            private set
        var started: Boolean = false
            private set
        var closed: Boolean = false
            private set

        override suspend fun open() {
            isOpen = true
            started = true
        }

        override suspend fun close() {
            isOpen = false
            closed = true
        }

        override suspend fun fetch(request: Request): Response = Response(
            url = request.url,
            content = label.encodeToByteArray(),
            status = 200,
            reason = "OK",
            cookies = emptyMap(),
            headers = emptyMap(),
            requestHeaders = emptyMap(),
            method = "GET",
            meta = mapOf("session" to label),
        )
    }

    @Test
    fun managerMaintainsDefaultAndRegisteredSessions() {
        val manager = SessionManager()

        manager.add("first", FakeSession())
        manager.add("second", FakeSession(), default = true)

        assertEquals("second", manager.defaultSessionId)
        assertTrue("first" in manager)
        assertTrue("second" in manager)
        assertEquals(setOf("first", "second"), manager.sessionIds.toSet())
        assertEquals(2, manager.size())
    }

    @Test
    fun duplicateRegistrationAndMissingLookupFail() {
        val manager = SessionManager()
        manager.add("one", FakeSession())

        assertFailsWith<IllegalArgumentException> {
            manager.add("one", FakeSession())
        }
        assertFailsWith<NoSuchElementException> {
            manager.get("missing")
        }
        assertFailsWith<IllegalStateException> {
            SessionManager().defaultSessionId
        }
    }

    @Test
    fun removeAndPopUpdateManagerState() {
        val manager = SessionManager()
        val first = FakeSession()
        val second = FakeSession()
        manager.add("first", first)
        manager.add("second", second)

        val popped = manager.pop("first")

        assertEquals(second, manager.get("second"))
        assertEquals(second, manager.pop("second"))
        assertEquals(first, popped)
        assertEquals(0, manager.size())
    }

    @Test
    fun startSkipsLazySessionsAndCloseStopsAll() = runTest {
        val manager = SessionManager()
        val eager = FakeSession("eager")
        val lazy = FakeSession("lazy")
        manager.add("eager", eager)
        manager.add("lazy", lazy, lazy = true)

        manager.start()
        assertTrue(eager.isOpen)
        assertTrue(!lazy.isOpen)

        manager.close()
        assertTrue(!eager.isOpen)
        assertTrue(!lazy.isOpen)
        assertTrue(eager.closed)
    }

    @Test
    fun lazySessionOpensOnFirstFetchAndMergesMeta() = runTest {
        val manager = SessionManager()
        val lazy = FakeSession("lazy")
        manager.add("lazy", lazy, default = true, lazy = true)

        val response = manager.fetch(Request("https://example.com", meta = mapOf("trace" to "ok")))

        assertTrue(lazy.isOpen)
        assertEquals("ok", response.meta["trace"])
        assertEquals("lazy", response.meta["session"])
    }
}

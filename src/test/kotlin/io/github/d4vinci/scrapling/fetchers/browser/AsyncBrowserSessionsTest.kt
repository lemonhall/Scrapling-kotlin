package io.github.d4vinci.scrapling.fetchers.browser

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AsyncBrowserSessionsTest {
    private lateinit var server: BrowserAsyncTestServer

    @BeforeTest
    fun setUp() {
        server = BrowserAsyncTestServer.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun asyncDynamicSessionSupportsConcurrentRequests() = runTest {
        val session = AsyncDynamicSession(maxPages = 3).open()
        try {
            val tasks = listOf(
                async { session.fetch(server.url("/basic")) },
                async { session.fetch(server.url("/dynamic"), BrowserFetchOptions(waitSelector = "#late")) },
                async { session.fetch(server.url("/basic")) },
            )

            assertEquals(3, session.maxPages)
            assertEquals(3, session.pagePool.maxPages)
            assertNotNull(session.context)

            val responses = tasks.awaitAll()
            assertTrue(responses.all { it.status == 200 })

            val stats = session.getPoolStats()
            assertTrue((stats["total_pages"] ?: 0) <= 3)
        } finally {
            session.close()
        }

        assertTrue(!session.isOpen)
        assertFailsWith<IllegalStateException> {
            session.fetch(server.url("/basic"))
        }
    }

    @Test
    fun asyncDynamicSessionReleasesPagesAfterEachFetch() = runTest {
        AsyncDynamicSession().open().use { session ->
            val first = session.fetch(server.url("/basic"))
            assertEquals(200, first.status)
            assertEquals(0, session.pagePool.pagesCount)

            val second = session.fetch(server.url("/dynamic"), BrowserFetchOptions(waitSelector = "#late"))
            assertEquals(200, second.status)
            assertEquals(0, session.pagePool.pagesCount)

            val stats = session.getPoolStats()
            assertEquals(0, stats["total_pages"])
            assertEquals(1, stats["max_pages"])
        }
    }

    @Test
    fun asyncStealthySessionSupportsStealthOptions() = runTest {
        AsyncStealthySession(
            defaultOptions = BrowserFetchOptions(
                headless = true,
                blockWebRtc = true,
                allowWebgl = false,
            ),
            maxPages = 1,
        ).open().use { session ->
            val response = session.fetch(
                server.url("/stealth-check"),
                BrowserFetchOptions(
                    blockWebRtc = true,
                    allowWebgl = false,
                    waitSelector = "#result",
                    waitSelectorState = WaitSelectorStateValue.VISIBLE,
                ),
            )
            assertEquals(200, response.status)
            assertEquals("webrtc-blocked|webgl-blocked", response.css("#result::text").get()?.value)
        }
    }

    @Test
    fun asyncDynamicFetcherSupportsDirectFetch() = runTest {
        val response = AsyncDynamicFetcher.fetch(
            server.url("/dynamic"),
            BrowserFetchOptions(waitSelector = "#late"),
        )

        assertEquals(200, response.status)
        assertEquals("ready", response.css("#late::text").get()?.value)
    }

    private class BrowserAsyncTestServer private constructor(
        private val server: HttpServer,
    ) : AutoCloseable {
        fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

        override fun close() {
            server.stop(0)
        }

        private fun registerContexts() {
            server.createContext("/basic") { exchange ->
                respond(exchange, 200, "<html><body><h1>basic</h1></body></html>")
            }
            server.createContext("/dynamic") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <script>
                          setTimeout(() => {
                            const node = document.createElement('div');
                            node.id = 'late';
                            node.textContent = 'ready';
                            document.body.appendChild(node);
                          }, 250);
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/stealth-check") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <div id="result"></div>
                        <canvas id="canvas"></canvas>
                        <script>
                          const canvas = document.getElementById('canvas');
                          const webRtc = typeof window.RTCPeerConnection === 'undefined' ? 'webrtc-blocked' : 'webrtc-open';
                          const webgl = canvas.getContext('webgl') === null ? 'webgl-blocked' : 'webgl-open';
                          document.getElementById('result').textContent = `${'$'}{webRtc}|${'$'}{webgl}`;
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
        }

        private fun respond(exchange: HttpExchange, status: Int, body: String) {
            val bytes = body.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        companion object {
            fun start(): BrowserAsyncTestServer {
                val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                val server = BrowserAsyncTestServer(httpServer)
                server.registerContexts()
                httpServer.start()
                return server
            }
        }
    }
}

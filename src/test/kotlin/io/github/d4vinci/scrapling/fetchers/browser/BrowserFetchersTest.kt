package io.github.d4vinci.scrapling.fetchers.browser

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrowserFetchersTest {
    private lateinit var server: BrowserTestServer

    @BeforeTest
    fun setUp() {
        server = BrowserTestServer.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun dynamicFetcherLoadsDynamicContentAfterWaitingForSelector() {
        val response = DynamicFetcher.fetch(
            server.url("/dynamic"),
            BrowserFetchOptions(
                waitSelector = "#late",
                waitSelectorState = WaitSelectorStateValue.VISIBLE,
            ),
        )

        assertEquals(200, response.status)
        assertEquals("ready", response.css("#late::text").get()?.value)
    }

    @Test
    fun dynamicFetcherSupportsHeadlessAndHeadfulLaunches() {
        val headlessResponse = DynamicFetcher.fetch(
            server.url("/basic"),
            BrowserFetchOptions(headless = true),
        )
        val headedResponse = DynamicFetcher.fetch(
            server.url("/basic"),
            BrowserFetchOptions(headless = false),
        )

        assertEquals(200, headlessResponse.status)
        assertEquals(200, headedResponse.status)
        assertEquals("basic", headedResponse.css("h1::text").get()?.value)
    }

    @Test
    fun dynamicFetcherCanDisableBackgroundResources() {
        server.resetImageHits()
        DynamicFetcher.fetch(server.url("/resource-page"), BrowserFetchOptions(disableResources = false))
        val regularHits = server.imageHits()

        server.resetImageHits()
        val blockedResponse = DynamicFetcher.fetch(
            server.url("/resource-page"),
            BrowserFetchOptions(disableResources = true),
        )
        val blockedHits = server.imageHits()

        assertTrue(regularHits >= 1)
        assertEquals(0, blockedHits)
        assertEquals(200, blockedResponse.status)
        assertEquals(1, blockedResponse.meta["blockedRequests"])
    }

    @Test
    fun stealthyFetcherAppliesStealthScripts() {
        val response = StealthyFetcher.fetch(
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

    @Test
    fun dynamicSessionReusesBrowserContextAndRejectsDoubleOpen() {
        val session = DynamicSession(BrowserFetchOptions())
        session.open()
        try {
            assertTrue(session.isOpen)
            assertFailsWith<IllegalStateException> { session.open() }
            val first = session.fetch(server.url("/basic"))
            val second = session.fetch(server.url("/basic"))

            assertEquals(200, first.status)
            assertEquals(200, second.status)
            assertNotNull(session.context)
        } finally {
            session.close()
        }
    }

    private class BrowserTestServer private constructor(
        private val server: HttpServer,
        private val imageCounter: AtomicInteger,
    ) : AutoCloseable {
        fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

        fun imageHits(): Int = imageCounter.get()

        fun resetImageHits() {
            imageCounter.set(0)
        }

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
                        <div id="placeholder">loading</div>
                        <script>
                          setTimeout(() => {
                            const node = document.createElement('div');
                            node.id = 'late';
                            node.textContent = 'ready';
                            document.body.appendChild(node);
                          }, 300);
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/resource-page") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <h1>resource</h1>
                        <img src="/assets/pixel.png" alt="pixel" />
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/assets/pixel.png") { exchange ->
                imageCounter.incrementAndGet()
                val png = byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                    0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
                    0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
                    0x54, 0x78, 0x9C.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(),
                    0x00, 0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(),
                    0x8D.toByte(), 0xB1.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49,
                    0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
                )
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, png.size.toLong())
                exchange.responseBody.use { it.write(png) }
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
            fun start(): BrowserTestServer {
                val counter = AtomicInteger(0)
                val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                val server = BrowserTestServer(httpServer, counter)
                server.registerContexts()
                httpServer.start()
                return server
            }
        }
    }
}

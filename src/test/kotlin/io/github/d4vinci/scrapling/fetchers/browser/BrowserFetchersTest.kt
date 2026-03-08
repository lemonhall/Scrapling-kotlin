package io.github.d4vinci.scrapling.fetchers.browser

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import io.github.d4vinci.scrapling.fetchers.static.SelectorConfig
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
    fun dynamicFetcherSupportsPageActionAutomation() {
        val response = DynamicFetcher.fetch(
            server.url("/action-page"),
            BrowserFetchOptions(
                pageAction = { page -> page.locator("#trigger").click() },
                waitSelector = "#action-result",
                waitSelectorState = WaitSelectorStateValue.VISIBLE,
            ),
        )

        assertEquals(200, response.status)
        assertEquals("clicked", response.css("#action-result::text").get()?.value)
    }

    @Test
    fun dynamicFetcherSupportsExtraHeadersCookiesAndNetworkIdle() {
        val headerResponse = DynamicFetcher.fetch(
            server.url("/echo-header"),
            BrowserFetchOptions(extraHeaders = mapOf("X-Test" to "browser-header")),
        )
        val cookieResponse = DynamicFetcher.fetch(
            server.url("/cookie-page"),
            BrowserFetchOptions(
                cookies = listOf(
                    BrowserCookie(
                        name = "session",
                        value = "browser-cookie",
                        domain = "127.0.0.1",
                    ),
                ),
            ),
        )
        val idleResponse = DynamicFetcher.fetch(
            server.url("/idle-page"),
            BrowserFetchOptions(networkIdle = true),
        )

        assertEquals("browser-header", headerResponse.css("h1::text").get()?.value)
        assertEquals("session=browser-cookie", cookieResponse.css("h1::text").get()?.value)
        assertEquals("idle-ready", idleResponse.css("#idle::text").get()?.value)
    }

    @Test
    fun dynamicFetcherSupportsWaitAliasAndRetriesAfterTimeout() {
        server.resetRetryTimeoutHits()

        val waitResponse = DynamicFetcher.fetch(
            server.url("/wait-page"),
            BrowserFetchOptions(wait = 250.0),
        )
        val retryResponse = DynamicFetcher.fetch(
            server.url("/retry-timeout"),
            BrowserFetchOptions(
                timeout = 200.0,
                retries = 2,
                retryDelay = 50.0,
            ),
        )

        assertEquals("wait-ready", waitResponse.css("#wait-result::text").get()?.value)
        assertEquals("retry-recovered", retryResponse.css("h1::text").get()?.value)
        assertEquals(2, server.retryTimeoutHits())
    }

    @Test
    fun dynamicFetcherSupportsInitScriptGoogleRefererAndSelectorConfig() {
        val initScript = Files.createTempFile("scrapling-browser-init", ".js")
        try {
            Files.writeString(initScript, "window.__SCRAPLING_INIT='ready';")
            val response = DynamicFetcher.fetch(
                server.url("/context-check"),
                BrowserFetchOptions(
                    initScript = initScript.toString(),
                    googleSearch = true,
                    selectorConfig = SelectorConfig(adaptiveDomain = "https://override.example"),
                ),
            )

            assertEquals("ready", response.css("#init::text").get()?.value)
            assertTrue((response.css("#referer::text").get()?.value ?: "").contains("google"))
            assertEquals("https://override.example", response.css("h1").first()?.url)
        } finally {
            Files.deleteIfExists(initScript)
        }
    }

    @Test
    fun dynamicFetcherSupportsPersistentUserDataDir() {
        val userDataDir = Files.createTempDirectory("scrapling-browser-profile")
        try {
            DynamicFetcher.fetch(
                server.url("/storage-page"),
                BrowserFetchOptions(
                    userDataDir = userDataDir.toString(),
                    pageAction = { page -> page.evaluate("localStorage.setItem('persisted', 'yes')") },
                ),
            )

            val response = DynamicFetcher.fetch(
                server.url("/storage-page"),
                BrowserFetchOptions(
                    userDataDir = userDataDir.toString(),
                    waitSelector = "#storage",
                    waitSelectorState = WaitSelectorStateValue.VISIBLE,
                ),
            )

            assertEquals("yes", response.css("#storage::text").get()?.value)
        } finally {
            userDataDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun dynamicFetcherSupportsAdditionalContextArgsViewport() {
        val response = DynamicFetcher.fetch(
            server.url("/viewport-check"),
            BrowserFetchOptions(additionalArgs = mapOf("viewportWidth" to 700, "viewportHeight" to 500)),
        )

        assertEquals("700x500", response.css("#viewport::text").get()?.value)
    }

    @Test
    fun dynamicFetcherRejectsInvalidCdpUrl() {
        assertFailsWith<IllegalArgumentException> {
            DynamicFetcher.fetch(server.url("/basic"), BrowserFetchOptions(cdpUrl = "blahblah"))
        }
        assertFailsWith<Exception> {
            DynamicFetcher.fetch(server.url("/basic"), BrowserFetchOptions(cdpUrl = "ws://blahblah"))
        }
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
    fun stealthyFetcherMasksNavigatorWebdriver() {
        val response = StealthyFetcher.fetch(
            server.url("/webdriver-check"),
            BrowserFetchOptions(waitSelector = "#webdriver"),
        )

        assertEquals(200, response.status)
        assertEquals("undefined", response.css("#webdriver::text").get()?.value)
    }

    @Test
    fun stealthyFetcherSolvesManagedCloudflareChallenge() {
        val response = StealthyFetcher.fetch(
            server.url("/cf-managed"),
            BrowserFetchOptions(
                solveCloudflare = true,
                waitSelector = "#challenge-result",
                waitSelectorState = WaitSelectorStateValue.VISIBLE,
            ),
        )

        assertEquals(200, response.status)
        assertEquals("managed-passed", response.css("#challenge-result::text").get()?.value)
    }

    @Test
    fun stealthyFetcherWaitsOutNonInteractiveCloudflareChallenge() {
        val response = StealthyFetcher.fetch(
            server.url("/cf-non-interactive"),
            BrowserFetchOptions(
                solveCloudflare = true,
                waitSelector = "#challenge-result",
                waitSelectorState = WaitSelectorStateValue.VISIBLE,
            ),
        )

        assertEquals(200, response.status)
        assertEquals("non-interactive-passed", response.css("#challenge-result::text").get()?.value)
    }

    @Test
    fun stealthySessionDetectsCloudflareMarkers() {
        val matchingUrls = listOf(
            "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/123456",
            "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/orchestrate/jsch/v1",
            "http://challenges.cloudflare.com/cdn-cgi/challenge-platform/scripts/abc",
        )
        val nonMatchingUrls = listOf(
            "https://example.com/challenge",
            "https://cloudflare.com/something",
            "https://challenges.cloudflare.com/other-path",
        )

        matchingUrls.forEach { url ->
            assertTrue(StealthySession.cloudflarePattern.matches(url))
        }
        nonMatchingUrls.forEach { url ->
            assertTrue(!StealthySession.cloudflarePattern.matches(url))
        }

        assertEquals(
            "managed",
            StealthySession.detectCloudflare(
                """
                <html>
                    <script>
                        cType: 'managed'
                    </script>
                </html>
                """.trimIndent(),
            ),
        )
        assertEquals(
            "embedded",
            StealthySession.detectCloudflare(
                """
                <html>
                    <script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script>
                </html>
                """.trimIndent(),
            ),
        )
        assertEquals(null, StealthySession.detectCloudflare("<html><body><p>Regular page</p></body></html>"))
    }

    @Test
    fun dynamicSessionReusesBrowserContextAndRejectsDoubleOpen() {
        val session = DynamicSession(BrowserFetchOptions())
        session.open()
        try {
            assertTrue(session.isOpen)
            assertFailsWith<IllegalStateException> { session.open() }
            val pageIds = mutableListOf<Int>()
            val first = session.fetch(
                server.url("/basic"),
                BrowserFetchOptions(pageAction = { page -> pageIds += System.identityHashCode(page) }),
            )
            val second = session.fetch(
                server.url("/basic"),
                BrowserFetchOptions(pageAction = { page -> pageIds += System.identityHashCode(page) }),
            )

            assertEquals(200, first.status)
            assertEquals(200, second.status)
            assertNotNull(session.context)
            assertEquals(2, pageIds.size)
            assertEquals(pageIds.first(), pageIds.last())
            assertEquals(1, session.pagePool.pagesCount)
            assertEquals(0, session.pagePool.busyCount)
            assertEquals(1, session.getPoolStats()["total_pages"])
        } finally {
            session.close()
        }
    }

    private class BrowserTestServer private constructor(
        private val server: HttpServer,
        private val imageCounter: AtomicInteger,
        private val retryTimeoutCounter: AtomicInteger,
    ) : AutoCloseable {
        fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

        fun imageHits(): Int = imageCounter.get()

        fun retryTimeoutHits(): Int = retryTimeoutCounter.get()

        fun resetImageHits() {
            imageCounter.set(0)
        }

        fun resetRetryTimeoutHits() {
            retryTimeoutCounter.set(0)
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
            server.createContext("/action-page") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <button id="trigger" onclick="document.getElementById('action-result').textContent='clicked'">Go</button>
                        <div id="action-result"></div>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/echo-header") { exchange ->
                val value = exchange.requestHeaders.getFirst("X-Test") ?: "missing"
                respond(exchange, 200, "<html><body><h1>$value</h1></body></html>")
            }
            server.createContext("/cookie-page") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <script>
                          document.body.innerHTML = `<h1>${'$'}{document.cookie}</h1>`;
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/idle-page") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <div id="idle">loading</div>
                        <script>
                          fetch('/idle-data')
                            .then((response) => response.text())
                            .then((text) => {
                              document.getElementById('idle').textContent = text;
                            });
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/idle-data") { exchange ->
                Thread.sleep(300)
                respond(exchange, 200, "idle-ready")
            }
            server.createContext("/wait-page") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <div id="wait-result">loading</div>
                        <script>
                          setTimeout(() => {
                            document.getElementById('wait-result').textContent = 'wait-ready';
                          }, 150);
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/retry-timeout") { exchange ->
                val attempt = retryTimeoutCounter.incrementAndGet()
                if (attempt == 1) {
                    Thread.sleep(400)
                }
                respond(exchange, 200, "<html><body><h1>retry-recovered</h1></body></html>")
            }
            server.createContext("/context-check") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <h1>context</h1>
                        <div id="init"></div>
                        <div id="referer"></div>
                        <script>
                          document.getElementById('init').textContent = window.__SCRAPLING_INIT || 'missing';
                          document.getElementById('referer').textContent = document.referrer || 'missing';
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/storage-page") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <div id="storage"></div>
                        <script>
                          document.getElementById('storage').textContent = localStorage.getItem('persisted') || 'missing';
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/viewport-check") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <div id="viewport"></div>
                        <script>
                          document.getElementById('viewport').textContent = `${'$'}{window.innerWidth}x${'$'}{window.innerHeight}`;
                        </script>
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
            server.createContext("/webdriver-check") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <body>
                        <div id="webdriver"></div>
                        <script>
                          const value = navigator.webdriver === undefined ? 'undefined' : String(navigator.webdriver);
                          document.getElementById('webdriver').textContent = value;
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/cf-managed") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <head><title>Just a moment...</title></head>
                      <body>
                        <div id="cf-marker">cType: 'managed'</div>
                        <div class="main-content">
                          <p>Verifying you are human.</p>
                          <div>
                            <div>
                              <div id="cf-box" style="width:120px;height:40px;border:1px solid #333;">verify</div>
                            </div>
                          </div>
                        </div>
                        <div id="challenge-result"></div>
                        <script>
                          const box = document.getElementById('cf-box');
                          box.addEventListener('click', () => {
                            document.title = 'Solved';
                            document.getElementById('cf-marker').remove();
                            document.querySelector('.main-content').remove();
                            document.getElementById('challenge-result').textContent = 'managed-passed';
                          });
                        </script>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }
            server.createContext("/cf-non-interactive") { exchange ->
                respond(
                    exchange,
                    200,
                    """
                    <html>
                      <head><title>Just a moment...</title></head>
                      <body>
                        <div id="cf-marker">cType: 'non-interactive'</div>
                        <div id="challenge-result"></div>
                        <script>
                          setTimeout(() => {
                            document.title = 'Solved';
                            document.getElementById('cf-marker').remove();
                            document.getElementById('challenge-result').textContent = 'non-interactive-passed';
                          }, 300);
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
                val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                val server = BrowserTestServer(httpServer, AtomicInteger(0), AtomicInteger(0))
                server.registerContexts()
                httpServer.start()
                return server
            }
        }
    }
}

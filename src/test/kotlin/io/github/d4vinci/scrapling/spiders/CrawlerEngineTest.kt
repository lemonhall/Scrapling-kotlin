package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrawlerEngineTest {
    private class RecordingSession(
        private val responseProvider: suspend (Request) -> Response = { request -> htmlResponse(request.url) },
        private val failureProvider: suspend (Request) -> Exception? = { null },
        private val fetchDelayMillis: Long = 0L,
    ) : SpiderSession {
        override var isOpen: Boolean = false
            private set

        private val inFlight = AtomicInteger(0)
        private val peakInFlight = AtomicInteger(0)

        val maxConcurrentFetches: Int
            get() = peakInFlight.get()

        override suspend fun open() {
            isOpen = true
        }

        override suspend fun close() {
            isOpen = false
        }

        override suspend fun fetch(request: Request): Response {
            check(isOpen) { "Session must be open before fetch." }
            val concurrent = inFlight.incrementAndGet()
            peakInFlight.updateAndGet { current -> maxOf(current, concurrent) }
            try {
                if (fetchDelayMillis > 0) {
                    delay(fetchDelayMillis)
                }
                failureProvider(request)?.let { throw it }
                return responseProvider(request)
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private class TestSpider(
        private val session: SpiderSession = RecordingSession(),
        private val crawlDirectory: Path? = null,
        private val startUrlValues: List<String> = emptyList(),
        private val allowedDomainValues: Set<String> = emptySet(),
        private val concurrentRequestValue: Int = 4,
        private val concurrentRequestPerDomainValue: Int = 0,
        private val downloadDelayValue: Double = 0.0,
        private val maxBlockedRetryValue: Int = 3,
    ) : Spider(crawldir = crawlDirectory) {
        override val name: String
            get() = "engine_test_spider"
        override val startUrls: List<String>
            get() = startUrlValues
        override val allowedDomains: Set<String>
            get() = allowedDomainValues
        override val concurrentRequests: Int
            get() = concurrentRequestValue
        override val concurrentRequestsPerDomain: Int
            get() = concurrentRequestPerDomainValue
        override val downloadDelay: Double
            get() = downloadDelayValue
        override val maxBlockedRetries: Int
            get() = maxBlockedRetryValue

        var parseHandler: suspend (Response) -> Flow<SpiderOutput> = { response ->
            flowOf(item(mapOf("url" to response.url)))
        }
        var onScrapedItemHandler: suspend (Map<String, Any?>) -> Map<String, Any?>? = { scrapedItem -> scrapedItem }
        var onBlockedHandler: suspend (Response) -> Boolean = { false }
        var retryBlockedRequestHandler: suspend (Request, Response) -> Request = { request, _ -> request }
        val errors: MutableList<Pair<Request, Exception>> = mutableListOf()
        var onStartResuming: Boolean? = null
        var closed: Boolean = false

        init {
            sessionManager.pop("default")
            sessionManager.add("default", session, default = true)
        }

        override suspend fun parse(response: Response): Flow<SpiderOutput> = parseHandler(response)

        override suspend fun onStart(resuming: Boolean) {
            onStartResuming = resuming
        }

        override suspend fun onClose() {
            closed = true
        }

        override suspend fun onError(request: Request, error: Exception) {
            errors += request to error
        }

        override suspend fun onScrapedItem(item: Map<String, Any?>): Map<String, Any?>? = onScrapedItemHandler(item)

        override suspend fun isBlocked(response: Response): Boolean = onBlockedHandler(response)

        override suspend fun retryBlockedRequest(request: Request, response: Response): Request =
            retryBlockedRequestHandler(request, response)
    }

    @Test
    fun dumpProducesPrettyJson() {
        val result = dump(linkedMapOf("key" to "value", "nested" to mapOf("answer" to 42)))

        assertTrue(result.contains("\"key\": \"value\""))
        assertTrue(result.contains("\"nested\""))
        assertTrue(result.contains("\n"))
        assertTrue(result.contains("    \"answer\": 42"))
    }

    @Test
    fun engineInitializesDefaultState() {
        val spider = TestSpider(
            allowedDomainValues = setOf("example.com", "test.org"),
            concurrentRequestValue = 8,
        )

        val engine = CrawlerEngine(spider, spider.sessionManager)

        assertFalse(engine.running)
        assertEquals(0, engine.activeTasks)
        assertFalse(engine.pauseRequested)
        assertFalse(engine.forceStop)
        assertFalse(engine.paused)
        assertFalse(engine.checkpointSystemEnabled)
        assertEquals(setOf("example.com", "test.org"), engine.allowedDomains)
        assertEquals(8, engine.globalLimiter.totalTokens)
        assertTrue(engine.items.isEmpty())
    }

    @Test
    fun domainAllowanceMatchesExactAndSubdomainRules() {
        val spider = TestSpider(allowedDomainValues = setOf("example.com", "b.org"))
        val engine = CrawlerEngine(spider, spider.sessionManager)

        assertTrue(engine.isDomainAllowed(Request("https://example.com/page")))
        assertTrue(engine.isDomainAllowed(Request("https://sub.example.com/page")))
        assertTrue(engine.isDomainAllowed(Request("https://deep.sub.example.com/page")))
        assertTrue(engine.isDomainAllowed(Request("https://b.org/page")))
        assertFalse(engine.isDomainAllowed(Request("https://notexample.com/page")))
        assertFalse(engine.isDomainAllowed(Request("https://other.net/page")))
    }

    @Test
    fun rateLimiterUsesGlobalLimiterWhenPerDomainDisabled() {
        val spider = TestSpider(concurrentRequestValue = 6)
        val engine = CrawlerEngine(spider, spider.sessionManager)

        val limiter = engine.rateLimiter("example.com")

        assertSame(engine.globalLimiter, limiter)
        assertEquals(6, limiter.totalTokens)
    }

    @Test
    fun rateLimiterReusesPerDomainLimiterWhenEnabled() {
        val spider = TestSpider(concurrentRequestPerDomainValue = 2)
        val engine = CrawlerEngine(spider, spider.sessionManager)

        val first = engine.rateLimiter("example.com")
        val second = engine.rateLimiter("example.com")
        val third = engine.rateLimiter("another.org")

        assertEquals(2, first.totalTokens)
        assertSame(first, second)
        assertTrue(first !== third)
    }

    @Test
    fun normalizeRequestSetsDefaultSidOnlyWhenMissing() {
        val spider = TestSpider()
        val engine = CrawlerEngine(spider, spider.sessionManager)
        val missing = Request("https://example.com")
        val existing = Request("https://example.com", sid = "custom")

        engine.normalizeRequest(missing)
        engine.normalizeRequest(existing)

        assertEquals("default", missing.sid)
        assertEquals("custom", existing.sid)
    }

    @Test
    fun requestPauseEscalatesToForceStopOnSecondCall() {
        val spider = TestSpider()
        val engine = CrawlerEngine(spider, spider.sessionManager)

        engine.requestPause()
        assertTrue(engine.pauseRequested)
        assertFalse(engine.forceStop)

        engine.requestPause()
        assertTrue(engine.pauseRequested)
        assertTrue(engine.forceStop)

        engine.requestPause()
        assertTrue(engine.forceStop)
    }

    @Test
    fun checkpointTimingRespectsEnablementAndInterval() {
        val disabledSpider = TestSpider()
        val disabledEngine = CrawlerEngine(disabledSpider, disabledSpider.sessionManager)
        assertFalse(disabledEngine.isCheckpointTime())

        val dir = Files.createTempDirectory("scrapling-engine-zero")
        val zeroSpider = TestSpider(crawlDirectory = dir)
        val zeroEngine = CrawlerEngine(zeroSpider, zeroSpider.sessionManager, dir, interval = 0.0)
        zeroEngine.lastCheckpointTime = (System.currentTimeMillis() / 1000.0) - 100.0
        assertFalse(zeroEngine.isCheckpointTime())

        val enabledDir = Files.createTempDirectory("scrapling-engine-enabled")
        val enabledSpider = TestSpider(crawlDirectory = enabledDir)
        val enabledEngine = CrawlerEngine(enabledSpider, enabledSpider.sessionManager, enabledDir, interval = 10.0)
        enabledEngine.lastCheckpointTime = (System.currentTimeMillis() / 1000.0) - 11.0
        assertTrue(enabledEngine.isCheckpointTime())
    }

    @Test
    fun saveAndRestoreCheckpointRoundTripsRequestsAndCallbacks() = runTest {
        val dir = Files.createTempDirectory("scrapling-engine-checkpoint")
        val spider = TestSpider(crawlDirectory = dir)
        val engine = CrawlerEngine(spider, spider.sessionManager, dir)
        val request = Request(
            url = "https://example.com/page",
            callback = spider::parse,
        )

        engine.normalizeRequest(request)
        engine.scheduler.enqueue(request)
        engine.saveCheckpoint()

        val checkpointPath = dir.resolve(CheckpointManager.CHECKPOINT_FILE)
        assertTrue(checkpointPath.exists())

        val restoredEngine = CrawlerEngine(spider, spider.sessionManager, dir)
        assertTrue(restoredEngine.restoreFromCheckpoint())

        val restored = restoredEngine.scheduler.dequeue()
        assertEquals("https://example.com/page", restored.url)
        assertEquals("default", restored.sid)
        assertEquals("parse", restored.callback?.name)
    }

    @Test
    fun restoreFromCheckpointFailsWhenDisabled() = runTest {
        val spider = TestSpider()
        val engine = CrawlerEngine(spider, spider.sessionManager)

        assertFailsWith<IllegalStateException> {
            engine.restoreFromCheckpoint()
        }
    }

    @Test
    fun processRequestSuccessUpdatesStatsAndStoresItems() = runTest {
        val spider = TestSpider()
        val engine = CrawlerEngine(spider, spider.sessionManager)
        val request = Request("https://example.com/page", sid = "default")

        spider.sessionManager.start()
        engine.processRequest(request)
        spider.sessionManager.close()

        assertEquals(1, engine.stats.requestsCount)
        assertEquals(44, engine.stats.responseBytes)
        assertEquals(1, engine.stats.itemsScraped)
        assertEquals(0, engine.stats.failedRequestsCount)
        assertEquals(1, engine.stats.responseStatusCount["status_200"])
        assertEquals(1, engine.items.size)
        assertEquals("https://example.com/page", engine.items.single()["url"])
    }

    @Test
    fun processRequestFailureCallsOnErrorAndKeepsRequestCountZero() = runTest {
        val spider = TestSpider(
            session = RecordingSession(failureProvider = { IllegalStateException("boom") }),
        )
        val engine = CrawlerEngine(spider, spider.sessionManager)
        val request = Request("https://example.com/page", sid = "default")

        spider.sessionManager.start()
        engine.processRequest(request)
        spider.sessionManager.close()

        assertEquals(0, engine.stats.requestsCount)
        assertEquals(1, engine.stats.failedRequestsCount)
        assertEquals(1, spider.errors.size)
        assertTrue(spider.errors.single().second is IllegalStateException)
    }

    @Test
    fun blockedResponseEnqueuesRetryAndClearsProxySettings() = runTest {
        val spider = TestSpider(maxBlockedRetryValue = 3)
        spider.onBlockedHandler = { true }
        spider.retryBlockedRequestHandler = { request, _ -> request }
        val engine = CrawlerEngine(spider, spider.sessionManager)
        val request = Request(
            url = "https://example.com/page",
            sid = "default",
            sessionOptions = mapOf(
                "proxy" to "http://proxy:8080",
                "proxies" to mapOf("https" to "http://proxy:8080"),
            ),
        )

        spider.sessionManager.start()
        engine.processRequest(request)
        spider.sessionManager.close()

        assertEquals(1, engine.stats.blockedRequestsCount)
        assertFalse(engine.scheduler.isEmpty)
        val retry = engine.scheduler.dequeue()
        assertTrue(retry.dontFilter)
        assertEquals(1, retry.retryCount)
        assertFalse("proxy" in retry.sessionOptions)
        assertFalse("proxies" in retry.sessionOptions)
    }

    @Test
    fun offsiteFollowUpIsFilteredAndCounted() = runTest {
        val spider = TestSpider(allowedDomainValues = setOf("example.com"))
        spider.parseHandler = {
            flow {
                emit(follow(Request("https://other.net/page", sid = "default")))
            }
        }
        val engine = CrawlerEngine(spider, spider.sessionManager)
        val request = Request("https://example.com/page", sid = "default")

        spider.sessionManager.start()
        engine.processRequest(request)
        spider.sessionManager.close()

        assertEquals(1, engine.stats.offsiteRequestsCount)
        assertTrue(engine.scheduler.isEmpty)
    }

    @Test
    fun droppedItemsIncrementDroppedCounter() = runTest {
        val spider = TestSpider()
        spider.onScrapedItemHandler = { null }
        val engine = CrawlerEngine(spider, spider.sessionManager)
        val request = Request("https://example.com/page", sid = "default")

        spider.sessionManager.start()
        engine.processRequest(request)
        spider.sessionManager.close()

        assertEquals(1, engine.stats.itemsDropped)
        assertEquals(0, engine.stats.itemsScraped)
        assertTrue(engine.items.isEmpty())
    }

    @Test
    fun crawlRunsHooksProcessesFollowUpsAndResetsSessionState() = runTest {
        var parseCount = 0
        val spider = TestSpider(
            startUrlValues = listOf("https://example.com/1"),
        )
        spider.parseHandler = { response ->
            flow {
                parseCount += 1
                if (parseCount == 1) {
                    emit(follow(Request("https://example.com/2", sid = "default")))
                }
                emit(item(mapOf("url" to response.url, "count" to parseCount)))
            }
        }
        val engine = CrawlerEngine(spider, spider.sessionManager)

        val stats = engine.crawl()

        assertFalse(engine.running)
        assertFalse(engine.paused)
        assertFalse(spider.sessionManager.started)
        assertEquals(false, spider.onStartResuming)
        assertTrue(spider.closed)
        assertEquals(2, stats.requestsCount)
        assertEquals(2, stats.itemsScraped)
        assertEquals(2, engine.items.size)
    }

    @Test
    fun crawlUsesRealConcurrencyInsteadOfSingleThreadHappyPath() = runTest {
        val session = RecordingSession(fetchDelayMillis = 150)
        val spider = TestSpider(
            session = session,
            startUrlValues = listOf(
                "https://example.com/1",
                "https://example.com/2",
            ),
            concurrentRequestValue = 2,
        )
        val engine = CrawlerEngine(spider, spider.sessionManager)

        val stats = engine.crawl()

        assertEquals(2, stats.requestsCount)
        assertEquals(2, stats.itemsScraped)
        assertTrue(session.maxConcurrentFetches >= 2)
    }

    @Test
    fun streamYieldsItemsWithoutPersistingItemStore() = runTest {
        val spider = TestSpider(
            startUrlValues = listOf(
                "https://example.com/1",
                "https://example.com/2",
            ),
        )
        val engine = CrawlerEngine(spider, spider.sessionManager)

        val items = engine.stream().toList()

        assertEquals(2, items.size)
        assertEquals(0, engine.items.size)
        assertEquals("https://example.com/1", items.first()["url"])
    }

    @Test
    fun pauseWithCheckpointLeavesCheckpointAndMarksPaused() = runTest {
        val dir = Files.createTempDirectory("scrapling-engine-paused")
        lateinit var engine: CrawlerEngine
        var parseCount = 0
        val spider = TestSpider(
            crawlDirectory = dir,
            startUrlValues = listOf("https://example.com/1"),
        )
        spider.parseHandler = {
            flow {
                parseCount += 1
                if (parseCount == 1) {
                    engine.requestPause()
                    emit(follow(Request("https://example.com/2", sid = "default")))
                }
                emit(item(mapOf("count" to parseCount)))
            }
        }
        engine = CrawlerEngine(spider, spider.sessionManager, dir)

        engine.crawl()

        assertTrue(engine.paused)
        assertTrue(dir.resolve(CheckpointManager.CHECKPOINT_FILE).exists())
    }

    companion object {
        private fun htmlResponse(url: String, status: Int = 200, body: String = "<html><body>payload data bytes</body></html>"): Response = Response(
            url = url,
            content = body.toByteArray(Charsets.UTF_8),
            status = status,
            reason = "OK",
            cookies = emptyMap(),
            headers = mapOf("content-type" to "text/html"),
            requestHeaders = emptyMap(),
            method = "GET",
        )
    }
}

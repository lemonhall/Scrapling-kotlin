package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CrawlerEngineTest {
    private class TestSpider(
        private val crawlDirectory: Path? = null,
        private val startUrlValues: List<String> = emptyList(),
        private val allowedDomainValues: Set<String> = emptySet(),
        private val concurrentRequestValue: Int = 4,
        private val concurrentRequestPerDomainValue: Int = 0,
        private val downloadDelayValue: Double = 0.0,
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

        var onStartResuming: Boolean? = null
        var closed: Boolean = false

        override suspend fun parse(response: Response): Flow<SpiderOutput> = emptyFlow()

        override suspend fun onStart(resuming: Boolean) {
            onStartResuming = resuming
        }

        override suspend fun onClose() {
            closed = true
        }
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
    fun crawlInitializesStatsRunsHooksAndCountsOffsiteRequests() = runTest {
        val spider = TestSpider(
            startUrlValues = listOf(
                "https://example.com/page",
                "https://offsite.net/page",
            ),
            allowedDomainValues = setOf("example.com"),
            concurrentRequestValue = 5,
            concurrentRequestPerDomainValue = 2,
            downloadDelayValue = 0.25,
        )
        val engine = CrawlerEngine(spider, spider.sessionManager)

        val stats = engine.crawl()

        assertFalse(engine.running)
        assertFalse(engine.paused)
        assertFalse(spider.sessionManager.started)
        assertEquals(false, spider.onStartResuming)
        assertTrue(spider.closed)
        assertEquals(5, stats.concurrentRequests)
        assertEquals(2, stats.concurrentRequestsPerDomain)
        assertEquals(0.25, stats.downloadDelay)
        assertEquals(1, stats.requestsCount)
        assertEquals(1, stats.offsiteRequestsCount)
        assertEquals(1, stats.sessionsRequestsCount["default"])
    }
}

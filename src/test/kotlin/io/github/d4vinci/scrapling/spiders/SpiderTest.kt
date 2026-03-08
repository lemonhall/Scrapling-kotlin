package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpiderTest {
    private class ConcreteSpider(
        crawldir: java.nio.file.Path? = null,
    ) : Spider(crawldir = crawldir) {
        override val name: String
            get() = "test_spider"
        override val startUrls: List<String> = listOf("https://example.com")

        override suspend fun parse(response: Response): Flow<SpiderOutput> = emptyFlow()
    }

    @Test
    fun logCounterTracksLevels() {
        val counter = LogCounterHandler()

        counter.publish(java.util.logging.LogRecord(SpiderLevels.DEBUG, "debug"))
        counter.publish(java.util.logging.LogRecord(SpiderLevels.INFO, "info"))
        counter.publish(java.util.logging.LogRecord(SpiderLevels.WARNING, "warn"))
        counter.publish(java.util.logging.LogRecord(SpiderLevels.ERROR, "error"))
        counter.publish(java.util.logging.LogRecord(SpiderLevels.CRITICAL, "critical"))

        assertEquals(1, counter.getCounts()["debug"])
        assertEquals(1, counter.getCounts()["info"])
        assertEquals(1, counter.getCounts()["warning"])
        assertEquals(1, counter.getCounts()["error"])
        assertEquals(1, counter.getCounts()["critical"])
    }

    @Test
    fun blockedCodesContainExpectedValues() {
        assertTrue(401 in BLOCKED_CODES)
        assertTrue(429 in BLOCKED_CODES)
        assertTrue(503 in BLOCKED_CODES)
        assertTrue(200 !in BLOCKED_CODES)
    }

    @Test
    fun spiderRequiresNameAndConfiguresLogger() {
        val spider = ConcreteSpider()

        assertEquals("test_spider", spider.name)
        assertEquals(false, spider.logger.useParentHandlers)
        assertEquals(4, spider.concurrentRequests)
        assertEquals(0, spider.concurrentRequestsPerDomain)
        assertEquals(0.0, spider.downloadDelay)
        assertEquals(3, spider.maxBlockedRetries)
        assertEquals(emptySet(), spider.allowedDomains)
        assertTrue(spider.toString().contains("ConcreteSpider"))
        assertTrue(spider.toString().contains("test_spider"))
    }

    @Test
    fun spiderUsesDefaultSessionConfiguration() {
        val spider = ConcreteSpider()

        assertEquals("default", spider.sessionManager.defaultSessionId)
        assertEquals(1, spider.sessionManager.size())
    }

    @Test
    fun spiderWrapsSessionConfigurationErrors() {
        class BrokenSpider : Spider() {
            override val name: String
                get() = "broken"
            override fun configureSessions(manager: SessionManager) {
                error("Configuration failed")
            }

            override suspend fun parse(response: Response): Flow<SpiderOutput> = emptyFlow()
        }

        val error = assertFailsWith<SessionConfigurationError> {
            BrokenSpider()
        }
        assertTrue(error.message!!.contains("Configuration failed"))
    }

    @Test
    fun spiderRejectsEmptySessionConfiguration() {
        class EmptySpider : Spider() {
            override val name: String
                get() = "empty"
            override fun configureSessions(manager: SessionManager) = Unit
            override suspend fun parse(response: Response): Flow<SpiderOutput> = emptyFlow()
        }

        val error = assertFailsWith<SessionConfigurationError> {
            EmptySpider()
        }
        assertTrue(error.message!!.contains("did not add any sessions"))
    }

    @Test
    fun startRequestsUseDefaultSessionAndParseCallback() = runBlocking {
        val spider = ConcreteSpider()
        val requests = spider.startRequests().toList()

        assertEquals(1, requests.size)
        assertEquals("default", requests.single().sid)
        assertEquals("parse", requests.single().callback?.name)
    }

    @Test
    fun spiderPauseAndStatsFailWithoutActiveEngine() {
        val spider = ConcreteSpider()

        assertFailsWith<IllegalStateException> {
            spider.pause()
        }
        assertFailsWith<IllegalStateException> {
            spider.stats
        }
    }

    @Test
    fun spiderSupportsCrawldirAndLogFileHandlers() {
        val dir = Files.createTempDirectory("scrapling-spider")
        class FileSpider : Spider(crawldir = dir) {
            override val name: String
                get() = "file_spider"
            override val logFile: String
                get() = dir.resolve("spider.log").toString()
            override suspend fun parse(response: Response): Flow<SpiderOutput> = emptyFlow()
        }

        val spider = FileSpider()

        assertEquals(dir, spider.crawldir)
        assertTrue(spider.logger.handlers.any { handler -> handler is java.util.logging.FileHandler })
    }
}

package io.github.d4vinci.scrapling.spiders

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultTest {
    @Test
    fun itemListExportsJsonAndJsonl() {
        val items = ItemList().apply {
            add(mapOf("name" to "alpha"))
            add(mapOf("name" to "beta"))
        }
        val dir = Files.createTempDirectory("scrapling-items")
        val json = dir.resolve("items.json")
        val jsonl = dir.resolve("items.jsonl")

        items.toJson(json, indent = true)
        items.toJsonl(jsonl)

        val jsonContent = Files.readString(json)
        val jsonlContent = Files.readString(jsonl)
        assertTrue(jsonContent.contains("alpha"))
        assertTrue(jsonContent.contains("\n"))
        assertEquals(2, jsonlContent.trim().lines().size)
    }

    @Test
    fun crawlStatsAccumulatesCounters() {
        val stats = CrawlStats(startTime = 1.0, endTime = 3.5, downloadDelay = 0.5)

        stats.incrementStatus(200)
        stats.incrementStatus(200)
        stats.incrementResponseBytes("example.com", 100)
        stats.incrementResponseBytes("example.com", 25)
        stats.incrementRequestsCount("default")
        stats.incrementRequestsCount("default")

        assertEquals(2.5, stats.elapsedSeconds)
        assertEquals(0.8, stats.requestsPerSecond)
        assertEquals(2, stats.responseStatusCount["status_200"])
        assertEquals(125, stats.responseBytes)
        assertEquals(125, stats.domainsResponseBytes["example.com"])
        assertEquals(2, stats.sessionsRequestsCount["default"])
        assertEquals(2, stats.requestsCount)
        assertEquals(0.5, stats.toMap()["download_delay"])
    }

    @Test
    fun crawlResultExposesCompletionAndIteration() {
        val items = ItemList().apply {
            add(mapOf("id" to 1))
            add(mapOf("id" to 2))
        }
        val result = CrawlResult(stats = CrawlStats(), items = items, paused = false)
        val paused = CrawlResult(stats = CrawlStats(), items = ItemList(), paused = true)

        assertTrue(result.completed)
        assertTrue(!paused.completed)
        assertEquals(2, result.toList().size)
        assertEquals(1, result.first()["id"])
    }
}

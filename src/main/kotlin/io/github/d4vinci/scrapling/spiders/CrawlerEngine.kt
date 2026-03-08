package io.github.d4vinci.scrapling.spiders

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.TimeSource

class CrawlerEngine(
    val spider: Spider,
    val sessionManager: SessionManager,
    val crawldir: java.nio.file.Path? = null,
    val interval: Double = 300.0,
) {
    val stats: CrawlStats = CrawlStats()
    val items: ItemList = ItemList()
    var paused: Boolean = false
        private set

    suspend fun crawl(): CrawlStats {
        val startMark = TimeSource.Monotonic.markNow()
        stats.startTime = 0.0
        stats.endTime = startMark.elapsedNow().inWholeMilliseconds / 1000.0
        return stats
    }

    fun requestPause() {
        paused = true
    }

    fun stream(): Flow<Map<String, Any?>> = emptyFlow()
}

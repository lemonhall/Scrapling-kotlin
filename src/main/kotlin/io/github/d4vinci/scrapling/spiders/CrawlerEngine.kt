package io.github.d4vinci.scrapling.spiders

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

@OptIn(ExperimentalSerializationApi::class)
private val dumpJson = Json {
    prettyPrint = true
    prettyPrintIndent = "    "
    encodeDefaults = true
    explicitNulls = true
}

fun dump(value: Map<String, Any?>): String = dumpJson.encodeToString<JsonElement>(dumpToJsonElement(value))

private fun dumpToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(
        value.entries
            .associate { entry -> entry.key.toString() to dumpToJsonElement(entry.value) }
            .toSortedMap(),
    )
    is Iterable<*> -> JsonArray(value.map(::dumpToJsonElement))
    is Array<*> -> JsonArray(value.map(::dumpToJsonElement))
    else -> JsonPrimitive(value.toString())
}

class CrawlLimiter(
    val limit: Int,
) {
    val totalTokens: Int
        get() = limit
}

class CrawlerEngine(
    val spider: Spider,
    val sessionManager: SessionManager,
    val crawldir: Path? = null,
    val interval: Double = 300.0,
) {
    val scheduler: Scheduler = Scheduler(
        includeKwargs = spider.fpIncludeKwargs,
        includeHeaders = spider.fpIncludeHeaders,
        keepFragments = spider.fpKeepFragments,
    )
    var stats: CrawlStats = CrawlStats()
    val globalLimiter: CrawlLimiter = CrawlLimiter(spider.concurrentRequests)
    val domainLimiters: MutableMap<String, CrawlLimiter> = linkedMapOf()
    val allowedDomains: Set<String> = spider.allowedDomains

    internal var activeTasks: Int = 0
    internal var running: Boolean = false
    internal val checkpointSystemEnabled: Boolean = crawldir != null
    internal var lastCheckpointTime: Double = 0.0
    internal var pauseRequested: Boolean = false
    internal var forceStop: Boolean = false
    var paused: Boolean = false
        private set

    private val itemStore: ItemList = ItemList()
    private val checkpointManager: CheckpointManager = CheckpointManager(crawldir ?: Path.of("."), interval)

    fun isDomainAllowed(request: Request): Boolean {
        if (allowedDomains.isEmpty()) {
            return true
        }
        val domain = request.domain
        return allowedDomains.any { allowed -> domain == allowed || domain.endsWith(".$allowed") }
    }

    fun rateLimiter(domain: String): CrawlLimiter {
        val perDomainLimit = spider.concurrentRequestsPerDomain
        if (perDomainLimit <= 0) {
            return globalLimiter
        }
        return domainLimiters.getOrPut(domain) { CrawlLimiter(perDomainLimit) }
    }

    fun normalizeRequest(request: Request) {
        if (request.sid.isBlank()) {
            request.sid = sessionManager.defaultSessionId
        }
    }

    fun requestPause() {
        if (forceStop) {
            return
        }
        if (pauseRequested) {
            forceStop = true
        } else {
            pauseRequested = true
        }
    }

    suspend fun saveCheckpoint() {
        val (requests, seen) = scheduler.snapshot()
        checkpointManager.save(CheckpointData(requests = requests, seen = seen))
        lastCheckpointTime = nowSeconds()
    }

    fun isCheckpointTime(): Boolean {
        if (!checkpointSystemEnabled) {
            return false
        }
        if (checkpointManager.interval == 0.0) {
            return false
        }
        return nowSeconds() - lastCheckpointTime >= checkpointManager.interval
    }

    suspend fun restoreFromCheckpoint(): Boolean {
        check(checkpointSystemEnabled) { "Checkpoint system is disabled." }
        val data = checkpointManager.load() ?: return false
        scheduler.restore(data)
        data.requests.forEach { request -> request.restoreCallback(spider) }
        return true
    }

    suspend fun crawl(): CrawlStats {
        running = true
        itemStore.clear()
        paused = false
        pauseRequested = false
        forceStop = false
        stats = CrawlStats(startTime = nowSeconds())

        val resumed = if (checkpointSystemEnabled) restoreFromCheckpoint() else false
        lastCheckpointTime = nowSeconds()

        sessionManager.start()
        try {
            stats.concurrentRequests = spider.concurrentRequests
            stats.concurrentRequestsPerDomain = spider.concurrentRequestsPerDomain
            stats.downloadDelay = spider.downloadDelay
            spider.onStart(resuming = resumed)

            if (!resumed) {
                spider.startRequests().collect { request ->
                    normalizeRequest(request)
                    scheduler.enqueue(request)
                }
            }

            while (running) {
                if (pauseRequested) {
                    if (checkpointSystemEnabled) {
                        saveCheckpoint()
                        paused = true
                    }
                    running = false
                    break
                }

                if (checkpointSystemEnabled && isCheckpointTime()) {
                    saveCheckpoint()
                }

                if (scheduler.isEmpty) {
                    running = false
                    break
                }

                val request = scheduler.dequeue()
                normalizeRequest(request)
                if (!isDomainAllowed(request)) {
                    stats.offsiteRequestsCount += 1
                    continue
                }
                stats.incrementRequestsCount(request.sid.ifBlank { sessionManager.defaultSessionId })
            }
        } finally {
            spider.onClose()
            if (!paused && checkpointSystemEnabled) {
                checkpointManager.cleanup()
            }
            sessionManager.close()
            running = false
        }

        stats.logLevelsCounter = spider.logCounter.getCounts()
        stats.endTime = nowSeconds()
        return stats
    }

    val items: ItemList
        get() = itemStore

    fun stream(): Flow<Map<String, Any?>> = emptyFlow()

    private fun nowSeconds(): Double = System.currentTimeMillis() / 1000.0
}

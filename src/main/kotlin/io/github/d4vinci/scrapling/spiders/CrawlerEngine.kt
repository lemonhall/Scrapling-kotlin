package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

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
    private val semaphore = Semaphore(limit.coerceAtLeast(1))

    val totalTokens: Int
        get() = limit

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }
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

    private val activeTaskCounter = AtomicInteger(0)
    internal var activeTasks: Int
        get() = activeTaskCounter.get()
        set(value) {
            activeTaskCounter.set(value)
        }

    @Volatile
    internal var running: Boolean = false
    internal val checkpointSystemEnabled: Boolean = crawldir != null
    internal var lastCheckpointTime: Double = 0.0
    @Volatile
    internal var pauseRequested: Boolean = false
    @Volatile
    internal var forceStop: Boolean = false
    var paused: Boolean = false
        private set

    private val itemStore: ItemList = ItemList()
    private val checkpointManager: CheckpointManager = CheckpointManager(crawldir ?: Path.of("."), interval)
    private var itemEmitter: (suspend (Map<String, Any?>) -> Unit)? = null

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

    internal suspend fun processRequest(request: Request) {
        normalizeRequest(request)
        if (spider.concurrentRequestsPerDomain > 0) {
            globalLimiter.withPermit {
                rateLimiter(request.domain).withPermit {
                    runRequestLifecycle(request)
                }
            }
        } else {
            runRequestLifecycle(request)
        }
    }

    internal suspend fun taskWrapper(request: Request) {
        try {
            processRequest(request)
        } finally {
            activeTaskCounter.decrementAndGet()
        }
    }

    suspend fun crawl(): CrawlStats = coroutineScope {
        running = true
        activeTasks = 0
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
                    if (!isDomainAllowed(request)) {
                        stats.offsiteRequestsCount += 1
                    } else {
                        scheduler.enqueue(request)
                    }
                }
            }

            val jobs = mutableListOf<Job>()
            while (running) {
                if (pauseRequested) {
                    if (activeTasks == 0 || forceStop) {
                        if (forceStop) {
                            jobs.forEach(Job::cancel)
                        }
                        if (checkpointSystemEnabled) {
                            saveCheckpoint()
                            paused = true
                        }
                        running = false
                        break
                    }
                    delay(25)
                    continue
                }

                if (checkpointSystemEnabled && isCheckpointTime()) {
                    saveCheckpoint()
                }

                if (scheduler.isEmpty) {
                    if (activeTasks == 0) {
                        running = false
                        break
                    }
                    delay(25)
                    continue
                }

                if (activeTasks >= spider.concurrentRequests.coerceAtLeast(1)) {
                    delay(10)
                    continue
                }

                val request = scheduler.dequeue()
                activeTaskCounter.incrementAndGet()
                jobs += launch {
                    taskWrapper(request)
                }
            }
            jobs.joinAll()
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
        stats
    }

    val items: ItemList
        get() = itemStore

    fun stream(): Flow<Map<String, Any?>> = channelFlow {
        itemEmitter = { item -> send(item) }
        try {
            crawl()
        } finally {
            itemEmitter = null
        }
    }

    private suspend fun runRequestLifecycle(request: Request) {
        if (spider.downloadDelay > 0.0) {
            delay((spider.downloadDelay * 1000).toLong())
        }
        trackProxyStats(request)

        val response = try {
            sessionManager.fetch(request)
        } catch (error: Exception) {
            stats.failedRequestsCount += 1
            spider.onError(request, error)
            return
        }

        stats.incrementRequestsCount(request.sid.ifBlank { sessionManager.defaultSessionId })
        stats.incrementResponseBytes(request.domain, response.body.size)
        stats.incrementStatus(response.status)

        if (spider.isBlocked(response)) {
            handleBlockedRequest(request, response)
            return
        }

        val callback = request.callback ?: spider::parse
        val outputs = try {
            invokeCallback(callback, response)
        } catch (error: Exception) {
            spider.onError(request, error)
            return
        }

        try {
            outputs.collect { output ->
                when (output) {
                    is SpiderOutput.Follow -> handleFollowOutput(output.request)
                    is SpiderOutput.Item -> handleItemOutput(output.value)
                }
            }
        } catch (error: Exception) {
            spider.onError(request, error)
        }
    }

    private suspend fun handleBlockedRequest(request: Request, response: Response) {
        stats.blockedRequestsCount += 1
        if (request.retryCount >= spider.maxBlockedRetries) {
            return
        }
        val retryRequest = spider.retryBlockedRequest(request.copy(), response).also { retry ->
            retry.retryCount = request.retryCount + 1
            retry.dontFilter = true
            retry.sessionOptions.remove("proxy")
            retry.sessionOptions.remove("proxies")
        }
        normalizeRequest(retryRequest)
        if (isDomainAllowed(retryRequest)) {
            scheduler.enqueue(retryRequest)
        } else {
            stats.offsiteRequestsCount += 1
        }
    }

    private suspend fun handleFollowOutput(request: Request) {
        normalizeRequest(request)
        if (isDomainAllowed(request)) {
            scheduler.enqueue(request)
        } else {
            stats.offsiteRequestsCount += 1
        }
    }

    private suspend fun handleItemOutput(item: Map<String, Any?>) {
        val processed = spider.onScrapedItem(item) ?: run {
            stats.itemsDropped += 1
            return
        }
        stats.itemsScraped += 1
        val emitter = itemEmitter
        if (emitter != null) {
            emitter(processed)
        } else {
            itemStore += processed
        }
    }

    private fun trackProxyStats(request: Request) {
        request.sessionOptions["proxy"]?.let { proxy ->
            stats.proxies += proxy
        }
        request.sessionOptions["proxies"]?.let { proxies ->
            stats.proxies += proxies
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeCallback(callback: KFunction<*>, response: Response): Flow<SpiderOutput> {
        val result = when (callback.parameters.size) {
            1 -> callback.callSuspend(response)
            2 -> callback.callSuspend(spider, response)
            else -> error("Unsupported callback signature: ${callback.name}")
        }
        return result as? Flow<SpiderOutput> ?: error("Callback ${callback.name} must return Flow<SpiderOutput>")
    }

    private fun nowSeconds(): Double = System.currentTimeMillis() / 1000.0
}

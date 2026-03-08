package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.logging.ConsoleHandler
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

val BLOCKED_CODES: Set<Int> = setOf(401, 403, 407, 429, 444, 500, 502, 503, 504)

object SpiderLevels {
    val DEBUG: Level = Level.FINE
    val INFO: Level = Level.INFO
    val WARNING: Level = Level.WARNING
    val ERROR: Level = Level.SEVERE
    val CRITICAL: Level = object : Level("CRITICAL", 1100) {}
}

class LogCounterHandler : Handler() {
    private val counts = linkedMapOf(
        "debug" to 0,
        "info" to 0,
        "warning" to 0,
        "error" to 0,
        "critical" to 0,
    )

    override fun publish(record: LogRecord) {
        when {
            record.level.intValue() >= SpiderLevels.CRITICAL.intValue() -> counts.compute("critical") { _, value -> (value ?: 0) + 1 }
            record.level.intValue() >= SpiderLevels.ERROR.intValue() -> counts.compute("error") { _, value -> (value ?: 0) + 1 }
            record.level.intValue() >= SpiderLevels.WARNING.intValue() -> counts.compute("warning") { _, value -> (value ?: 0) + 1 }
            record.level.intValue() >= SpiderLevels.INFO.intValue() -> counts.compute("info") { _, value -> (value ?: 0) + 1 }
            else -> counts.compute("debug") { _, value -> (value ?: 0) + 1 }
        }
    }

    fun getCounts(): Map<String, Int> = counts.toMap()

    override fun flush() = Unit

    override fun close() = Unit
}

class SessionConfigurationError(message: String) : Exception(message)

abstract class Spider(
    val crawldir: Path? = null,
    private val interval: Double = 300.0,
) {
    abstract val name: String

    open val startUrls: List<String> = emptyList()
    open val allowedDomains: Set<String> = emptySet()
    open val concurrentRequests: Int = 4
    open val concurrentRequestsPerDomain: Int = 0
    open val downloadDelay: Double = 0.0
    open val maxBlockedRetries: Int = 3
    open val fpIncludeKwargs: Boolean = false
    open val fpKeepFragments: Boolean = false
    open val fpIncludeHeaders: Boolean = false
    open val loggingLevel: Level = SpiderLevels.DEBUG
    open val loggingFormat: String = "[%1\$tF %1\$tT]:(%2\$s) %3\$s: %4\$s"
    open val logFile: String? = null

    val logger: Logger = Logger.getLogger("scrapling.spiders.$name")
    internal val logCounter: LogCounterHandler = LogCounterHandler()
    internal var engine: CrawlerEngine? = null
        private set

    internal val sessionManager: SessionManager = SessionManager()

    init {
        require(name.isNotBlank()) { "${this::class.simpleName} must have a name." }
        logger.level = loggingLevel
        logger.useParentHandlers = false
        logger.handlers.forEach(logger::removeHandler)
        val formatter = object : Formatter() {
            override fun format(record: LogRecord): String = String.format(
                loggingFormat,
                java.util.Date(record.millis),
                name,
                record.level.name,
                record.message,
            ) + System.lineSeparator()
        }
        logger.addHandler(logCounter)
        logger.addHandler(ConsoleHandler().also { handler ->
            handler.level = loggingLevel
            handler.formatter = formatter
        })
        logFile?.let { file ->
            Path.of(file).parent?.toFile()?.mkdirs()
            logger.addHandler(FileHandler(file).also { handler ->
                handler.level = loggingLevel
                handler.formatter = formatter
            })
        }

        try {
            configureSessions(sessionManager)
        } catch (error: Exception) {
            throw SessionConfigurationError("Error in ${this::class.simpleName}.configureSessions(): ${error.message}")
        }
        if (sessionManager.size() == 0) {
            throw SessionConfigurationError("${this::class.simpleName}.configureSessions() did not add any sessions")
        }
    }

    open fun configureSessions(manager: SessionManager) {
        manager.add("default", StaticSpiderSession())
    }

    open fun startRequests(): Flow<Request> = flow {
        val defaultSessionId = sessionManager.defaultSessionId
        startUrls.forEach { url ->
            emit(Request(url = url, sid = defaultSessionId, callback = this@Spider::parse))
        }
    }

    abstract suspend fun parse(response: Response): Flow<SpiderOutput>

    open suspend fun onStart(resuming: Boolean) = Unit

    open suspend fun onClose() = Unit

    open suspend fun isBlocked(response: Response): Boolean = response.status in BLOCKED_CODES

    open suspend fun retryBlockedRequest(request: Request, response: Response): Request = request

    fun pause() {
        engine?.requestPause() ?: error("No active crawl to stop")
    }

    fun start(): CrawlResult = runBlocking {
        engine = CrawlerEngine(this@Spider, sessionManager, crawldir, interval)
        try {
            val stats = engine!!.crawl()
            CrawlResult(stats = stats, items = engine!!.items, paused = engine!!.paused)
        } finally {
            closeFileHandlers()
            engine = null
        }
    }

    fun stream(): Flow<Map<String, Any?>> = flow {
        engine = CrawlerEngine(this@Spider, sessionManager, crawldir, interval)
        try {
            emitAll(engine!!.stream())
        } finally {
            closeFileHandlers()
            engine = null
        }
    }

    val stats: CrawlStats
        get() = engine?.stats ?: error("No active crawl. Use this property inside stream().")

    override fun toString(): String = "<${this::class.simpleName} '$name'>"

    private fun closeFileHandlers() {
        logger.handlers.filterIsInstance<FileHandler>().forEach(Handler::close)
    }
}

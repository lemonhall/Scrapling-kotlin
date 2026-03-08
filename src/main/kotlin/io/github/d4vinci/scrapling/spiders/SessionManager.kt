package io.github.d4vinci.scrapling.spiders

import io.github.d4vinci.scrapling.fetchers.static.AsyncFetcherSession
import io.github.d4vinci.scrapling.fetchers.static.RequestOptions
import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SpiderSession {
    val isOpen: Boolean

    suspend fun open()

    suspend fun close()

    suspend fun fetch(request: Request): Response
}

class StaticSpiderSession(
    private val delegate: AsyncFetcherSession = AsyncFetcherSession(),
) : SpiderSession {
    override val isOpen: Boolean
        get() = delegate.isOpen

    override suspend fun open() {
        if (!delegate.isOpen) {
            delegate.open()
        }
    }

    override suspend fun close() {
        delegate.close()
    }

    override suspend fun fetch(request: Request): Response {
        check(delegate.isOpen) { "Session must be open before fetch." }
        val method = (request.sessionOptions["method"] as? String ?: "GET").uppercase()
        val options = RequestOptions(
            data = stringMap(request.sessionOptions["data"]),
            json = request.sessionOptions["json"] as? String,
            headers = stringMap(request.sessionOptions["headers"]),
            cookies = stringMap(request.sessionOptions["cookies"]),
            params = stringMap(request.sessionOptions["params"]),
            proxies = stringMap(request.sessionOptions["proxies"]),
            proxy = request.sessionOptions["proxy"] as? String,
            timeout = request.sessionOptions["timeout"] as? Int ?: delegate.defaultTimeout,
            retries = request.sessionOptions["retries"] as? Int ?: delegate.defaultRetries,
            followRedirects = request.sessionOptions["followRedirects"] as? Boolean ?: true,
            stealthyHeaders = request.sessionOptions["stealthyHeaders"] as? Boolean ?: delegate.stealthyHeaders,
        )
        return when (method) {
            "GET" -> delegate.get(request.url, options)
            "POST" -> delegate.post(request.url, options.data, options)
            "PUT" -> delegate.put(request.url, options.data, options)
            "DELETE" -> delegate.delete(request.url, options)
            else -> error("Unsupported request method: $method")
        }.mergeMeta(request.meta)
    }
}

class SessionManager {
    private val sessions = linkedMapOf<String, SpiderSession>()
    private val lazySessions = linkedSetOf<String>()
    private val lazyLock = Mutex()
    private var defaultSessionIdInternal: String? = null
    internal var started: Boolean = false
        private set

    fun add(
        sessionId: String,
        session: SpiderSession,
        default: Boolean = false,
        lazy: Boolean = false,
    ): SessionManager {
        require(sessionId !in sessions) { "Session '$sessionId' already registered" }
        sessions[sessionId] = session
        if (default || defaultSessionIdInternal == null) {
            defaultSessionIdInternal = sessionId
        }
        if (lazy) {
            lazySessions += sessionId
        }
        return this
    }

    fun remove(sessionId: String) {
        pop(sessionId)
    }

    fun pop(sessionId: String): SpiderSession {
        val session = sessions.remove(sessionId) ?: throw NoSuchElementException("Session '$sessionId' not found")
        lazySessions.remove(sessionId)
        if (defaultSessionIdInternal == sessionId) {
            defaultSessionIdInternal = sessions.keys.firstOrNull()
        }
        return session
    }

    val defaultSessionId: String
        get() = defaultSessionIdInternal ?: error("No sessions registered")

    val sessionIds: List<String>
        get() = sessions.keys.toList()

    fun get(sessionId: String): SpiderSession = sessions[sessionId]
        ?: throw NoSuchElementException("Session '$sessionId' not found. Available: ${sessions.keys.joinToString(", ")}")

    suspend fun start() {
        if (started) {
            return
        }
        sessions.forEach { (sessionId, session) ->
            if (sessionId !in lazySessions && !session.isOpen) {
                session.open()
            }
        }
        started = true
    }

    suspend fun close() {
        sessions.values.forEach { session ->
            if (session.isOpen) {
                session.close()
            }
        }
        started = false
    }

    suspend fun fetch(request: Request): Response {
        val sessionId = if (request.sid.isBlank()) defaultSessionId else request.sid
        val session = get(sessionId)
        if (sessionId in lazySessions && !session.isOpen) {
            lazyLock.withLock {
                if (!session.isOpen) {
                    session.open()
                }
            }
        }
        return session.fetch(request).mergeMeta(request.meta)
    }

    suspend fun use(block: suspend (SessionManager) -> Unit) {
        start()
        try {
            block(this)
        } finally {
            close()
        }
    }

    operator fun contains(sessionId: String): Boolean = sessionId in sessions

    fun size(): Int = sessions.size
}

private fun Response.mergeMeta(requestMeta: Map<String, Any?>): Response = Response(
    url = url,
    content = content,
    status = status,
    reason = reason,
    cookies = cookies,
    headers = headers,
    requestHeaders = requestHeaders,
    method = method,
    history = history,
    meta = requestMeta.filterValues { value -> value != null }.mapValues { (_, value) -> value as Any } + meta,
)

private fun stringMap(value: Any?): Map<String, String> =
    (value as? Map<*, *>)
        ?.mapNotNull { (key, entryValue) ->
            val normalizedKey = key?.toString() ?: return@mapNotNull null
            normalizedKey to (entryValue?.toString() ?: "")
        }
        ?.toMap()
        .orEmpty()

package io.github.d4vinci.scrapling.fetchers.browser

import io.github.d4vinci.scrapling.fetchers.static.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

open class AsyncDynamicSession(
    private val defaultOptions: BrowserFetchOptions = BrowserFetchOptions(),
    val maxPages: Int = 1,
    stealth: Boolean = false,
) : AutoCloseable {
    private val delegate = DynamicSession(defaultOptions, maxPages = maxPages, stealth = stealth)
    private val semaphore = Semaphore(maxPages)
    private val engineMutex = Mutex()

    val pagePool
        get() = delegate.pagePool

    val context
        get() = delegate.context

    var isOpen: Boolean = false
        private set

    fun open(): AsyncDynamicSession {
        check(!isOpen) { "AsyncDynamicSession is already open." }
        delegate.open()
        isOpen = true
        return this
    }

    suspend fun fetch(url: String, options: BrowserFetchOptions = defaultOptions): Response {
        check(isOpen) { "AsyncDynamicSession must be opened before fetch." }
        return semaphore.withPermit {
            engineMutex.withLock {
                withContext(Dispatchers.IO) {
                    delegate.fetch(url, options)
                }
            }
        }
    }

    fun getPoolStats(): Map<String, Int> = delegate.getPoolStats()

    override fun close() {
        delegate.close()
        isOpen = false
    }
}

class AsyncStealthySession(
    defaultOptions: BrowserFetchOptions = BrowserFetchOptions(blockWebRtc = true),
    maxPages: Int = 1,
) : AsyncDynamicSession(defaultOptions = defaultOptions, maxPages = maxPages, stealth = true)

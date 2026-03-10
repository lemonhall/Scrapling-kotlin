package io.github.d4vinci.scrapling.fetchers.static

import kotlinx.coroutines.delay

fun interface BlockingRetryPause {
    fun pause(seconds: Int)
}

fun interface AsyncRetryPause {
    suspend fun pause(seconds: Int)
}

object DefaultBlockingRetryPause : BlockingRetryPause {
    override fun pause(seconds: Int) {
        if (seconds <= 0) return
        Thread.sleep(seconds.toLong().coerceAtLeast(0) * 1_000L)
    }
}

object DefaultAsyncRetryPause : AsyncRetryPause {
    override suspend fun pause(seconds: Int) {
        if (seconds <= 0) return
        delay(seconds.toLong().coerceAtLeast(0) * 1_000L)
    }
}


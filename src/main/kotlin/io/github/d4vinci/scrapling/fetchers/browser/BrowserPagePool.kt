package io.github.d4vinci.scrapling.fetchers.browser

class BrowserPagePool(
    val maxPages: Int,
) {
    var pagesCount: Int = 0
        private set

    var busyCount: Int = 0
        private set

    @Synchronized
    fun markPageAcquired() {
        pagesCount += 1
        busyCount += 1
    }

    @Synchronized
    fun markPageReleased() {
        pagesCount = (pagesCount - 1).coerceAtLeast(0)
        busyCount = (busyCount - 1).coerceAtLeast(0)
    }
}

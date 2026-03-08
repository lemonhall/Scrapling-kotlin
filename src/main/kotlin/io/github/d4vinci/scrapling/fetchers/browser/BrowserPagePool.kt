package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Page

class BrowserPagePool(
    val maxPages: Int,
) {
    private data class Entry(
        val page: Page,
        var busy: Boolean,
    )

    private val entries = mutableListOf<Entry>()

    val pagesCount: Int
        @Synchronized get() = entries.size

    val busyCount: Int
        @Synchronized get() = entries.count { it.busy }

    @Synchronized
    fun acquirePage(factory: () -> Page): Page {
        entries.removeAll { it.page.isClosed() }
        entries.firstOrNull { !it.busy }?.let { entry ->
            entry.busy = true
            return entry.page
        }
        check(entries.size < maxPages) { "Maximum page limit ($maxPages) reached." }
        val page = factory()
        entries += Entry(page = page, busy = true)
        return page
    }

    @Synchronized
    fun releasePage(page: Page) {
        entries.firstOrNull { it.page == page }?.busy = false
    }

    @Synchronized
    fun discardPage(page: Page) {
        entries.removeAll { it.page == page }
        if (!page.isClosed()) {
            page.close()
        }
    }

    @Synchronized
    fun closeAll() {
        entries.toList().forEach { entry ->
            if (!entry.page.isClosed()) {
                entry.page.close()
            }
        }
        entries.clear()
    }
}

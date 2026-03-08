package io.github.d4vinci.scrapling.fetchers.browser

import io.github.d4vinci.scrapling.fetchers.static.Response

object DynamicFetcher {
    fun fetch(url: String, options: BrowserFetchOptions = BrowserFetchOptions()): Response =
        DynamicSession(options).open().use { session ->
            session.fetch(url, options)
        }
}

object StealthyFetcher {
    fun fetch(
        url: String,
        options: BrowserFetchOptions = BrowserFetchOptions(blockWebRtc = true),
    ): Response = StealthySession(options).open().use { session ->
        session.fetch(url, options)
    }
}

package io.github.d4vinci.scrapling.fetchers.browser

import io.github.d4vinci.scrapling.fetchers.static.Response

object AsyncDynamicFetcher {
    suspend fun fetch(url: String, options: BrowserFetchOptions = BrowserFetchOptions()): Response =
        AsyncDynamicSession(defaultOptions = options).open().use { session ->
            session.fetch(url, options)
        }
}

object AsyncStealthyFetcher {
    suspend fun fetch(
        url: String,
        options: BrowserFetchOptions = BrowserFetchOptions(blockWebRtc = true),
    ): Response = AsyncStealthySession(defaultOptions = options).open().use { session ->
        session.fetch(url, options)
    }
}

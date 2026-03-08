package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Page
import com.microsoft.playwright.Response as PlaywrightResponse
import io.github.d4vinci.scrapling.fetchers.static.Response
import io.github.d4vinci.scrapling.fetchers.static.SelectorConfig

internal object BrowserResponseFactory {
    fun fromPage(
        page: Page,
        playwrightResponse: PlaywrightResponse?,
        requestHeaders: Map<String, String>,
        routeStats: BrowserRouteStats,
        selectorConfig: SelectorConfig,
    ): Response {
        val url = page.url()
        val html = page.content().encodeToByteArray()
        val cookies = page.context().cookies(url).associate { cookie -> cookie.name to cookie.value }

        return Response(
            url = url,
            content = html,
            status = playwrightResponse?.status() ?: 0,
            reason = playwrightResponse?.statusText() ?: "",
            cookies = cookies,
            headers = playwrightResponse?.headers() ?: emptyMap(),
            requestHeaders = requestHeaders,
            method = playwrightResponse?.request()?.method() ?: "GET",
            meta = mapOf(
                "blockedRequests" to routeStats.blockedRequests,
                "continuedRequests" to routeStats.continuedRequests,
            ),
            selectorConfig = selectorConfig.copy(url = selectorConfig.url.ifBlank { url }),
        )
    }
}

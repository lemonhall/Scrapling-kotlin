package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.Page

data class BrowserFetchOptions(
    val headless: Boolean = true,
    val disableResources: Boolean = false,
    val blockedDomains: Set<String> = emptySet(),
    val pageAction: ((Page) -> Unit)? = null,
    val timeout: Double = 30_000.0,
    val retries: Int = 3,
    val retryDelay: Double = 1_000.0,
    val loadDom: Boolean = true,
    val waitSelector: String? = null,
    val waitSelectorState: WaitSelectorStateValue = WaitSelectorStateValue.ATTACHED,
    val wait: Double? = null,
    val waitForMillis: Double? = null,
    val networkIdle: Boolean = false,
    val solveCloudflare: Boolean = false,
    val extraHeaders: Map<String, String> = emptyMap(),
    val cookies: List<BrowserCookie> = emptyList(),
    val locale: String? = null,
    val timezoneId: String? = null,
    val userAgent: String? = null,
    val realChrome: Boolean = false,
    val hideCanvas: Boolean = false,
    val blockWebRtc: Boolean = false,
    val allowWebgl: Boolean = true,
)

data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
)

enum class WaitSelectorStateValue {
    ATTACHED,
    DETACHED,
    VISIBLE,
    HIDDEN,
    ;

    fun toPlaywright(): WaitForSelectorState = when (this) {
        ATTACHED -> WaitForSelectorState.ATTACHED
        DETACHED -> WaitForSelectorState.DETACHED
        VISIBLE -> WaitForSelectorState.VISIBLE
        HIDDEN -> WaitForSelectorState.HIDDEN
    }
}

data class BrowserRouteStats(
    val blockedRequests: Int = 0,
    val continuedRequests: Int = 0,
)

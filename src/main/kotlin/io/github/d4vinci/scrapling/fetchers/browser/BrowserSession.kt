package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.Cookie
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitUntilState
import io.github.d4vinci.scrapling.fetchers.static.Response

open class DynamicSession(
    private val defaultOptions: BrowserFetchOptions = BrowserFetchOptions(),
) : AutoCloseable {
    private var playwright: Playwright? = null
    private var browser: Browser? = null
    internal var context: BrowserContext? = null

    var isOpen: Boolean = false
        private set

    fun open(): DynamicSession {
        check(!isOpen) { "DynamicSession is already open." }

        playwright = Playwright.create()
        browser = playwright!!.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(defaultOptions.headless)
                .setChannel(if (defaultOptions.realChrome) "msedge" else null),
        )
        context = newContext(defaultOptions)
        isOpen = true
        return this
    }

    fun fetch(url: String, options: BrowserFetchOptions = defaultOptions): Response {
        check(isOpen) { "DynamicSession must be opened before fetch." }
        val activeContext = context ?: error("Browser context is not initialized.")
        val page = activeContext.newPage()
        var blockedRequests = 0
        var continuedRequests = 0

        try {
            if (options.extraHeaders.isNotEmpty()) {
                page.setExtraHTTPHeaders(options.extraHeaders)
            }
            if (options.disableResources || options.blockedDomains.isNotEmpty()) {
                page.route(
                    "**/*",
                    BrowserRouting.createRouteHandler(
                        disableResources = options.disableResources,
                        blockedDomains = options.blockedDomains,
                        onBlocked = { blockedRequests += 1 },
                        onContinued = { continuedRequests += 1 },
                    ),
                )
            }

            val response = page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD))

            if (options.networkIdle) {
                page.waitForLoadState(LoadState.NETWORKIDLE)
            }
            options.waitForMillis?.let(page::waitForTimeout)
            options.waitSelector?.let { selector ->
                page.waitForSelector(
                    selector,
                    Page.WaitForSelectorOptions().setState(options.waitSelectorState.toPlaywright()),
                )
            }

            return BrowserResponseFactory.fromPage(
                page = page,
                playwrightResponse = response,
                requestHeaders = options.extraHeaders,
                routeStats = BrowserRouteStats(
                    blockedRequests = blockedRequests,
                    continuedRequests = continuedRequests,
                ),
            )
        } finally {
            page.close()
        }
    }

    protected open fun newContext(options: BrowserFetchOptions): BrowserContext {
        val activeBrowser = browser ?: error("Browser is not initialized.")
        val context = activeBrowser.newContext(
            Browser.NewContextOptions()
                .setLocale(options.locale)
                .setTimezoneId(options.timezoneId)
                .setUserAgent(options.userAgent),
        )

        if (options.cookies.isNotEmpty()) {
            context.addCookies(options.cookies.map { cookie ->
                Cookie(cookie.name, cookie.value)
                    .setDomain(cookie.domain)
                    .setPath(cookie.path)
            })
        }

        BrowserStealth.initScript(options)?.let(context::addInitScript)
        return context
    }

    override fun close() {
        context?.close()
        browser?.close()
        playwright?.close()
        context = null
        browser = null
        playwright = null
        isOpen = false
    }
}

class StealthySession(
    defaultOptions: BrowserFetchOptions = BrowserFetchOptions(blockWebRtc = true),
) : DynamicSession(defaultOptions)

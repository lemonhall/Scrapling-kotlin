package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.Cookie
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitUntilState
import io.github.d4vinci.scrapling.fetchers.static.Response

open class DynamicSession(
    private val defaultOptions: BrowserFetchOptions = BrowserFetchOptions(),
    val maxPages: Int = 1,
    private val stealth: Boolean = false,
) : AutoCloseable {
    private var playwright: Playwright? = null
    private var browser: Browser? = null
    internal var context: BrowserContext? = null

    val pagePool = BrowserPagePool(maxPages)

    var isOpen: Boolean = false
        private set

    fun open(): DynamicSession {
        check(!isOpen) { "DynamicSession is already open." }

        playwright = Playwright.create()
        browser = playwright!!.chromium().launch(BrowserLaunchSupport.launchOptions(defaultOptions, stealth))
        context = newContext(defaultOptions)
        isOpen = true
        return this
    }

    fun fetch(url: String, options: BrowserFetchOptions = defaultOptions): Response {
        check(isOpen) { "DynamicSession must be opened before fetch." }
        val activeContext = context ?: error("Browser context is not initialized.")
        val page = pagePool.acquirePage(activeContext::newPage)
        var blockedRequests = 0
        var continuedRequests = 0
        var reusable = true

        try {
            preparePage(page)
            applyCookies(activeContext, options)
            page.setExtraHTTPHeaders(options.extraHeaders)
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
            options.pageAction?.invoke(page)
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
        } catch (error: Exception) {
            reusable = false
            throw error
        } finally {
            if (reusable && cleanupReusablePage(page)) {
                pagePool.releasePage(page)
            } else {
                pagePool.discardPage(page)
            }
        }
    }

    fun getPoolStats(): Map<String, Int> = mapOf(
        "total_pages" to pagePool.pagesCount,
        "busy_pages" to pagePool.busyCount,
        "max_pages" to maxPages,
    )

    protected open fun newContext(options: BrowserFetchOptions): BrowserContext {
        val activeBrowser = browser ?: error("Browser is not initialized.")
        val context = activeBrowser.newContext(
            Browser.NewContextOptions()
                .setLocale(options.locale)
                .setTimezoneId(options.timezoneId)
                .setUserAgent(options.userAgent),
        )

        applyCookies(context, options)
        BrowserStealth.initScript(options, stealth)?.let(context::addInitScript)
        return context
    }

    private fun applyCookies(context: BrowserContext, options: BrowserFetchOptions) {
        if (options.cookies.isEmpty()) {
            return
        }
        context.addCookies(options.cookies.map { cookie ->
            Cookie(cookie.name, cookie.value)
                .setDomain(cookie.domain)
                .setPath(cookie.path)
        })
    }

    private fun preparePage(page: Page) {
        page.unrouteAll()
        page.setExtraHTTPHeaders(emptyMap())
    }

    private fun cleanupReusablePage(page: Page): Boolean = try {
        if (!page.isClosed()) {
            page.unrouteAll()
            page.setExtraHTTPHeaders(emptyMap())
            page.navigate("about:blank", Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD))
        }
        true
    } catch (_: Exception) {
        false
    }

    override fun close() {
        pagePool.closeAll()
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
) : DynamicSession(defaultOptions, stealth = true) {
    companion object {
        val cloudflarePattern: Regex = CloudflareInspector.challengeUrlPattern

        fun detectCloudflare(pageContent: String): String? = CloudflareInspector.detect(pageContent)
    }
}

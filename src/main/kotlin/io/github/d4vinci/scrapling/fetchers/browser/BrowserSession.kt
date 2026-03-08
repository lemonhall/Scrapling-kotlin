package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.Cookie
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitUntilState
import io.github.d4vinci.scrapling.fetchers.static.Response
import java.nio.file.Path

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
        val browserType = playwright!!.chromium()
        context = when {
            !defaultOptions.cdpUrl.isNullOrBlank() -> {
                BrowserLaunchSupport.validateCdpUrl(defaultOptions.cdpUrl)
                browser = browserType.connectOverCDP(defaultOptions.cdpUrl)
                browser!!.contexts().firstOrNull() ?: browser!!.newContext(BrowserLaunchSupport.newContextOptions(defaultOptions))
            }

            !defaultOptions.userDataDir.isNullOrBlank() -> {
                val persistentContext = browserType.launchPersistentContext(
                    Path.of(defaultOptions.userDataDir),
                    BrowserLaunchSupport.persistentContextOptions(defaultOptions, stealth),
                )
                browser = persistentContext.browser()
                persistentContext
            }

            else -> {
                browser = browserType.launch(BrowserLaunchSupport.launchOptions(defaultOptions, stealth))
                newContext(defaultOptions)
            }
        }
        applyContextDecorations(context ?: error("Browser context is not initialized."), defaultOptions)
        isOpen = true
        return this
    }

    fun fetch(url: String, options: BrowserFetchOptions = defaultOptions): Response {
        check(isOpen) { "DynamicSession must be opened before fetch." }
        val activeContext = context ?: error("Browser context is not initialized.")
        var lastError: Exception? = null

        repeat(options.retries.coerceAtLeast(1)) { attemptIndex ->
            val page = pagePool.acquirePage(activeContext::newPage)
            var blockedRequests = 0
            var continuedRequests = 0
            var reusable = true

            try {
                val effectiveTimeout = if (options.solveCloudflare) {
                    maxOf(options.timeout, 60_000.0)
                } else {
                    options.timeout
                }

                preparePage(page, effectiveTimeout)
                applyCookies(activeContext, options)
                val effectiveHeaders = effectiveHeaders(url, options)
                page.setExtraHTTPHeaders(effectiveHeaders)
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

                val navigateOptions = Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD)
                effectiveHeaders["Referer"]?.let(navigateOptions::setReferer)
                val response = page.navigate(url, navigateOptions)
                if (options.loadDom) {
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED)
                }
                if (options.networkIdle) {
                    page.waitForLoadState(
                        LoadState.NETWORKIDLE,
                        Page.WaitForLoadStateOptions().setTimeout(effectiveTimeout),
                    )
                }
                if (options.solveCloudflare) {
                    CloudflareSolver.solve(page)
                }
                options.pageAction?.invoke(page)
                val waitMillis = options.wait ?: options.waitForMillis ?: 0.0
                if (waitMillis > 0) {
                    page.waitForTimeout(waitMillis)
                }
                options.waitSelector?.let { selector ->
                    page.waitForSelector(
                        selector,
                        Page.WaitForSelectorOptions().setState(options.waitSelectorState.toPlaywright()),
                    )
                }

                return BrowserResponseFactory.fromPage(
                    page = page,
                    playwrightResponse = response,
                    requestHeaders = effectiveHeaders,
                    routeStats = BrowserRouteStats(
                        blockedRequests = blockedRequests,
                        continuedRequests = continuedRequests,
                    ),
                    selectorConfig = options.selectorConfig,
                )
            } catch (error: Exception) {
                lastError = error
                reusable = false
                if (attemptIndex < options.retries.coerceAtLeast(1) - 1 && options.retryDelay > 0) {
                    Thread.sleep(options.retryDelay.toLong())
                }
            } finally {
                if (reusable && cleanupReusablePage(page)) {
                    pagePool.releasePage(page)
                } else {
                    pagePool.discardPage(page)
                }
            }
        }

        throw lastError ?: IllegalStateException("Browser fetch failed without an exception.")
    }

    fun getPoolStats(): Map<String, Int> = mapOf(
        "total_pages" to pagePool.pagesCount,
        "busy_pages" to pagePool.busyCount,
        "max_pages" to maxPages,
    )

    protected open fun newContext(options: BrowserFetchOptions): BrowserContext {
        val activeBrowser = browser ?: error("Browser is not initialized.")
        return activeBrowser.newContext(BrowserLaunchSupport.newContextOptions(options))
    }

    private fun applyContextDecorations(context: BrowserContext, options: BrowserFetchOptions) {
        applyCookies(context, options)
        options.initScript?.let { context.addInitScript(Path.of(it)) }
        BrowserStealth.initScript(options, stealth)?.let(context::addInitScript)
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

    private fun preparePage(page: Page, timeout: Double) {
        page.unrouteAll()
        page.setExtraHTTPHeaders(emptyMap())
        page.setDefaultNavigationTimeout(timeout)
        page.setDefaultTimeout(timeout)
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
        runCatching { context?.close() }
        runCatching { browser?.close() }
        playwright?.close()
        context = null
        browser = null
        playwright = null
        isOpen = false
    }

    private fun effectiveHeaders(
        url: String,
        options: BrowserFetchOptions,
    ): Map<String, String> {
        val headers = options.extraHeaders.toMutableMap()
        if (options.googleSearch && headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            headers["Referer"] = BrowserLaunchSupport.googleReferer(url)
        }
        return headers
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

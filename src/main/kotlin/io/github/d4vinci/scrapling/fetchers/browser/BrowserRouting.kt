package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Route
import java.net.URI

internal object BrowserRouting {
    private val disabledResourceTypes = setOf("image", "media", "font", "stylesheet")

    fun createRouteHandler(
        disableResources: Boolean,
        blockedDomains: Set<String>,
        onBlocked: () -> Unit,
        onContinued: () -> Unit,
    ): (Route) -> Unit = { route ->
        val request = route.request()
        val hostname = runCatching { URI(request.url()).host.orEmpty() }.getOrDefault("")
        val shouldBlockByResource = disableResources && request.resourceType() in disabledResourceTypes
        val shouldBlockByDomain = blockedDomains.any { domain -> hostname == domain || hostname.endsWith(".$domain") }

        if (shouldBlockByResource || shouldBlockByDomain) {
            onBlocked()
            route.abort()
        } else {
            onContinued()
            route.resume()
        }
    }
}

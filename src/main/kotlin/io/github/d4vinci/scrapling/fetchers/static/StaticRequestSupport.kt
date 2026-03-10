package io.github.d4vinci.scrapling.fetchers.static

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.random.Random

internal fun RequestOptions.validateTransportSupport() {
    require(proxyRotator == null || (proxy == null && proxies.isEmpty())) {
        "Cannot use 'proxyRotator' together with 'proxy' or 'proxies'. Use either a static proxy or proxy rotation, not both."
    }
    if (http3) {
        throw UnsupportedOperationException(
            "HTTP/3 is not supported by the JDK static transport. Disable 'http3' or use a browser fetcher.",
        )
    }
    if (!verify) {
        throw UnsupportedOperationException(
            "Disabling TLS verification is not supported by the JDK static transport.",
        )
    }
}

internal fun RequestOptions.resolveProxy(url: String): String? {
    val scheme = URI.create(url).scheme?.lowercase() ?: "http"
    return proxy ?: proxies[scheme] ?: proxies["all"] ?: proxyRotator?.getProxy()
}

internal fun RequestOptions.resolvedUserAgent(): String {
    val candidate = impersonate.browsers.filter(String::isNotBlank).let { browsers ->
        when {
            browsers.isEmpty() -> null
            browsers.size == 1 -> browsers.single()
            else -> browsers[Random.Default.nextInt(browsers.size)]
        }
    }
    val browser = parseImpersonatedBrowser(candidate)

    return when (browser.family) {
        "firefox" -> FIREFOX_USER_AGENT_TEMPLATE.format(
            browser.version ?: DEFAULT_FIREFOX_VERSION,
            browser.version ?: DEFAULT_FIREFOX_VERSION,
        )
        "safari" -> SAFARI_USER_AGENT_TEMPLATE.format(browser.version ?: DEFAULT_SAFARI_VERSION)
        "edge" -> EDGE_USER_AGENT_TEMPLATE.format(
            browser.version ?: DEFAULT_CHROME_VERSION,
            browser.version ?: DEFAULT_CHROME_VERSION,
        )
        else -> CHROME_USER_AGENT_TEMPLATE.format(browser.version ?: DEFAULT_CHROME_VERSION)
    }
}

internal fun RequestOptions.authorizationHeader(): String? = auth.toBasicAuthHeader()

internal fun RequestOptions.resolvedProxyAuthorizationHeader(proxyUrl: String?): String? =
    proxyAuthorizationHeader() ?: proxyUrl?.let(::proxyAuthorizationFromUrl)

internal fun RequestOptions.proxyAuthorizationHeader(): String? = proxyAuth.toBasicAuthHeader()

private fun Map<String, String>?.toBasicAuthHeader(): String? {
    val username = this?.get("username") ?: return null
    val password = this["password"] ?: return null
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
    return "Basic $token"
}

private fun proxyAuthorizationFromUrl(proxyUrl: String): String? {
    val userInfo = runCatching { URI.create(proxyUrl).userInfo }.getOrNull() ?: return null
    val credentials = userInfo.split(':', limit = 2)
    val username = credentials.getOrNull(0)?.takeIf(String::isNotEmpty) ?: return null
    val password = credentials.getOrNull(1).orEmpty()
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
    return "Basic $token"
}

private data class ParsedImpersonatedBrowser(
    val family: String,
    val version: String?,
)

private fun parseImpersonatedBrowser(token: String?): ParsedImpersonatedBrowser {
    val normalized = token?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) {
        return ParsedImpersonatedBrowser("chrome", null)
    }

    val prefixMatches = listOf(
        "chrome_android" to "chrome",
        "chromium" to "chrome",
        "chrome" to "chrome",
        "firefox" to "firefox",
        "msedge" to "edge",
        "edge" to "edge",
        "edg" to "edge",
        "safari_ios_beta" to "safari",
        "safari_ios" to "safari",
        "safari_beta" to "safari",
        "safari" to "safari",
    )
    val match = prefixMatches.firstOrNull { (prefix, _) -> normalized.startsWith(prefix) }
        ?: return ParsedImpersonatedBrowser("chrome", null)

    val rawVersion = normalized.removePrefix(match.first)
        .trimStart('_', '-', ' ')
        .replace('_', '.')
        .takeIf(String::isNotBlank)

    return ParsedImpersonatedBrowser(
        family = match.second,
        version = rawVersion?.let { normalizeVersion(match.second, it) },
    )
}

private fun normalizeVersion(family: String, version: String): String = when (family) {
    "chrome", "edge" -> normalizeChromeVersion(version)
    "firefox" -> if ('.' in version) version else "$version.0"
    "safari" -> version
    else -> version
}

private fun normalizeChromeVersion(version: String): String {
    val parts = version.split('.')
    return when (parts.size) {
        1 -> "$version.0.0.0"
        2 -> "$version.0.0"
        3 -> "$version.0"
        else -> version
    }
}

private const val DEFAULT_CHROME_VERSION = "134.0.0.0"
private const val DEFAULT_FIREFOX_VERSION = "136.0"
private const val DEFAULT_SAFARI_VERSION = "18.3"

private const val CHROME_USER_AGENT_TEMPLATE =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36"

private const val FIREFOX_USER_AGENT_TEMPLATE =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:%s) Gecko/20100101 Firefox/%s"

private const val SAFARI_USER_AGENT_TEMPLATE =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/%s Safari/605.1.15"

private const val EDGE_USER_AGENT_TEMPLATE =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36 Edg/%s"

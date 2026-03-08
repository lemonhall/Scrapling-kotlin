package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.options.Proxy
import java.net.URI

sealed interface BrowserProxyValue {
    fun toPlaywrightProxy(): Proxy
}

data class BrowserProxyUrl(
    val value: String,
) : BrowserProxyValue {
    override fun toPlaywrightProxy(): Proxy = BrowserProxySupport.fromUrl(value)
}

data class BrowserProxySettings(
    val server: String,
    val username: String = "",
    val password: String = "",
    val bypass: String? = null,
) : BrowserProxyValue {
    override fun toPlaywrightProxy(): Proxy = BrowserProxySupport.fromSettings(this)
}

fun interface BrowserProxyRotationStrategy {
    fun next(proxies: List<BrowserProxyValue>, currentIndex: Int): Pair<BrowserProxyValue, Int>
}

class BrowserProxyRotator(
    proxies: List<BrowserProxyValue>,
    private val strategy: BrowserProxyRotationStrategy = BrowserProxyRotationStrategy { configuredProxies, currentIndex ->
        val proxyIndex = currentIndex.mod(configuredProxies.size)
        configuredProxies[proxyIndex] to ((proxyIndex + 1) % configuredProxies.size)
    },
) {
    private val configuredProxies = proxies.toList()
    private var currentIndex: Int = 0
    private val lock = Any()

    init {
        require(configuredProxies.isNotEmpty()) { "At least one proxy must be provided." }
    }

    fun getProxy(): BrowserProxyValue = synchronized(lock) {
        val (proxy, nextIndex) = strategy.next(configuredProxies, currentIndex)
        currentIndex = nextIndex
        proxy
    }

    val proxies: List<BrowserProxyValue>
        get() = configuredProxies.toList()

    override fun toString(): String = "BrowserProxyRotator(proxies=${configuredProxies.size})"
}

internal object BrowserProxySupport {
    private val supportedSchemes = setOf("http", "https", "socks4", "socks5")

    fun validate(options: BrowserFetchOptions) {
        require(options.proxy == null || options.proxyRotator == null) {
            "Cannot use 'proxyRotator' together with 'proxy'. Use either a static proxy or proxy rotation, not both."
        }
    }

    fun applyProxy(
        target: Browser.NewContextOptions,
        proxy: BrowserProxyValue?,
    ) {
        proxy?.toPlaywrightProxy()?.let(target::setProxy)
    }

    fun applyProxy(
        target: BrowserType.LaunchPersistentContextOptions,
        proxy: BrowserProxyValue?,
    ) {
        proxy?.toPlaywrightProxy()?.let(target::setProxy)
    }

    fun fromUrl(proxyUrl: String): Proxy {
        val proxyUri = parseProxyUri(proxyUrl)
        val server = buildString {
            append(proxyUri.scheme.lowercase())
            append("://")
            append(requireNotNull(proxyUri.host))
            if (proxyUri.port != -1) {
                append(":")
                append(proxyUri.port)
            }
        }
        val proxy = Proxy(server)
        val credentials = proxyUri.userInfo.orEmpty().split(':', limit = 2)
        credentials.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let(proxy::setUsername)
        credentials.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(proxy::setPassword)
        return proxy
    }

    fun fromSettings(settings: BrowserProxySettings): Proxy {
        val proxyUri = parseProxyUri(settings.server)
        val server = buildString {
            append(proxyUri.scheme.lowercase())
            append("://")
            append(requireNotNull(proxyUri.host))
            if (proxyUri.port != -1) {
                append(":")
                append(proxyUri.port)
            }
        }
        return Proxy(server)
            .also { proxy ->
                settings.username.takeIf { it.isNotEmpty() }?.let(proxy::setUsername)
                settings.password.takeIf { it.isNotEmpty() }?.let(proxy::setPassword)
                settings.bypass?.takeIf { it.isNotBlank() }?.let(proxy::setBypass)
            }
    }

    private fun parseProxyUri(proxyUrl: String): URI {
        val proxyUri = runCatching { URI.create(proxyUrl) }
            .getOrElse { throw IllegalArgumentException("Invalid proxy string!") }
        require(proxyUri.scheme?.lowercase() in supportedSchemes && !proxyUri.host.isNullOrBlank()) {
            "Invalid proxy string!"
        }
        return proxyUri
    }
}

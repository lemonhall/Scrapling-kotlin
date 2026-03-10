package io.github.d4vinci.scrapling.fetchers.static

fun interface ProxyRotationStrategy {
    fun next(proxies: List<String>, currentIndex: Int): Pair<String, Int>
}

class ProxyRotator(
    proxies: List<String>,
    private val strategy: ProxyRotationStrategy = ProxyRotationStrategy { configuredProxies, currentIndex ->
        val proxyIndex = currentIndex.mod(configuredProxies.size)
        configuredProxies[proxyIndex] to ((proxyIndex + 1) % configuredProxies.size)
    },
) {
    private val configuredProxies = proxies.toList()
    private var currentIndex: Int = 0
    private val lock = Any()

    init {
        require(configuredProxies.isNotEmpty()) { "At least one proxy must be provided." }
        require(configuredProxies.all(String::isNotBlank)) { "Proxy values cannot be blank." }
    }

    fun getProxy(): String = synchronized(lock) {
        val (proxy, nextIndex) = strategy.next(configuredProxies, currentIndex)
        currentIndex = nextIndex
        proxy
    }

    val proxies: List<String>
        get() = configuredProxies.toList()

    override fun toString(): String = "ProxyRotator(proxies=${configuredProxies.size})"
}

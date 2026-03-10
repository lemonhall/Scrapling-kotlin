package io.github.d4vinci.scrapling.fetchers.static

sealed interface Impersonation {
    val browsers: List<String>

    data class Single(
        val browser: String,
    ) : Impersonation {
        override val browsers: List<String> = listOf(browser)
    }

    data class Multiple(
        override val browsers: List<String>,
    ) : Impersonation {
        init {
            require(browsers.isNotEmpty()) { "Impersonation browser list cannot be empty." }
        }
    }

    companion object {
        fun parse(value: String): Impersonation {
            val browsers = value.split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)

            require(browsers.isNotEmpty()) { "Impersonation value cannot be blank." }

            return if (browsers.size == 1) {
                Single(browsers.single())
            } else {
                Multiple(browsers)
            }
        }
    }
}

data class RequestOptions(
    val data: Map<String, String> = emptyMap(),
    val json: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val params: Map<String, String> = emptyMap(),
    val impersonate: Impersonation = Impersonation.Single("chrome"),
    val proxies: Map<String, String> = emptyMap(),
    val proxy: String? = null,
    val proxyRotator: ProxyRotator? = null,
    val timeout: Int? = 30,
    val retries: Int = 3,
    val retryDelay: Int = 1,
    val followRedirects: Boolean = true,
    val maxRedirects: Int = 30,
    val proxyAuth: Map<String, String>? = null,
    val auth: Map<String, String>? = null,
    val verify: Boolean = true,
    val http3: Boolean = false,
    val stealthyHeaders: Boolean = true,
)

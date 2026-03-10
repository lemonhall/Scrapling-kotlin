package io.github.d4vinci.scrapling.fetchers.static

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class HttpClientPoolTest {
    @Test
    fun poolReusesClientForSameProxyAndRedirectMode() {
        val pool = StaticHttpClientPool()

        val first = pool.client(followRedirects = true, proxyUrl = "http://127.0.0.1:8080")
        val second = pool.client(followRedirects = true, proxyUrl = "http://127.0.0.1:8080")

        assertSame(first, second)
    }

    @Test
    fun poolSeparatesClientsByProxyAndRedirectMode() {
        val pool = StaticHttpClientPool()

        val redirecting = pool.client(followRedirects = true, proxyUrl = "http://127.0.0.1:8080")
        val nonRedirecting = pool.client(followRedirects = false, proxyUrl = "http://127.0.0.1:8080")
        val differentProxy = pool.client(followRedirects = true, proxyUrl = "http://127.0.0.1:8181")

        assertNotSame(redirecting, nonRedirecting)
        assertNotSame(redirecting, differentProxy)
    }
}

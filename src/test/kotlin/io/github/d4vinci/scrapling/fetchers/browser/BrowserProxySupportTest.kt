package io.github.d4vinci.scrapling.fetchers.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BrowserProxySupportTest {
    @Test
    fun browserProxyUrlParsesCredentialsForPlaywright() {
        val proxy = BrowserProxyUrl("http://user:pass@127.0.0.1:8080").toPlaywrightProxy()

        assertEquals("http://127.0.0.1:8080", proxy.server)
        assertEquals("user", proxy.username)
        assertEquals("pass", proxy.password)
    }

    @Test
    fun browserProxyRotatorCyclesConfiguredServers() {
        val rotator = BrowserProxyRotator(
            listOf(
                BrowserProxyUrl("http://127.0.0.1:8080"),
                BrowserProxyUrl("http://127.0.0.1:8081"),
            ),
        )

        assertEquals(BrowserProxyUrl("http://127.0.0.1:8080"), rotator.getProxy())
        assertEquals(BrowserProxyUrl("http://127.0.0.1:8081"), rotator.getProxy())
        assertEquals(BrowserProxyUrl("http://127.0.0.1:8080"), rotator.getProxy())
        assertEquals(2, rotator.proxies.size)
        assertEquals("BrowserProxyRotator(proxies=2)", rotator.toString())
    }

    @Test
    fun dynamicSessionRejectsStaticProxyAndRotatorTogether() {
        val error = assertFailsWith<IllegalArgumentException> {
            DynamicSession(
                BrowserFetchOptions(
                    proxy = BrowserProxyUrl("http://127.0.0.1:8080"),
                    proxyRotator = BrowserProxyRotator(listOf(BrowserProxyUrl("http://127.0.0.1:8081"))),
                ),
            ).open()
        }

        assertTrue(error.message?.contains("proxyRotator") == true)
    }
}

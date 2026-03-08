package io.github.d4vinci.scrapling.fetchers.browser

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrowserLaunchSupportTest {
    @Test
    fun stealthLaunchOptionsMirrorUpstreamFlags() {
        val launchOptions = BrowserLaunchSupport.launchOptions(
            options = BrowserFetchOptions(
                headless = true,
                realChrome = true,
                blockWebRtc = true,
                allowWebgl = false,
                hideCanvas = true,
            ),
            stealth = true,
        )

        assertEquals(true, launchOptions.headless)
        assertEquals("chrome", launchOptions.channel)
        assertNotNull(launchOptions.args)
        assertNotNull(launchOptions.ignoreDefaultArgs)
        assertContains(launchOptions.args!!, "--disable-blink-features=AutomationControlled")
        assertContains(launchOptions.args!!, "--webrtc-ip-handling-policy=disable_non_proxied_udp")
        assertContains(launchOptions.args!!, "--disable-webgl")
        assertContains(launchOptions.args!!, "--fingerprinting-canvas-image-data-noise")
        assertContains(launchOptions.ignoreDefaultArgs!!, "--enable-automation")
    }

    @Test
    fun dynamicLaunchOptionsMirrorDefaultFlags() {
        val launchOptions = BrowserLaunchSupport.launchOptions(
            options = BrowserFetchOptions(headless = false),
            stealth = false,
        )

        assertEquals(false, launchOptions.headless)
        assertEquals("chromium", launchOptions.channel)
        assertNotNull(launchOptions.args)
        assertNotNull(launchOptions.ignoreDefaultArgs)
        assertContains(launchOptions.args!!, "--no-first-run")
        assertContains(launchOptions.ignoreDefaultArgs!!, "--enable-automation")
    }

    @Test
    fun launchOptionsIncludeExtraFlags() {
        val launchOptions = BrowserLaunchSupport.launchOptions(
            options = BrowserFetchOptions(extraFlags = listOf("--proxy-bypass-list=<-loopback>")),
            stealth = false,
        )

        assertNotNull(launchOptions.args)
        assertContains(launchOptions.args!!, "--proxy-bypass-list=<-loopback>")
    }
}

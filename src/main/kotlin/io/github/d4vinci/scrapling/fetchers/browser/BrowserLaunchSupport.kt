package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.BrowserType

internal object BrowserLaunchSupport {
    private val harmfulArgs = listOf(
        "--enable-automation",
        "--disable-popup-blocking",
        "--disable-component-update",
        "--disable-default-apps",
        "--disable-extensions",
    )

    private val defaultArgs = listOf(
        "--no-pings",
        "--no-first-run",
        "--disable-infobars",
        "--disable-breakpad",
        "--no-service-autorun",
        "--homepage=about:blank",
        "--password-store=basic",
        "--disable-hang-monitor",
        "--no-default-browser-check",
        "--disable-session-crashed-bubble",
        "--disable-search-engine-choice-screen",
    )

    private val stealthArgs = listOf(
        "--test-type",
        "--lang=en-US",
        "--mute-audio",
        "--disable-sync",
        "--hide-scrollbars",
        "--disable-logging",
        "--start-maximized",
        "--enable-async-dns",
        "--accept-lang=en-US",
        "--use-mock-keychain",
        "--disable-translate",
        "--disable-voice-input",
        "--window-position=0,0",
        "--disable-wake-on-wifi",
        "--ignore-gpu-blocklist",
        "--enable-tcp-fast-open",
        "--enable-web-bluetooth",
        "--disable-cloud-import",
        "--disable-print-preview",
        "--disable-dev-shm-usage",
        "--metrics-recording-only",
        "--disable-crash-reporter",
        "--disable-partial-raster",
        "--disable-gesture-typing",
        "--disable-checker-imaging",
        "--disable-prompt-on-repost",
        "--force-color-profile=srgb",
        "--font-render-hinting=none",
        "--aggressive-cache-discard",
        "--disable-cookie-encryption",
        "--disable-domain-reliability",
        "--disable-threaded-animation",
        "--disable-threaded-scrolling",
        "--enable-simple-cache-backend",
        "--disable-background-networking",
        "--enable-surface-synchronization",
        "--disable-image-animation-resync",
        "--disable-renderer-backgrounding",
        "--disable-ipc-flooding-protection",
        "--prerender-from-omnibox=disabled",
        "--safebrowsing-disable-auto-update",
        "--disable-offer-upload-credit-cards",
        "--disable-background-timer-throttling",
        "--disable-new-content-rendering-timeout",
        "--run-all-compositor-stages-before-draw",
        "--disable-client-side-phishing-detection",
        "--disable-backgrounding-occluded-windows",
        "--disable-layer-tree-host-memory-pressure",
        "--autoplay-policy=user-gesture-required",
        "--disable-offer-store-unmasked-wallet-cards",
        "--disable-blink-features=AutomationControlled",
        "--disable-component-extensions-with-background-pages",
        "--enable-features=NetworkService,NetworkServiceInProcess,TrustTokens,TrustTokensAlwaysAllowIssuance",
        "--blink-settings=primaryHoverType=2,availableHoverTypes=2,primaryPointerType=4,availablePointerTypes=4",
        "--disable-features=AudioServiceOutOfProcess,TranslateUI,BlinkGenPropertyTrees",
    )

    fun launchOptions(
        options: BrowserFetchOptions,
        stealth: Boolean,
    ): BrowserType.LaunchOptions {
        val args = buildList {
            addAll(defaultArgs)
            if (stealth) {
                addAll(stealthArgs)
            }
            if (options.blockWebRtc) {
                add("--webrtc-ip-handling-policy=disable_non_proxied_udp")
                add("--force-webrtc-ip-handling-policy")
            }
            if (!options.allowWebgl) {
                add("--disable-webgl")
                add("--disable-webgl-image-chromium")
                add("--disable-webgl2")
            }
            if (options.hideCanvas) {
                add("--fingerprinting-canvas-image-data-noise")
            }
        }.distinct()

        return BrowserType.LaunchOptions()
            .setHeadless(options.headless)
            .setChannel(if (options.realChrome) "chrome" else "chromium")
            .setArgs(args)
            .setIgnoreDefaultArgs(harmfulArgs)
    }
}

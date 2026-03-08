package io.github.d4vinci.scrapling.fetchers.browser

import com.microsoft.playwright.Frame
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.LoadState

internal object CloudflareSolver {
    private val embeddedSelectors = listOf(
        "#cf_turnstile div",
        "#cf-turnstile div",
        ".turnstile>div>div",
    )

    private val managedSelectors = listOf(
        ".main-content p+div>div>div",
        "#cf-box",
    ) + embeddedSelectors

    fun solve(page: Page): Boolean {
        waitForNetworkIdle(page, timeoutMs = 5000.0)
        val challengeType = detect(page) ?: return false

        if (challengeType == "non-interactive") {
            repeat(50) {
                if (!isChallengePresent(page)) {
                    return true
                }
                page.waitForTimeout(100.0)
                waitForLoad(page)
            }
            return !isChallengePresent(page)
        }

        if (challengeType != "embedded") {
            repeat(20) {
                if (!page.content().contains("Verifying you are human.")) {
                    return@repeat
                }
                page.waitForTimeout(100.0)
            }
        }

        repeat(3) {
            if (!isChallengePresent(page)) {
                return true
            }
            clickChallenge(page, challengeType)
            waitForNetworkIdle(page, timeoutMs = 5000.0)
            waitForLoad(page)
            page.waitForTimeout(100.0)
        }

        return !isChallengePresent(page)
    }

    private fun detect(page: Page): String? {
        val content = page.content()
        return CloudflareInspector.detect(content)
            ?: if (CloudflareInspector.challengeUrlPattern.matches(page.url())) "managed" else null
    }

    private fun isChallengePresent(page: Page): Boolean {
        val content = page.content()
        return CloudflareInspector.detect(content) != null ||
            "<title>Just a moment...</title>" in content ||
            "Verifying you are human." in content ||
            CloudflareInspector.challengeUrlPattern.matches(page.url())
    }

    private fun clickChallenge(page: Page, challengeType: String): Boolean {
        val selectors = if (challengeType == "embedded") embeddedSelectors else managedSelectors
        selectors.forEach { selector ->
            val locator = page.locator(selector)
            if (locator.count() > 0) {
                val candidate = locator.last()
                if (candidate.isVisible()) {
                    candidate.click()
                    return true
                }
            }
        }

        val frame = page.frames().firstOrNull { CloudflareInspector.challengeUrlPattern.matches(it.url()) }
        if (frame != null) {
            clickFrame(frame)?.let { element ->
                element.click()
                return true
            }
        }

        return false
    }

    private fun clickFrame(frame: Frame): Locator? {
        val element = frame.frameElement()
        return if (element.isVisible()) {
            frame.locator("body")
        } else {
            null
        }
    }

    private fun waitForNetworkIdle(page: Page, timeoutMs: Double) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, Page.WaitForLoadStateOptions().setTimeout(timeoutMs))
        } catch (_: Exception) {
        }
    }

    private fun waitForLoad(page: Page) {
        try {
            page.waitForLoadState(LoadState.LOAD)
        } catch (_: Exception) {
        }
    }
}

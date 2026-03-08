package io.github.d4vinci.scrapling.fetchers.browser

object CloudflareInspector {
    val challengeUrlPattern = Regex("^https?://challenges\\.cloudflare\\.com/cdn-cgi/challenge-platform/.*")

    fun detect(pageContent: String): String? {
        val challengeTypes = listOf("non-interactive", "managed", "interactive")
        for (challengeType in challengeTypes) {
            if ("cType: '$challengeType'" in pageContent) {
                return challengeType
            }
        }

        return if ("script[src*=\"challenges.cloudflare.com/turnstile/v\"]" in pageContent ||
            "challenges.cloudflare.com/turnstile/v" in pageContent
        ) {
            "embedded"
        } else {
            null
        }
    }
}

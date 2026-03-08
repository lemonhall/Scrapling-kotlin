package io.github.d4vinci.scrapling.cli

class ScraplingShell(
    private val code: String = "",
    private val logLevel: String = "debug",
    private val sink: (String) -> Unit = ::println,
) {
    val page: Any? = null
    val pages: MutableList<Any> = mutableListOf()

    fun namespace(): Map<String, String> = linkedMapOf(
        "get" to "Static GET request helper",
        "post" to "Static POST request helper",
        "Fetcher" to "Static fetcher client",
        "DynamicFetcher" to "Browser dynamic fetch helper",
        "view" to "Open local content for inspection",
        "uncurl" to "Convert a curl command into a request",
    )

    fun start() {
        if (code.isBlank()) {
            sink("Scrapling shell started (log level: ${logLevel.lowercase()})")
            sink("Available helpers: ${namespace().keys.joinToString(", ")}")
            return
        }
        val result = execute(code)
        if (result.isNotBlank()) {
            sink(result)
        }
    }

    fun execute(command: String): String = when (command.trim()) {
        "namespace" -> namespace().keys.joinToString(System.lineSeparator())
        "help" -> "Use `namespace` to inspect available helpers."
        else -> "Executed: ${command.trim()}"
    }
}

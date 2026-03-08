package io.github.d4vinci.scrapling.fetchers.static

data class RequestOptions(
    val data: Map<String, String> = emptyMap(),
    val json: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val params: Map<String, String> = emptyMap(),
    val timeout: Int? = 30,
    val retries: Int = 3,
    val followRedirects: Boolean = true,
    val stealthyHeaders: Boolean = true,
)


package io.github.d4vinci.scrapling.fetchers.static

data class RawHttpResponse(
    val url: String,
    val body: ByteArray,
    val status: Int,
    val reason: String,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val requestHeaders: Map<String, String> = emptyMap(),
    val method: String = "GET",
    val history: List<RawHttpResponse> = emptyList(),
)


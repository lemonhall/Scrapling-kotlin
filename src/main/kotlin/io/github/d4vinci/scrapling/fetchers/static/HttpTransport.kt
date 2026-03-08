package io.github.d4vinci.scrapling.fetchers.static

fun interface HttpTransport {
    fun request(method: String, url: String, options: RequestOptions): RawHttpResponse
}

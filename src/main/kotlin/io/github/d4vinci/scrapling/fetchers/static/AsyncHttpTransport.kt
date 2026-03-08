package io.github.d4vinci.scrapling.fetchers.static

interface AsyncHttpTransport {
    suspend fun request(method: String, url: String, options: RequestOptions): RawHttpResponse
}

package io.github.d4vinci.scrapling.fetchers.static

class FetcherClient(
    private val transport: HttpTransport,
) {
    fun get(url: String, options: RequestOptions = RequestOptions()): Response = request("GET", url, options)

    fun post(url: String, data: Map<String, String> = emptyMap(), options: RequestOptions = RequestOptions()): Response =
        request("POST", url, options.copy(data = data))

    fun put(url: String, data: Map<String, String> = emptyMap(), options: RequestOptions = RequestOptions()): Response =
        request("PUT", url, options.copy(data = data))

    fun delete(url: String, options: RequestOptions = RequestOptions()): Response = request("DELETE", url, options)

    private fun request(method: String, url: String, options: RequestOptions): Response =
        ResponseFactory.fromRaw(transport.request(method, url, options))
}


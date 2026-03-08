package io.github.d4vinci.scrapling.fetchers.static

class AsyncFetcherClient(
    private val transport: AsyncHttpTransport = AsyncJdkHttpTransport(),
) {
    suspend fun get(url: String, options: RequestOptions = RequestOptions()): Response = request("GET", url, options)

    suspend fun post(
        url: String,
        data: Map<String, String> = emptyMap(),
        options: RequestOptions = RequestOptions(),
    ): Response = request("POST", url, options.copy(data = data))

    suspend fun put(
        url: String,
        data: Map<String, String> = emptyMap(),
        options: RequestOptions = RequestOptions(),
    ): Response = request("PUT", url, options.copy(data = data))

    suspend fun delete(url: String, options: RequestOptions = RequestOptions()): Response = request("DELETE", url, options)

    private suspend fun request(method: String, url: String, options: RequestOptions): Response =
        ResponseFactory.fromRaw(transport.request(method, url, options))
}

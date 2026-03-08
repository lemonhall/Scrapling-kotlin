package io.github.d4vinci.scrapling.fetchers.static

class FetcherSession(
    private val transport: HttpTransport = JdkHttpTransport.sessionTransport,
    timeout: Int = 30,
    retries: Int = 3,
    val stealthyHeaders: Boolean = true,
) : AutoCloseable {
    val defaultTimeout: Int = timeout
    val defaultRetries: Int = retries

    var isOpen: Boolean = false
        private set

    fun open(): FetcherSession {
        check(!isOpen) { "FetcherSession is already open." }
        isOpen = true
        return this
    }

    fun get(url: String, options: RequestOptions = defaultOptions()): Response = request("GET", url, options)

    fun post(url: String, data: Map<String, String> = emptyMap(), options: RequestOptions = defaultOptions()): Response =
        request("POST", url, options.copy(data = data))

    fun put(url: String, data: Map<String, String> = emptyMap(), options: RequestOptions = defaultOptions()): Response =
        request("PUT", url, options.copy(data = data))

    fun delete(url: String, options: RequestOptions = defaultOptions()): Response = request("DELETE", url, options)

    private fun request(method: String, url: String, options: RequestOptions): Response {
        check(isOpen) { "FetcherSession must be opened before making requests." }
        return ResponseFactory.fromRaw(transport.request(method, url, options))
    }

    private fun defaultOptions(): RequestOptions =
        RequestOptions(timeout = defaultTimeout, retries = defaultRetries, stealthyHeaders = stealthyHeaders)

    override fun close() {
        isOpen = false
    }
}

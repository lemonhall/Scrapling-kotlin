package io.github.d4vinci.scrapling.fetchers.static

object ResponseFactory {
    fun fromRaw(raw: RawHttpResponse, selectorConfig: SelectorConfig = SelectorConfig(url = raw.url)): Response =
        Response(
            url = raw.url,
            content = raw.body,
            status = raw.status,
            reason = raw.reason,
            cookies = raw.cookies,
            headers = raw.headers,
            requestHeaders = raw.requestHeaders,
            method = raw.method,
            history = raw.history.map {
                fromRaw(
                    it,
                    selectorConfig.copy(url = it.url),
                )
            },
            selectorConfig = selectorConfig,
        )
}

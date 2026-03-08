# Fetchers

## 静态抓取

- `FetcherClient`
- `AsyncFetcherClient`
- `FetcherSession` / `AsyncFetcherSession`
- `RequestOptions` / `Response`

## 浏览器抓取

- `DynamicFetcher` / `StealthyFetcher`
- `AsyncDynamicFetcher` / `AsyncStealthyFetcher`
- `BrowserFetchOptions`
- `BrowserPagePool`
- `CloudflareInspector` / `CloudflareSolver`

## 相关代码

- `src/main/kotlin/io/github/d4vinci/scrapling/fetchers/static/*`
- `src/main/kotlin/io/github/d4vinci/scrapling/fetchers/browser/*`

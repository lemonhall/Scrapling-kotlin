# Fetchers

## 详细页面

- `docs/fetchers/static.md`
- `docs/fetchers/dynamic.md`
- `docs/fetchers/stealthy.md`

## 静态抓取（Static）

- `FetcherClient`
- `AsyncFetcherClient`
- `FetcherSession` / `AsyncFetcherSession`
- `RequestOptions` / `Response`
- `ProxyRotator`

## 静态行为

- 真实 `GET` / `POST` / `PUT` / `DELETE`，redirect、timeout、重试、`retryDelay`、cookie session reuse
- `proxy` / `proxies` / `proxyAuth`，以及基于 `ProxyRotator` 的按请求轮转代理
- `impersonate` 支持单值与多值（逗号分隔），并映射到“浏览器风格”的请求头组合
- 对 JDK 静态传输不支持的选项（如 `http3=true`、`verify=false`）做显式 fail-fast

## 浏览器抓取（Browser）

- `DynamicFetcher` / `StealthyFetcher`
- `AsyncDynamicFetcher` / `AsyncStealthyFetcher`
- `BrowserFetchOptions`
- `BrowserPagePool`
- `CloudflareInspector` / `CloudflareSolver`

# 静态抓取（Static Fetchers）

## 入口

- `FetcherClient`
- `AsyncFetcherClient`
- `FetcherSession`
- `AsyncFetcherSession`
- `RequestOptions`
- `Response`
- `ProxyRotator`

## 支持的行为

- 真实 HTTP 方法：`GET` / `POST` / `PUT` / `DELETE`
- redirect、重试、`retryDelay`、timeout、cookies、session reuse
- 通过 `proxy` / `proxies` / `proxyAuth` 提供代理支持
- `impersonate` 的单值/多值输入映射为浏览器风格请求头

## 边界说明

- JDK 静态传输的 “impersonate” 属于请求头层面的提示，不等同于 TLS 指纹级伪装。
- `http3=true` 与 `verify=false` 在 JDK 不支持路径上会 fail-fast。
- 若目标需要 JS 执行或浏览器上下文，请优先使用 Browser Fetchers。

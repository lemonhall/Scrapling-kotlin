# v1 Static Fetchers

## Goal

交付 Kotlin 版静态 Fetchers 与 Response 基础层，为 CLI、Spider 和 MCP 提供统一抓取底座。

## PRD Trace

- `REQ-0001-004`

## Scope

- 同步/异步 HTTP 请求
- `Response` 与 parser 集成
- Session 复用
- Headers/Cookies/Params/Proxy/Timeout

## Non-Goals

- 本计划不实现浏览器自动化
- 本计划不实现 Cloudflare 求解

## Acceptance

1. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"` 退出码为 `0`。
2. GET/POST/PUT/DELETE、session reuse、response parsing、异常路径都有测试。
3. 反作弊条款：不得仅通过本地 fixture 伪造 Response 对象而跳过真实 HTTP 客户端行为测试。

## Files

- Create: `src/main/kotlin/io/github/d4vinci/scrapling/fetchers/static/*`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/fetchers/static/*`

## Steps

1. 先写 GET/Response 红测
2. 跑红
3. 实现 HTTP 客户端与 response 适配
4. 跑绿
5. 增补异常与边界测试

## Risks

- JVM HTTP 栈选择会影响后续 proxy/cookies/async 的一致性

## Current Status

- 2026-03-08：已完成 M3 首切，新增 `RequestOptions`、`RawHttpResponse`、`HttpTransport`、`Response`、`ResponseFactory`、`FetcherClient`、`FetcherSession`。
- 已通过验证：`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.StaticFetchersTest"`、`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.StaticFetchersJdkTransportTest"`、`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"`、`./gradlew.bat test`。
- 2026-03-08：已完成 M3 第二刀，新增 `JdkHttpTransport`，并通过本地 `HttpServer` 验证真实 GET/POST/PUT/DELETE、redirect、cookie session reuse。
- 2026-03-08：已完成 M3 第三刀，补齐 timeout/重试真行为测试，验证超时抛错与重试后恢复成功。
- 2026-03-08：已完成 M3 第四刀，新增 `AsyncHttpTransport`、`AsyncJdkHttpTransport`、`AsyncFetcherClient`、`AsyncFetcherSession`，并通过异步本地 `HttpServer` 测试。
- 第 3 条反作弊验收已满足；sync/async、headers/cookies/params/proxy/timeout/重试 语义均已落地，M3 DoD 达成。

## Delivered Slice 1

- `ResponseFactory.fromRaw(...)`：将原始 HTTP 响应转为 parser-aware `Response`。
- `Response`：复用 `Selector` 能力，已打通 `css(...)`、`xpath(...)`、`getAllText()`。
- `FetcherClient`：提供 `get/post/put/delete` 同步门面，通过 `HttpTransport` 注入。
- `FetcherSession`：提供默认 `timeout/retries/stealthyHeaders`、显式 `open()` 与 double-open 防护。

## Delivered Slice 2

- `JdkHttpTransport`：基于 JDK `HttpClient` 落地真实同步 HTTP transport。
- `FetcherClient()`：默认可直接发起真实请求，不再强制注入 transport。
- `FetcherSession()`：默认携带 cookie store，已验证跨请求 cookie 复用。
- 本地 server 级联测试：覆盖 query params、表单 body、redirect 开关、stealthy headers 与 response parsing。

## Delivered Slice 3

- timeout/重试验证：使用本地 `HttpServer` 覆盖 `HttpTimeoutException` 与 `retries` 生效路径。
- `JdkHttpTransport`：当前已确认 timeout 会沿 JDK `HttpClient` 语义抛出，`retries` 会在预算内重试 `IOException`。

## Delivered Slice 4

- `AsyncFetcherClient` / `AsyncFetcherSession`：补齐 async fetcher 公共入口。
- `AsyncJdkHttpTransport`：基于 JDK `HttpClient.sendAsync(...)` 落地异步 transport。
- 异步测试：覆盖真实 GET/POST/PUT/DELETE、redirect、cookie session reuse、timeout/重试与 double-open。


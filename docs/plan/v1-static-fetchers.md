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


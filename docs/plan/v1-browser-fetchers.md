# v1 Browser Fetchers

## Goal

交付 Kotlin 版动态抓取与 stealth 抓取，使源项目的浏览器能力在 JVM 侧有等价落点。

## PRD Trace

- `REQ-0001-005`

## Scope

- `DynamicFetcher`
- `StealthyFetcher`
- 浏览器 session
- 等待策略、资源屏蔽、基础 stealth 配置

## Non-Goals

- 本计划不承诺首轮覆盖全部浏览器品牌
- 本计划不实现分布式浏览器编排

## Acceptance

1. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` 退出码为 `0`。
2. 动态内容、等待选择器、headless/headful、资源屏蔽至少各有一个自动化测试。
3. 反作弊条款：不得用静态 HTML fixture 冒充浏览器加载成功。

## Files

- Create: `src/main/kotlin/io/github/d4vinci/scrapling/fetchers/browser/*`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/fetchers/browser/*`

## Steps

1. 写浏览器抓取红测
2. 跑红
3. 选型并实现浏览器适配层
4. 跑绿
5. 回归 fetchers 全量测试

## Risks

- 浏览器依赖体积、操作系统兼容与 CI 稳定性是主要风险

## Current Status

- 2026-03-08：已完成 M4 首切，新增 `Playwright Java + Chromium` 浏览器抓取基线。
- 已通过验证：`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.BrowserFetchersTest"`、`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"`、`./gradlew.bat test`。
- 首次运行时 Playwright 已自动下载浏览器二进制；仓库内额外提供 `./gradlew.bat installPlaywrightChromium` 任务做显式安装。
- 当前已满足动态内容、等待选择器、headless/headful、资源屏蔽、基础 stealth 配置以及 async browser/session 的真实浏览器测试；Cloudflare、真实 page reuse、更多 stealth 语义仍待补齐。

## Delivered Slice 1

- `DynamicFetcher` / `DynamicSession`：基于 Playwright Java 提供同步浏览器抓取入口。
- `StealthyFetcher` / `StealthySession`：补齐基础 stealth 入口，当前已落地 `blockWebRtc` 与 `allowWebgl` init script。
- `BrowserFetchOptions`：统一承载 `headless`、`waitSelector`、`disableResources`、`extraHeaders`、cookies 等浏览器侧参数。
- 浏览器自动化测试：使用本地 `HttpServer` 真实验证动态 DOM、等待策略、headful/headless、资源屏蔽与 stealth 检测页。

## Delivered Slice 2

- `AsyncDynamicSession` / `AsyncStealthySession`：补齐 async browser session 外部语义与协程接口。
- `AsyncDynamicFetcher` / `AsyncStealthyFetcher`：补齐异步浏览器 fetcher 直接入口。
- `BrowserPagePool`：补齐 `maxPages`、`pagesCount`、`busyCount` 统计口径。
- 异步测试：覆盖并发请求、page pool 释放、stealth 选项、直接 fetch 与关闭后报错路径。

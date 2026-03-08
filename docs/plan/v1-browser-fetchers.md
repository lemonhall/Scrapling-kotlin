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
- 当前已满足动态内容、等待选择器、headless/headful、资源屏蔽、额外请求头、cookie 注入、network-idle 等待、基础 stealth 配置、stealth launch flags、`navigator.webdriver` 伪装、async browser/session、pageAction 与 Cloudflare 检测的真实测试；真实 page reuse、Cloudflare 求解仍待补齐。

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

## Delivered Slice 3

- `pageAction`：补齐浏览器自动化回调入口，可在返回前执行点击/滚动等页面动作。
- `CloudflareInspector`：补齐 challenge URL 正则与页面内容检测语义，当前支持 `non-interactive`、`managed`、`interactive`、`embedded`。
- 测试：新增自动化按钮点击与 Cloudflare 检测断言，确保语义不漂移。

## Delivered Slice 4

- 浏览器请求选项语义：新增 `extraHeaders`、`cookies` 与 `networkIdle` 的真实回归测试，确保 fetcher 侧选项会真正传递到浏览器上下文与导航等待策略。
- 本地测试路由：补齐 `/echo-header`、`/cookie-page`、`/idle-page`、`/idle-data`，不依赖静态 fixture，直接验证浏览器发出的请求头、页面内 cookie 与异步网络收敛行为。
- 验证：`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` 与 `./gradlew.bat test` 于 2026-03-08 再次通过。

## Delivered Slice 5

- `BrowserLaunchSupport`：对齐上游浏览器 launch 语义，补齐 `DEFAULT_ARGS` / `STEALTH_ARGS` / `HARMFUL_ARGS`，并把 `realChrome` 通道从临时的 `msedge` 修正为上游一致的 `chrome`。
- `BrowserFetchOptions.hideCanvas`：新增 `hideCanvas` 选项，并把 `blockWebRtc`、`allowWebgl`、`hideCanvas` 映射到 Chromium launch flags，减少仅靠页面注入脚本兜底的偏差。
- `BrowserStealth`：补齐 `navigator.webdriver` 伪装、`window.chrome.runtime` 兜底与 notifications permissions query 补丁；新增真实浏览器测试验证 stealth fetcher 页面内的 `navigator.webdriver` 不再暴露为 `true`。
- 验证：`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.BrowserLaunchSupportTest"`、`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` 与 `./gradlew.bat test` 于 2026-03-08 通过。

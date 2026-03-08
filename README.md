# scrapling-kotlin

`scrapling-kotlin` 的目标不是“做一个像 Scrapling 的 Kotlin 抓取库”，而是**以塔山循环为纪律，把 `E:\development\Scrapling` 的公开能力逐项、可追溯地复刻到 Kotlin/JVM**。

当前基线参考：`E:\development\openagentic-sdk-kotlin`

- Kotlin `2.0.21`
- JVM Toolchain `17`
- Gradle Wrapper `8.9`
- JUnit 5 + `kotlin-test`

## 当前进度

- `M0 Foundation`：已完成
- `M1 Parser Baseline`：已完成
- `M2 Parser Advanced`：已完成
- `M3 Static Fetchers`：已完成
- `M4 Browser Fetchers`：进行中

已落地内容：

- 文档矩阵：`docs/prd/VISION.md`、`docs/prd/PRD-0001-capability-parity.md`、`docs/plan/v1-index.md`
- 源项目审计：`docs/research/source-project-audit-2026-03-08.md`
- 解析器基础层：`Selector`、`Selectors`、`TextHandler`、`TextHandlers`、`AttributesHandler`
- 已验证语义：CSS 查询、`::text`、`::attr(...)`、`get/getall`、outer HTML、`prettify`、基础导航
- M3 首切已落地：`fetchers.static` 中的 `ResponseFactory`、`Response`、`FetcherClient`、`FetcherSession`、`HttpTransport` 抽象
- M3 第二刀已落地：`JdkHttpTransport`、真实 HTTP 请求、redirect 与 cookie session reuse 已通过本地 `HttpServer` 测试
- M3 第三刀已落地：timeout/重试真行为已通过本地 `HttpServer` 验证，当前剩余 async/proxy 语义待补齐
- M3 第四刀已落地：`AsyncFetcherClient` / `AsyncFetcherSession` 与异步真实 HTTP 已通过本地 `HttpServer` 验证，当前剩余 proxy 语义待补齐
- M4 首切已落地：`Playwright Java + Chromium` 浏览器抓取基线、dynamic/stealth 同步 fetcher、wait selector、headless/headful、资源屏蔽测试
- M4 第二刀已落地：`AsyncDynamicSession` / `AsyncStealthySession`、`BrowserPagePool` 与异步浏览器测试已落地，当前剩余 Cloudflare/真实 page reuse 语义待补齐
- M4 第三刀已落地：`pageAction` 与 `CloudflareInspector` 已落地，当前剩余真实 page reuse / Cloudflare 求解 / 更多 stealth 语义待补齐
- M4 第四刀已落地：浏览器请求选项回归测试已覆盖 `extraHeaders`、`cookies` 与 `networkIdle`，当前剩余真实 page reuse / Cloudflare 求解 / 更多 stealth 语义待补齐
- M4 第五刀已落地：stealth launch flags、`hideCanvas`、`navigator.webdriver` 伪装与 `realChrome -> chrome` 通道修正已落地，当前剩余真实 page reuse / Cloudflare 求解 待补齐
- M4 第六刀已落地：真实 page reuse / pool ready-state 已落地，`fetch()` 级 cookies 也已接入；当前剩余 Cloudflare 求解

## 快速命令

Windows PowerShell：

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"
.\gradlew.bat test --tests "io.github.d4vinci.scrapling.core.*"
.\gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"
.\gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"
.\gradlew.bat installPlaywrightChromium
```

## 仓库结构

```text
docs/
├─ prd/        # 愿景与需求定义
├─ plan/       # v1 计划、追溯矩阵、里程碑状态
└─ research/   # 对源项目/参考仓库的审计材料

src/
├─ main/kotlin/io/github/d4vinci/scrapling/
└─ test/kotlin/io/github/d4vinci/scrapling/
```

## 里程碑路线

- `M0 Foundation`：Gradle/Kotlin 工程基线、文档矩阵、首个红绿闭环
- `M1 Parser Baseline`：解析器基础对象模型与 core 富类型
- `M2 Parser Advanced`：XPath、自适应重定位、存储层
- `M3 Static Fetchers`：HTTP fetchers、session、response
- `M4 Browser Fetchers`：dynamic / stealth fetchers
- `M5 Spiders`：Spider、scheduler、checkpoint、session manager
- `M6 CLI + MCP + Docs`：CLI、Shell、MCP、用户文档

## 工作方式

- 严格按塔山循环推进：PRD → 计划 → 红测 → 绿实现 → 验证 → 回填证据
- 默认在 `main` 分支持续推进
- 每一刀尽量保持 `commit + push`
- 不允许把“概念验证”包装成“能力复刻”

## 相关文档

- 愿景：`docs/prd/VISION.md`
- PRD：`docs/prd/PRD-0001-capability-parity.md`
- v1 索引：`docs/plan/v1-index.md`
- M1 计划：`docs/plan/v1-parser-baseline.md`
- M2 计划：`docs/plan/v1-parser-advanced.md`

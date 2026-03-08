# v1 Index

## 愿景

- 愿景文档：`docs/prd/VISION.md`
- PRD：`docs/prd/PRD-0001-capability-parity.md`
- 源项目审计：`docs/research/source-project-audit-2026-03-08.md`

## 里程碑

| 里程碑 | 范围 | DoD | 验证命令 | 状态 |
|---|---|---|---|---|
| M0 Foundation | Gradle/Kotlin 工程基线、目录结构、首个红绿测试闭环 | 构建可运行、测试可执行、文档矩阵落地 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorTest"` | done |
| M1 Parser Baseline | `Selector` / `Selectors` / `TextHandler` / `AttributesHandler` 基础能力 | CSS 查询、文本/属性访问、基础导航通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*" --tests "io.github.d4vinci.scrapling.core.*"` | done |
| M2 Parser Advanced | XPath、正则、自适应重定位、存储 | parser advanced/adaptive 测试通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*" --tests "io.github.d4vinci.scrapling.core.storage.*"` | done |
| M3 Static Fetchers | HTTP fetchers、sessions、response | 静态抓取测试通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"` | done |
| M4 Browser Fetchers | dynamic / stealth fetchers | 浏览器抓取测试通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` | done |
| M5 Spiders | Spider 引擎、调度、checkpoint、会话 | spiders 测试通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.spiders.*"` | todo |
| M6 CLI + MCP + Docs | CLI、Shell、MCP、使用文档 | cli/ai/docs 验收通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.cli.*"`; `./gradlew.bat test --tests "io.github.d4vinci.scrapling.ai.*"` | todo |

## 计划索引

- `docs/plan/v1-foundation.md`
- `docs/plan/v1-parser-baseline.md`
- `docs/plan/v1-parser-advanced.md`
- `docs/plan/v1-static-fetchers.md`
- `docs/plan/v1-browser-fetchers.md`
- `docs/plan/v1-spiders-core.md`
- `docs/plan/v1-cli-and-mcp.md`

## 追溯矩阵

| Req ID | v1 Plan | 测试/命令 | 证据 | 状态 |
|---|---|---|---|---|
| REQ-0001-001 | `docs/plan/v1-parser-baseline.md`; `docs/plan/v1-parser-advanced.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"` 于 2026-03-08 通过；XPath 与 adaptive 已进入 M2 首刀，但更完整的 advanced 面仍待补齐 | doing |
| REQ-0001-002 | `docs/plan/v1-parser-advanced.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*" --tests "io.github.d4vinci.scrapling.core.storage.*"` | XPath variables、selector generation、`save/retrieve/relocate`、`urlJoin`、`re/reFirst`、SQLite storage 于 2026-03-08 通过 | done |
| REQ-0001-003 | `docs/plan/v1-parser-baseline.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.core.*"` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.core.*"` 于 2026-03-08 通过 | done |
| REQ-0001-004 | `docs/plan/v1-static-fetchers.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"` | `StaticFetchersTest` + `StaticFetchersJdkTransportTest` + `AsyncStaticFetchersTest` 已覆盖 sync/async Response 适配、真实 GET/POST/PUT/DELETE、redirect、cookie session reuse、proxy、timeout/重试、session 默认值与 double-open；`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"` 与 `./gradlew.bat test` 于 2026-03-08 通过 | done |
| REQ-0001-005 | `docs/plan/v1-browser-fetchers.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` | `BrowserFetchersTest` + `AsyncBrowserSessionsTest` + `BrowserLaunchSupportTest` + `BrowserProxySupportTest` 已覆盖 sync/async 浏览器抓取、动态内容加载、wait selector、headless/headful、资源屏蔽、额外请求头、cookie 注入、network-idle、pageAction、stealth launch flags、`navigator.webdriver` 伪装、真实 page reuse、Cloudflare 检测与基础求解 flow、`timeout/wait/retries`、`googleSearch/initScript/userDataDir/cdpUrl/extraFlags/additionalArgs/selectorConfig`、`proxy/proxyRotator`、page pool 统计与 session double-open；`./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` 与 `./gradlew.bat test` 于 2026-03-09 通过；M4 Browser Fetchers DoD 达成 | done |
| REQ-0001-006 | `docs/plan/v1-spiders-core.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.spiders.*"` | 待执行 | todo |
| REQ-0001-007 | `docs/plan/v1-cli-and-mcp.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.cli.*"` | 待执行 | todo |
| REQ-0001-008 | `docs/plan/v1-cli-and-mcp.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.ai.*"` | 待执行 | todo |
| REQ-0001-009 | `docs/plan/v1-cli-and-mcp.md`; `docs/plan/v1-spiders-core.md` | 文档人工复核 | 待执行 | todo |
| REQ-0001-010 | `docs/plan/v1-foundation.md` | 文档人工复核；`./gradlew.bat test` | `README`、Vision、PRD、v1-index、审计文档已创建，`./gradlew.bat test` 于 2026-03-08 通过 | done |
| REQ-0001-011 | `docs/plan/v1-foundation.md` | `./gradlew.bat test` | Gradle Wrapper + Kotlin 2.0.21 + JVM 17 基线已落地，`./gradlew.bat test` 于 2026-03-08 通过 | done |

## ECN 索引

- 当前无 ECN。

## 差异列表

- 当前仓库已初始化 Git，并启用按 slice `commit + push` 的交付纪律。
- M4 已锁定 `Playwright Java + Chromium` 方案，当前已落地 sync/async 浏览器抓取基线、真实 page reuse、browser request options 语义测试、stealth launch parity、pageAction、Cloudflare 检测与基础求解 flow、`timeout/wait/retries`、`googleSearch/initScript/userDataDir/cdpUrl/extraFlags/additionalArgs/selectorConfig`。
- 解析层当前已完成 M1 + M2；M3 Static Fetchers 与 M4 Browser Fetchers 均已完成，浏览器抓取能力已补齐 `proxy/proxyRotator` 对齐。

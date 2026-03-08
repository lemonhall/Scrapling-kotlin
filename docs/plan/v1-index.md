# v1 Index

## 愿景

- 愿景文档：`docs/prd/VISION.md`
- PRD：`docs/prd/PRD-0001-capability-parity.md`
- 源项目审计：`docs/research/source-project-audit-2026-03-08.md`

## 里程碑

| 里程碑 | 范围 | DoD | 验证命令 | 状态 |
|---|---|---|---|---|
| M0 Foundation | Gradle/Kotlin 工程基线、目录结构、首个红绿测试闭环 | 构建可运行、测试可执行、文档矩阵落地 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorTest"` | done |
| M1 Parser Baseline | `Selector` / `Selectors` / `TextHandler` / `AttributesHandler` 基础能力 | CSS 查询、文本/属性访问、基础导航通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"` | doing |
| M2 Parser Advanced | XPath、正则、自适应重定位、存储 | parser advanced/adaptive 测试通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*Adaptive*"` | todo |
| M3 Static Fetchers | HTTP fetchers、sessions、response | 静态抓取测试通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"` | todo |
| M4 Browser Fetchers | dynamic / stealth fetchers | 浏览器抓取测试通过 | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` | todo |
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
| REQ-0001-001 | `docs/plan/v1-parser-baseline.md`; `docs/plan/v1-parser-advanced.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorTest"` 于 2026-03-08 通过；更广 parser 套件待补齐 | doing |
| REQ-0001-002 | `docs/plan/v1-parser-advanced.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*Adaptive*"` | 待执行 | todo |
| REQ-0001-003 | `docs/plan/v1-parser-baseline.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.core.*"` | `TextHandler` / `AttributesHandler` 已被 `SelectorTest` 间接验证；独立 core 测试待补齐 | doing |
| REQ-0001-004 | `docs/plan/v1-static-fetchers.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"` | 待执行 | todo |
| REQ-0001-005 | `docs/plan/v1-browser-fetchers.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` | 待执行 | todo |
| REQ-0001-006 | `docs/plan/v1-spiders-core.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.spiders.*"` | 待执行 | todo |
| REQ-0001-007 | `docs/plan/v1-cli-and-mcp.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.cli.*"` | 待执行 | todo |
| REQ-0001-008 | `docs/plan/v1-cli-and-mcp.md` | `./gradlew.bat test --tests "io.github.d4vinci.scrapling.ai.*"` | 待执行 | todo |
| REQ-0001-009 | `docs/plan/v1-cli-and-mcp.md`; `docs/plan/v1-spiders-core.md` | 文档人工复核 | 待执行 | todo |
| REQ-0001-010 | `docs/plan/v1-foundation.md` | 文档人工复核；`./gradlew.bat test` | `README`、Vision、PRD、v1-index、审计文档已创建，`./gradlew.bat test` 于 2026-03-08 通过 | done |
| REQ-0001-011 | `docs/plan/v1-foundation.md` | `./gradlew.bat test` | Gradle Wrapper + Kotlin 2.0.21 + JVM 17 基线已落地，`./gradlew.bat test` 于 2026-03-08 通过 | done |

## ECN 索引

- 当前无 ECN。

## 差异列表

- 当前目录不是 Git 仓库，`commit + push` Gate 暂未执行。
- 浏览器抓取技术选型尚未锁定到具体实现层，需在 M4 前形成明确方案。
- 解析层当前已完成 baseline 首个红绿切片，XPath 与 adaptive 能力尚未落地。

# v1 CLI And MCP

## Goal

让 Kotlin 版本不仅能当库使用，也具备与源项目一致的 CLI 与 MCP 对外入口。

## PRD Trace

- `REQ-0001-007`
- `REQ-0001-008`
- `REQ-0001-009`

## Scope

- CLI 基础命令
- interactive shell
- extract 子命令
- MCP server 与工具暴露
- 对应用户文档

## Non-Goals

- 本计划不包含多语言文档翻译
- 本计划不包含远程托管平台适配

## Acceptance

1. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.cli.*"` 退出码为 `0`。
2. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.ai.*"` 退出码为 `0`。
3. `docs/` 下存在 CLI、MCP、教程索引文档。
4. 反作弊条款：CLI 测试不能只断言进程启动；必须断言帮助输出、参数语义或产物文件。

## Files

- Create: `src/main/kotlin/io/github/d4vinci/scrapling/cli/*`
- Create: `src/main/kotlin/io/github/d4vinci/scrapling/ai/*`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/cli/*`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/ai/*`
- Create: `docs/cli/*`
- Create: `docs/ai/*`

## Steps

1. 先写 CLI 帮助与参数红测
2. 再写 MCP 工具暴露红测
3. 分别实现 CLI 与 MCP
4. 跑绿
5. 补文档并更新追溯矩阵

## Risks

- CLI 解析库与协程/流式 IO 设计会影响 MCP 实现边界

## Status

- State: done
- Completed At: `2026-03-09`
- DoD: met

## Delivered Slice

- `ScraplingCli` 提供 `install`、`shell`、`extract`、`mcp` 四类对外入口。
- `ScraplingShell` 暴露 `get`、`post`、`Fetcher`、`DynamicFetcher`、`view`、`uncurl` 等 shell namespace 对象。
- `ContentExtractor` 统一处理 HTML 到 Markdown / HTML / Text 的导出，并按 `.md` / `.html` / `.txt` 落盘。
- `ScraplingMcpServer` 暴露 `get`、`bulk_get`、`fetch`、`bulk_fetch`、`stealthy_fetch`、`bulk_stealthy_fetch`。
- `docs/` 新增 parser、fetchers、spiders、cli、ai、tutorials、api 索引文档，构成 M6 用户入门矩阵。

## Verification

- `./gradlew.bat test --tests "io.github.d4vinci.scrapling.cli.*"` 于 `2026-03-09` 通过。
- `./gradlew.bat test --tests "io.github.d4vinci.scrapling.ai.*"` 于 `2026-03-09` 通过。
- `./gradlew.bat test` 于 `2026-03-09` 通过。

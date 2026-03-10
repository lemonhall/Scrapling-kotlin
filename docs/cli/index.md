# CLI

## 详细页面

- `docs/cli/interactive-shell.md`

## 顶级命令

- `install`
- `shell`
- `extract`
- `mcp`

## Shell（交互式）

- 轻量交互式 Shell，helper 会真实执行抓取逻辑。
- 支持 `namespace()` 与 `help()`。
- 维护 `page` / `response` / `pages` 三个状态对象。
- 支持用分号分隔的多语句执行。
- 提供 `uncurl(...)` 与 `curl2fetcher(...)` 作为辅助工具。

## Extract（导出）

- 静态子命令：`get` / `post` / `put` / `delete`
- 浏览器子命令：`fetch` / `stealthy-fetch`
- 静态高频 flags（部分示例）：headers、cookies、params、data、json、timeout、proxy、impersonate、selector

## MCP（AI 工具入口）

- `scrapling-kotlin mcp`：`stdio`
- `scrapling-kotlin mcp --http --host 127.0.0.1 --port 8000`：`streamable-http`

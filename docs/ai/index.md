# MCP / AI

## 详细页面

- `docs/ai/mcp-server.md`

## 工具

- `get`
- `bulk_get`
- `fetch`
- `bulk_fetch`
- `stealthy_fetch`
- `bulk_stealthy_fetch`

## 传输方式

- `stdio`（标准输入输出）
- `streamable-http`（HTTP：`/health`、`/mcp`）

## 当前协议面

- 最小 MCP / JSON-RPC 握手：`initialize`、`notifications/initialized`、`tools/list`、`tools/call`
- tool call 支持结构化返回：`content` 与 `structuredContent`
- Windows pipe 上 `stdio` 支持 BOM 容忍
- HTTP transport：`GET /health`、`POST /mcp`，并对 `GET /mcp` 明确返回 `405`

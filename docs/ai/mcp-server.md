# MCP 服务端

## 启动

```powershell
.\gradlew.bat run --args="mcp"
.\gradlew.bat run --args="mcp --http --host 127.0.0.1 --port 8000"
```

## 传输方式

- `stdio`
- `streamable-http`

## 当前协议面

- `initialize`
- `notifications/initialized`
- `tools/list`
- `tools/call`

## 工具族

- `get` / `bulk_get`
- `fetch` / `bulk_fetch`
- `stealthy_fetch` / `bulk_stealthy_fetch`

## 说明

- 静态工具调用暴露高频请求选项（部分示例）：`impersonate`、`timeout`、`retries`、`retryDelay`、`proxy`、`proxyAuth`、`auth`、`verify`、`http3`。
- 浏览器工具调用保持 `BrowserFetchOptions` 语义。
- 静态传输对不支持的选项会显式 fail-fast，而不是静默忽略。

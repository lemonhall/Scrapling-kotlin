# Getting Started

## 1. 运行测试

```powershell
.\gradlew.bat test
```

## 2. 运行 CLI

```powershell
.\gradlew.bat run --args="extract get https://example.com output.md"
```

## 3. 启动 Spider

- 定义继承 `Spider` 的类
- 实现 `parse(response)`
- 调用 `start()` 或 `stream()`

## 4. 启动 MCP

```powershell
.\gradlew.bat run --args="mcp --http --host 127.0.0.1 --port 8000"
```

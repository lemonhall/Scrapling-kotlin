# 交互式 Shell

## 启动

```powershell
.\gradlew.bat run --args="shell"
.\gradlew.bat run --args='shell -c "namespace()"'
.\gradlew.bat run --args='shell -c "get(\"https://example.com\"); page.url"'
```

## Helpers

- `get` / `post` / `put` / `delete`
- `fetch` / `stealthy_fetch`
- `namespace` / `help`
- `view`
- `uncurl` / `curl2fetcher`

## 状态对象

- `page`
- `response`
- `pages`

## 说明

- Helpers 会真实执行抓取逻辑。
- 每次请求后会自动刷新 `page` / `response` / `pages`。
- 支持 `len(pages)`、`pages[0]`、`pages[-1]`、`pages[0].url` 等常用表达式。
- Shell 被刻意保持轻量：它不是一个完整的 Python 解释器。

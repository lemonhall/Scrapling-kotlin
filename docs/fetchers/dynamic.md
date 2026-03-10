# 动态抓取（Dynamic Fetchers）

## 入口

- `DynamicFetcher`
- `AsyncDynamicFetcher`
- `DynamicSession`
- `AsyncDynamicSession`
- `BrowserFetchOptions`
- `BrowserPagePool`

## 支持的行为

- Playwright + Chromium 浏览器执行
- headless / headful 模式
- `timeout` / `wait` / `waitSelector` / `networkIdle` / `disableResources`
- `extraHeaders` / `cookies` / `proxy` / `googleSearch` / `initScript` / `userDataDir` / `cdpUrl` / `selectorConfig`
- session 与 page reuse

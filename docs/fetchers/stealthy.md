# 隐身抓取（Stealthy Fetchers）

## 入口

- `StealthyFetcher`
- `AsyncStealthyFetcher`
- `StealthySession`
- `AsyncStealthySession`
- `CloudflareInspector`
- `CloudflareSolver`

## 支持的行为

- stealth 启动参数与 page 级别的反检测设置
- `realChrome` / `blockWebRtc` / `hideCanvas` / `allowWebgl` / `solveCloudflare`
- proxy 支持、额外启动参数、init script、持久化浏览器 profile

## 边界说明

- stealth 的目标是更接近真实浏览器，但不保证对所有站点都能绕过检测。
- `solveCloudflare` 仅启用当前内建流程，不代表“通用绕过”承诺。

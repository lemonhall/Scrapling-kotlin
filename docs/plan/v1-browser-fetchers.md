# v1 Browser Fetchers

## Goal

交付 Kotlin 版动态抓取与 stealth 抓取，使源项目的浏览器能力在 JVM 侧有等价落点。

## PRD Trace

- `REQ-0001-005`

## Scope

- `DynamicFetcher`
- `StealthyFetcher`
- 浏览器 session
- 等待策略、资源屏蔽、基础 stealth 配置

## Non-Goals

- 本计划不承诺首轮覆盖全部浏览器品牌
- 本计划不实现分布式浏览器编排

## Acceptance

1. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` 退出码为 `0`。
2. 动态内容、等待选择器、headless/headful、资源屏蔽至少各有一个自动化测试。
3. 反作弊条款：不得用静态 HTML fixture 冒充浏览器加载成功。

## Files

- Create: `src/main/kotlin/io/github/d4vinci/scrapling/fetchers/browser/*`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/fetchers/browser/*`

## Steps

1. 写浏览器抓取红测
2. 跑红
3. 选型并实现浏览器适配层
4. 跑绿
5. 回归 fetchers 全量测试

## Risks

- 浏览器依赖体积、操作系统兼容与 CI 稳定性是主要风险


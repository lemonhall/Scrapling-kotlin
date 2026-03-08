# v1 Spiders Core

## Goal

复刻源项目的 Spider 框架基本面，让 Kotlin 版本具备可暂停、可恢复、可并发调度的爬虫能力。

## PRD Trace

- `REQ-0001-006`

## Scope

- `Request`
- `Spider`
- `CrawlerEngine`
- `Scheduler`
- `SessionManager`
- `CheckpointManager`
- `CrawlResult` / `CrawlStats`

## Non-Goals

- 本计划不做分布式架构
- 本计划不做 UI 面板

## Acceptance

1. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.spiders.*"` 退出码为 `0`。
2. 必须覆盖并发、checkpoint、blocked retry、会话配置、统计输出。
3. 反作弊条款：不得以单线程顺序执行伪装成调度器完成。

## Files

- Create: `src/main/kotlin/io/github/d4vinci/scrapling/spiders/*`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/spiders/*`

## Steps

1. 先写数据模型红测
2. 再写 engine/scheduler 红测
3. 逐层实现
4. 跑全量 spiders 测试到绿

## Risks

- 并发模型与 session 生命周期的边界需要先在测试里锁住

## Delivered Slice 1

- `Request`：补齐 URL、session id、priority、meta、session options、SHA-1 fingerprint、copy、序列化后 callback 名称保留与 `restoreCallback()` 基础语义。
- `ItemList` / `CrawlStats` / `CrawlResult`：补齐 items 导出、crawl 统计累计、结果 completed 视图与基础迭代能力。
- `Scheduler` / `CheckpointManager`：补齐优先级队列、去重、snapshot/restore、checkpoint 原子写入与加载清理。
- `SessionManager` / `StaticSpiderSession`：补齐默认 session、lazy session、生命周期管理、request meta 合并与 async static fetch 适配层。
- `Spider` / `CrawlerEngine`：补齐默认 logger、`BLOCKED_CODES`、默认 session 配置、`startRequests()`、`pause()`/`stats` 边界与 crawl engine 基础骨架。
- 验证：`./gradlew.bat test --tests "io.github.d4vinci.scrapling.spiders.*"` 于 2026-03-09 通过；M5 当前进入真正的 engine 执行语义阶段。

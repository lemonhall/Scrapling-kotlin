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


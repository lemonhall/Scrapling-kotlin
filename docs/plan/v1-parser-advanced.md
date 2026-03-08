# v1 Parser Advanced

## Goal

在 parser baseline 之上补齐 XPath、正则辅助、自适应重定位与存储层，使 Kotlin 解析器进入“真正的 Scrapling 级别”。

## PRD Trace

- `REQ-0001-001`
- `REQ-0001-002`

## Scope

- XPath 查询
- 正则搜索入口
- adaptive 保存 / 检索 / 重定位
- 存储层抽象与默认实现

## Current Slice

- 已完成首刀：XPath variables、`SQLiteStorageSystem`、adaptive 默认初始化、基于相似度的元素重定位
- 下一刀继续补：更广 XPath 语法、显式 `save/retrieve` 覆盖、更多 adaptive 边界测试

## Non-Goals

- 本计划不交付 HTTP Fetchers
- 本计划不交付 CLI shell

## Acceptance

1. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"` 退出码为 `0`。
2. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorAdvancedTest" --tests "io.github.d4vinci.scrapling.core.storage.SQLiteStorageSystemTest"` 退出码为 `0`。
3. adaptive 测试必须至少包含一个 DOM 结构变形夹具，而不是同一页面重复保存/读取。
4. 反作弊条款：XPath variables 不能只做字符串 contains 匹配，必须经过 XPath 或等价查询路径落到节点结果。

## Files

- Create: `src/main/kotlin/io/github/d4vinci/scrapling/core/storage/*`
- Modify: `src/main/kotlin/io/github/d4vinci/scrapling/parser/Selector.kt`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/parser/SelectorAdvancedTest.kt`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/core/storage/SQLiteStorageSystemTest.kt`

## Steps

1. 先写 XPath / storage / adaptive 红测
2. 跑到红并记录失败原因
3. 补存储、XPath 与重定位实现
4. 跑到绿
5. 回归 parser / core / 全仓测试

## Risks

- XPath 实现策略如果选错，后续维护成本会明显放大

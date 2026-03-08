# v1 Parser Baseline

## Goal

交付 Kotlin 版解析器最小可用对象模型，让 `Selector` / `Selectors` / `TextHandler` / `AttributesHandler` 进入真实红绿循环。

## PRD Trace

- `REQ-0001-001`
- `REQ-0001-003`

## Scope

- `Selector`：HTML 输入、CSS 查询、首个元素查询、基本导航、HTML/文本/属性访问
- `Selectors`：集合包装、`first/last/length`、二次 CSS 选择
- `TextHandler`：文本值、清洗、JSON 解析、正则提取
- `AttributesHandler`：属性访问与基础搜索

## Non-Goals

- 本计划不交付 XPath 完整支持
- 本计划不交付 adaptive relocate
- 本计划不交付 Spider / Fetcher 集成

## Acceptance

1. `SelectorTest` 覆盖：CSS 查询、直接文本、递归文本、属性 JSON 解析、children/siblings。
2. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorTest"` 退出码为 `0`。
3. 反作弊条款：解析逻辑必须基于真实 DOM 解析，不得通过夹具字符串分支硬编码返回结果。
4. 反作弊条款：`text` 与 `getAllText()` 必须区分直接文本和递归文本，不能共用同一实现结果。

## Files

- Create: `src/main/kotlin/io/github/d4vinci/scrapling/core/TextHandler.kt`
- Create: `src/main/kotlin/io/github/d4vinci/scrapling/core/TextHandlers.kt`
- Create: `src/main/kotlin/io/github/d4vinci/scrapling/core/AttributesHandler.kt`
- Create: `src/main/kotlin/io/github/d4vinci/scrapling/parser/Selector.kt`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/parser/SelectorTest.kt`

## Steps

1. 写失败测试：解析 fixture HTML 并断言基础语义
2. 运行 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorTest"` 到红
3. 实现最小通过代码
4. 运行相同命令到绿
5. 运行 `./gradlew.bat test`，确认基础回归仍绿

## Risks

- `jsoup` 没有直接等价的 XPath 能力，后续需补桥接层或替换实现
- 文本空白折叠语义需要 fixture 明确锁定


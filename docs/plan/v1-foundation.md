# v1 Foundation

## Goal

把空目录变成一个可以持续执行塔山循环的 Kotlin 工程基础盘：能写文档、能跑测试、能开始 parser baseline 实施。

## PRD Trace

- `REQ-0001-010`
- `REQ-0001-011`

## Scope

- 创建 Gradle Wrapper 与 Kotlin/JVM 构建文件
- 固化包命名 `io.github.d4vinci.scrapling`
- 建立 `docs/prd`、`docs/plan`、`docs/research`、`src/main/kotlin`、`src/test/kotlin` 目录层次
- 建立 parser baseline 的首个红测入口

## Non-Goals

- 本计划不交付 HTTP Fetchers
- 本计划不交付浏览器自动化
- 本计划不交付 Spider 引擎

## Acceptance

1. 根目录存在 `gradlew`、`gradlew.bat`、`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`。
2. 命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorTest"` 可执行并用于驱动红绿循环。
3. 文档矩阵至少包含愿景、PRD、v1-index、源项目审计四类文件。
4. 反作弊条款：主源码目录不得仅由 `TODO("not implemented")`、`UnsupportedOperationException("not implemented")` 或空类组成并宣称完成。

## Files

- Create: `README.md`
- Create: `.gitignore`
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `docs/research/source-project-audit-2026-03-08.md`
- Create: `docs/prd/VISION.md`
- Create: `docs/prd/PRD-0001-capability-parity.md`
- Create: `docs/plan/v1-index.md`
- Create: `src/test/kotlin/io/github/d4vinci/scrapling/parser/SelectorTest.kt`

## Steps

1. 写 `SelectorTest` 的失败测试（红）
2. 运行 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.SelectorTest"` 到红
3. 实现 parser baseline 的最小代码（绿）
4. 再次运行同一命令到绿
5. 运行 `./gradlew.bat test` 做基础回归
6. 更新 `docs/plan/v1-index.md` 中的证据与状态

## Risks

- 首次使用 Gradle Wrapper 可能触发下载耗时
- `jsoup` 与 `lxml` 行为差异会在 parser 阶段暴露


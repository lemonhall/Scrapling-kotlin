# scrapling-kotlin

`scrapling-kotlin` 的目标不是“做一个类似 Scrapling 的 Kotlin 抓取库”，而是**按塔山循环把 `E:\development\Scrapling` 的能力逐项、可追溯地复刻到 Kotlin**。

当前阶段已建立：

- 愿景文档：`docs/prd/VISION.md`
- 首份 PRD：`docs/prd/PRD-0001-capability-parity.md`
- v1 索引：`docs/plan/v1-index.md`
- 源项目审计：`docs/research/source-project-audit-2026-03-08.md`

首个实施切片聚焦解析器基础层：

- `Selector`
- `Selectors`
- `TextHandler`
- `AttributesHandler`

约束基线参考：`E:\development\openagentic-sdk-kotlin`

- Kotlin `2.0.21`
- JVM Toolchain `17`
- Gradle Wrapper `8.9`
- JUnit 5 + `kotlin-test`


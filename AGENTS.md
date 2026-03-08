# Agent Notes: scrapling-kotlin

## Project Overview

`scrapling-kotlin` 的目标是把 `E:\development\Scrapling` 的能力按塔山循环逐项复刻到 Kotlin/JVM，而不是做一个“类似物”或只覆盖局部能力的缩减版。

当前基线参考仓库：`E:\development\openagentic-sdk-kotlin`

- Kotlin `2.0.21`
- JVM Toolchain `17`
- Gradle Wrapper `8.9`
- 测试：JUnit 5 + `kotlin-test`

## Current Milestone State

- `M0 Foundation`：done
- `M1 Parser Baseline`：done
- `M2 Parser Advanced`：done
- `M3 Static Fetchers`：done
- `M3 Static Fetchers`：sync/async + proxy + timeout/重试 已落地；下一里程碑为 `M4 Browser Fetchers`
- `M4 Browser Fetchers`：done
- `M4 Browser Fetchers`：Playwright Java + Chromium sync/async 基线、browser request option 语义测试、stealth launch parity、真实 page reuse、pageAction、Cloudflare 检测与基础求解 flow、`timeout/wait/retries`、`googleSearch/initScript/userDataDir/cdpUrl/extraFlags/additionalArgs/selectorConfig`、`proxy/proxyRotator` 已落地；M4 DoD 已达成
- `M5 Spiders`：in progress
- `M5 Spiders`：第二刀已落地 `dump()` 与 `CrawlerEngine` 基础执行语义，覆盖初始化状态、allowed domains、rate limiter、request normalize、pause/checkpoint 与基础 crawl 统计；当前剩余真实并发调度、blocked retry、item/stream 完整闭环

## Default Workflow

- 默认持续推进，不做碎片化汇报；除非阻塞、失败、风险升级或需要人类确认。
- 默认直接在当前仓库工作；**不要创建 worktree**，本项目只有一个 agent 在维护。
- 任何行为变更都先走测试：先写红测，再写最小实现，再跑绿。
- 每一刀都尽量保持：实现 → 验证 → `git add -A` → `git commit` → `git push`。
- 完成一轮任务后，使用 `apn-pushtool send` 发送简短通知。

## Quick Commands

- 全量测试：`.\gradlew.bat test`
- Parser：`.\gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"`
- Core：`.\gradlew.bat test --tests "io.github.d4vinci.scrapling.core.*"`
- Static Fetchers：`.\gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"`
- Browser Fetchers：`.\gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"`
- Install Chromium：`.\gradlew.bat installPlaywrightChromium`

## File / Doc Discipline

- 改 public behavior、里程碑状态或执行范围时，同步更新：
  - `docs/plan/v1-index.md`
  - 对应 `docs/plan/v1-*.md`
  - 必要时更新 `README.md`
- 如果发现 PRD/计划与现实不符，优先补 ECN 或至少把差异写入 `v1-index`，不要只改代码。

## Coding Conventions

- 包名统一：`io.github.d4vinci.scrapling`
- 优先保持实现简单、可验证、可替换
- 不要为了追求 Kotlin idiomatic 而删除源项目的重要语义
- 主源码中不要保留 `TODO("not implemented")` 之类的伪完成占位

## Safety

- 不要执行未确认的危险删除操作
- 不要提交 `.env`、密钥、token 等敏感信息
- PowerShell 连续命令使用 `;`，不要用 `&&`

# Scrapling 源项目审计（2026-03-08）

## 审计范围

- 源项目：`E:\development\Scrapling`
- Kotlin 参考项目：`E:\development\openagentic-sdk-kotlin`
- 审计目标：建立 Kotlin 复刻的愿景、需求边界、计划分层与技术约束

## 源项目事实清单

- 发行版本：`0.4.1`
- Python 要求：`>=3.10`
- 核心依赖：`lxml`、`cssselect`、`orjson`、`tld`、`w3lib`
- 可选能力组：`fetchers`、`ai`、`shell`、`all`
- CLI 入口：`scrapling = scrapling.cli:main`

## 代码结构盘点

- `scrapling/parser.py`
  - 核心类型：`Selector`、`Selectors`
  - 核心能力：CSS/XPath 选择、文本/属性访问、导航、正则搜索、自适应重定位、HTML 序列化
- `scrapling/core/*`
  - `custom_types.py`：`TextHandler`、`TextHandlers`、`AttributesHandler`
  - `storage.py`：`StorageSystemMixin`、`SQLiteStorageSystem`
  - `shell.py`：交互式 Shell、HTML→Markdown 转换、Curl 解析
  - `ai.py`：`ScraplingMCPServer`
- `scrapling/fetchers/*`
  - `Fetcher`、`AsyncFetcher`
  - `DynamicFetcher`、`DynamicSession`、`AsyncDynamicSession`
  - `StealthyFetcher`、`StealthySession`、`AsyncStealthySession`
  - `ProxyRotator`
- `scrapling/spiders/*`
  - `Request`、`CrawlerEngine`、`SessionManager`、`Scheduler`
  - `CheckpointManager`、`CrawlResult`、`CrawlStats`
  - `Spider` 抽象基类与暂停/恢复机制

## 测试矩阵盘点

源项目共有 `34` 个测试文件：

- `tests/ai`: `1`
- `tests/cli`: `2`
- `tests/core`: `2`
- `tests/fetchers`: `18`
- `tests/parser`: `4`
- `tests/spiders`: `7`

这说明复刻优先级必须是：

1. 解析器与自定义类型
2. Fetchers 与 Response 语义
3. Spider 调度与会话体系
4. CLI / MCP / Shell

## 文档矩阵盘点

源项目文档已按能力域拆分：

- `docs/parsing/*`
- `docs/fetching/*`
- `docs/spiders/*`
- `docs/cli/*`
- `docs/ai/*`
- `docs/api-reference/*`
- `docs/tutorials/*`

这意味着 Kotlin 端不能只做 API；还必须同步形成面向使用者的学习路径和 API 参考。

## 公开卖点提炼

从 `README.md` 和文档目录可归纳出五条必须守住的价值承诺：

1. 同一个库覆盖解析、请求、浏览器抓取、爬虫、CLI、MCP
2. 解析器不是“只会 CSS”的薄封装，而是可导航、可重定位、可提取文本/属性的富对象系统
3. Fetchers 同时覆盖静态请求、动态浏览器与 stealth 抗检测抓取
4. Spider 层支持并发、会话、检查点、代理轮换与结果统计
5. 对外体验要简洁，不能让 Kotlin 版本沦为“概念验证”

## Kotlin 侧约束基线

参考 `E:\development\openagentic-sdk-kotlin`，当前仓库采用：

- Kotlin：`2.0.21`
- JVM Toolchain：`17`
- Gradle Wrapper：`8.9`
- 代码风格：`official`
- 测试：JUnit 5 + `kotlin-test`
- 依赖选择倾向：核心依赖精简、优先确定性离线测试

## Kotlin 复刻策略

### 语义优先级

1. **源项目能力语义一致**
2. **测试与夹具结果一致**
3. **Kotlin 语法层尽量自然，但不能牺牲源语义**

### 分层映射

- 解析层：优先用 `jsoup` 起步，先复刻对象模型与行为，再补 XPath/适配重定位
- 静态抓取层：优先用 JVM 原生 HTTP 客户端体系实现稳定基础能力
- 浏览器层：后续独立一层，避免浏览器依赖污染解析/静态抓取核心
- Spider 层：先实现数据模型与调度契约，再接入 fetchers

## 当前已知风险

- Python `lxml/cssselect` 与 Kotlin `jsoup` 的 DOM/选择器细节存在差异，必须依赖 fixture-based tests 收敛
- Python 的“单库全能”体验，在 JVM 侧需要谨慎处理浏览器依赖体积与平台兼容
- 当前目录不是 Git 仓库；在未明确远端和初始化策略前，无法执行塔山循环中的 `commit + push` Gate


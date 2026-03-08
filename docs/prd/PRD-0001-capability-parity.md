# PRD-0001: scrapling-kotlin capability parity

## Vision

见 `docs/prd/VISION.md`。

## Background

源项目 `Scrapling` 已经形成“解析器 + Fetchers + Spider 框架 + CLI + MCP + 教程文档”的完整产品面。当前目录为空目录，本 PRD 定义 Kotlin 复刻的第一性原则：**能力一致、证据一致、追溯一致。**

## Product Goal

交付一个基于 Kotlin/JVM 的 `scrapling-kotlin`，让用户可以用 Kotlin 完成源项目已承诺的主要抓取工作流，并通过分阶段计划逐步收敛到能力完全等价。

## Non-Goals

- 不在 v1 中发散到 Android UI、桌面 UI 或 SaaS 服务端产品化
- 不优先追求比源项目更多的新特性
- 不为了“先跑起来”永久降低公开能力边界

## Requirements

### REQ-0001-001 解析器核心对象模型 parity

- **动机**：`Selector` / `Selectors` 是源项目全部上层能力的基础对象模型。
- **范围**：`Selector`、`Selectors`、CSS/XPath 查询、元素导航、HTML/文本访问、常见集合操作。
- **非目标**：本条不负责浏览器抓取，也不负责 Spider 调度。
- **验收口径**：`src/test/kotlin/io/github/d4vinci/scrapling/parser/*` 中必须存在与源项目 parser 测试域对应的 Kotlin 测试；命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*"` 退出码为 `0`；测试夹具输出与批准数据一致。

### REQ-0001-002 自适应重定位与持久化 parity

- **动机**：源项目区别于普通 HTML 解析库的重要能力之一，是页面结构变化后的元素重定位。
- **范围**：自适应定位入口、指纹/存储层、重定位 API、回归测试夹具。
- **非目标**：本条不负责代理轮换，也不负责浏览器挑战求解。
- **验收口径**：存在独立的 adaptive 测试套件；命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.parser.*Adaptive*"` 退出码为 `0`；至少一个页面变形夹具证明重定位成功且非硬编码路径匹配。

### REQ-0001-003 TextHandler / AttributesHandler parity

- **动机**：源项目不是直接暴露裸字符串，而是通过富类型提供 JSON、正则、清洗和属性检索能力。
- **范围**：`TextHandler`、`TextHandlers`、`AttributesHandler` 的核心方法与常见转换能力。
- **非目标**：不负责所有字符串标准库 API 的一比一封装。
- **验收口径**：命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.core.*"` 退出码为 `0`；测试必须覆盖正常、异常、边界三类场景；不得只返回裸 `String` 规避富类型语义。

### REQ-0001-004 静态 Fetchers 与 Response parity

- **动机**：基础 HTTP 抓取是 CLI、MCP、Spider 的公共底座。
- **范围**：同步/异步静态请求、请求参数、Headers/Cookies/Proxy、Response 与 parser 集成、Session。
- **非目标**：本条不包含浏览器自动化。
- **验收口径**：命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.static.*"` 退出码为 `0`；测试覆盖 GET/POST/PUT/DELETE、会话复用、响应解析、异常处理；不得用 fixture 假装网络层存在。

### REQ-0001-005 动态 / stealth 浏览器抓取 parity

- **动机**：源项目对外承诺可处理动态页面与抗检测场景。
- **范围**：`DynamicFetcher`、`StealthyFetcher`、对应 Session、浏览器配置与页面等待策略。
- **非目标**：本条不要求在第一阶段支持全部浏览器品牌。
- **验收口径**：命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.fetchers.browser.*"` 退出码为 `0`；至少包含动态内容加载、等待选择器、资源屏蔽、stealth 配置测试；不得以“直接请求静态 HTML”冒充浏览器抓取通过。

### REQ-0001-006 Spider 框架 parity

- **动机**：源项目不是请求工具集合，而是完整爬虫框架。
- **范围**：`Request`、`Spider`、`CrawlerEngine`、`Scheduler`、`SessionManager`、`CheckpointManager`、`CrawlResult`、`CrawlStats`。
- **非目标**：不要求 v1 首轮就提供分布式集群能力。
- **验收口径**：命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.spiders.*"` 退出码为 `0`；测试覆盖并发、域名限制、暂停/恢复、blocked retry、结果统计；不得用单线程 happy-path 替代并发语义。

### REQ-0001-007 CLI parity

- **动机**：源项目提供安装、交互 shell、页面提取等 CLI 工作流。
- **范围**：`install`、`shell`、`extract` 命令组及子命令语义。
- **非目标**：不要求一开始就覆盖所有平台 shell 彩色体验。
- **验收口径**：命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.cli.*"` 退出码为 `0`；CLI 帮助输出、参数解析、文件产物路径都必须可断言；不得只验证命令能启动。

### REQ-0001-008 MCP / AI 集成 parity

- **动机**：源项目已经公开提供 MCP server 工作流。
- **范围**：MCP server 启动、stdio/http 传输、get/fetch/stealthy-fetch 工具暴露。
- **非目标**：不负责任何商业模型托管服务。
- **验收口径**：命令 `./gradlew.bat test --tests "io.github.d4vinci.scrapling.ai.*"` 退出码为 `0`；工具清单、参数契约、基础调用链全部可验证。

### REQ-0001-009 文档与教程 parity

- **动机**：如果只有代码没有文档，Kotlin 社区不会真正获得“同等能力”。
- **范围**：概览、解析、抓取、Spider、CLI、MCP、教程与 API 参考。
- **非目标**：不要求首轮就做多语言翻译。
- **验收口径**：`docs/` 下必须存在与源项目主要文档域对应的 Kotlin 文档索引；`docs/plan/v1-index.md` 追溯矩阵中每条已完成需求都有文档链接；不得出现“功能完成但文档缺失”的 done 状态。

### REQ-0001-010 测试与追溯矩阵 parity

- **动机**：用户要求的是“像素级复刻”，这必须依赖逐域证据链而不是口头承诺。
- **范围**：需求编号、计划索引、测试命令、证据记录、差异清单。
- **非目标**：不要求在第一轮就实现自动生成全部矩阵。
- **验收口径**：`docs/plan/v1-index.md` 中每个 `Req ID` 都有对应计划和验证命令；不存在空白的 plan/test 列；文档完整性检查通过人工复核。

### REQ-0001-011 Kotlin 工程基线一致性

- **动机**：空目录起步时，需要先固化可持续迭代的 Kotlin 工程约束。
- **范围**：Gradle Wrapper、Kotlin/JVM 版本、目录结构、测试入口、包命名与编码规范。
- **非目标**：不要求在本条中引入所有未来依赖。
- **验收口径**：存在 `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、`gradlew.bat`；命令 `./gradlew.bat test` 可执行；项目包名统一为 `io.github.d4vinci.scrapling`；不得在主源码保留 `TODO("not implemented")` 形式的伪完成占位。


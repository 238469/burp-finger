# Finger 插件架构与文件说明

用于记录项目中每个关键文件的职责与作用。

## 1. 文档 (Docs)
- [design.md](design.md): 整体设计方案、逻辑说明及指纹示例。
- [tech-stack.md](tech-stack.md): 技术栈选型及其背后的健壮性考量。
- [implementation-plan.md](implementation-plan.md): 详细的分步骤实施指南。
- [progress.md](progress.md): 开发进度跟踪表。
- [architecture.md](architecture.md): 本文件，维护项目结构说明。

## 2. 核心代码
- [FingerExtension.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/FingerExtension.java): 插件入口类，负责初始化 Montoya API、注册 UI Tab、扫描器以及卸载清理逻辑。
- [HashUtils.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/utils/HashUtils.java): 提供哈希计算工具（MurmurHash3, MD5）。
- [RuleLoader.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/rules/RuleLoader.java): 模块化规则加载器，通过读取 `rules.index` 索引文件，动态加载多个 JSON 指纹规则文件。
- [MatchingEngine.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/engine/MatchingEngine.java): 核心匹配引擎。支持在 HTTP 请求 (Request) 和响应 (Response) 中执行具体的 Header、Body、Hash 及 Status 匹配。实现了多关键词 AND 逻辑。
- [PassiveScanner.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/scanner/PassiveScanner.java): 被动扫描处理器。同时监听代理的请求和响应流量。包含 **URL 级别去重缓存**，并负责触发主动探测逻辑，支持路径规范化处理。
- [ActiveScanner.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/scanner/ActiveScanner.java): 主动探测引擎。支持**递归路径探测**、**探测深度控制**及**并发限流控制**。支持从 `ScannerConfig` 动态更新配置。
- [FingerTabPanel.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/ui/FingerTabPanel.java): 插件主 UI 界面，采用 **Master-Detail (主从表)** 布局。集成扫描结果展示、规则管理及系统配置 (ConfigPanel) 三大标签页。
- [FingerTableModel.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/ui/FingerTableModel.java): 核心表格数据模型。提供 `getFingerprintsAt()` 方法，用于聚合选定 Host 的所有指纹标签供 Nuclei 使用。
- [RuleManagerPanel.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/ui/RuleManagerPanel.java): 规则管理界面。支持按“主动/被动”类型过滤指纹，方便用户维护规则库。
- [ConfigPanel.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/ui/ConfigPanel.java): 系统配置界面。允许用户配置全局过滤规则（状态码、关键字）、线程池大小及 RPS。
- [ScannerConfig.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/model/ScannerConfig.java): 配置模型类，存储所有系统级偏好设置。
- [ConfigPersistence.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/utils/ConfigPersistence.java): 配置持久化工具，使用 Montoya API 的 `extensionData` 存储配置为 JSON 字符串。
- [ResultPersistence.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/utils/ResultPersistence.java): 结果持久化工具，将匹配到的指纹结果实时追加保存至本地 `finger_results.json` 文件。
- [Fingerprint.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/model/Fingerprint.java): 指纹模型类，定义指纹名称、类型及其包含的规则列表。
- [Rule.java](file:///e:/信息/项目/finger/src/main/java/com/finger/burp/model/Rule.java): 规则模型类，定义匹配位置（Header/Body/Path）、匹配内容及哈希校验等。
- [pom.xml](file:///e:/信息/project/finger/pom.xml): 项目构建配置文件，管理依赖（Montoya, Jackson, Guava）及打包逻辑。

## 3. 资源文件
- [rules.index](file:///e:/信息/项目/finger/src/main/resources/fingerprints/rules.index): 指纹库索引文件，记录需要加载的所有分类 JSON 文件。
- `src/main/resources/fingerprints/`: 指纹规则根目录。
    - `frameworks.json`: 存放所有框架类指纹（如 Shiro, Spring Boot）。
    - `cms.json`: 存放所有 CMS 类指纹（如 WordPress, ThinkPHP）。
    - `middleware.json`: 存放所有中间件类指纹（如 Nginx, Tomcat）。

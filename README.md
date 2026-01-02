# Finger - Burp Suite 自动化指纹识别与主动探测插件

Finger 是一款专为 Burp Suite 打造的模块化指纹识别插件，旨在通过被动流量监控与智能主动探测，快速识别目标站点的技术栈（CMS、框架、中间件、API 接口及敏感路径泄露）。

## 🚀 核心特性

- **中英文双语支持**：内置完善的国际化（I18n）机制，支持中英文界面动态切换，满足不同用户需求。
- **双模识别**：支持被动流量匹配与主动路径探测。
- **在线规则更新**：支持从 GitHub/Gitee 或自定义 URL 实时更新指纹库，并具备“智能合并”功能，在更新官方库的同时保留用户自定义规则。
- **正则匹配引擎**：在传统的字符串匹配基础上，新增正则表达式支持，可对 Header 和 Body 进行更精细的特征提取。
- **风险提示功能**：指纹规则支持 `description` 描述字段，在识别出指纹的同时，可直接提示关联的敏感路径或潜在漏洞风险。
- **智能去重机制**：
  - **请求级去重**：同一 URL 在单次会话中仅进行一次匹配，避免重复扫描。
  - **展示级去重**：同一 URL 下的重复指纹会自动压缩显示，保持界面整洁。
- **智能递归探测**：支持多级目录递归扫描，自动向上追溯父级目录，深度可调（0-N）。
- **精准规则引擎**：
  - 支持 `header`、`body`、`status`、`hash` (Favicon MurmurHash3/MD5) 多维度匹配。
  - 支持多关键字 `AND` 逻辑。
  - 支持状态码强校验。
- **Nuclei 集成**：识别指纹后自动生成 Nuclei 扫描标签（Tags），支持右键一键生成 Nuclei 扫描命令。
- **规则管理**：内置 UI 管理界面，支持主动/被动规则过滤、指纹搜索、在线更新、规则导出/导入。
- **高性能与可配置性**：
  - 采用多线程执行、限流保护（Rate Limiting）。
  - **系统配置面板**：支持动态调整线程数、每秒发包数（RPS）。
  - **智能过滤**：支持全局配置状态码黑名单（如 404, 403）及响应体关键字黑名单，减少无效匹配。
- **丰富的指纹库**：内置 2000+ 指纹规则，覆盖 Spring Boot Actuator、Swagger、GraphQL、常见备份文件、.git/.svn 泄露、主流 Web 编辑器、编程语言、前端框架等。

## 🛠️ 安装方法

1.  **编译项目**：
    ```bash
    mvn clean package -DskipTests
    ```
2.  **加载插件**：
    - 打开 Burp Suite -> Extensions -> Installed -> Add。
    - 选择 `target/finger-1.0-SNAPSHOT-jar-with-dependencies.jar`。
3.  **配置**：
    - 插件加载后会自动在 `rules/` 目录下搜索 `fingerprints.json`。
    - 首次使用建议进入 `System Config` 标签页配置合适的线程数和过滤规则。

## 📖 使用指南

### 1. 被动识别
插件会自动监听 Proxy 流量，实时识别经过的所有请求和响应中的技术指纹。识别结果展示在 `Finger` 标签页的表格中。

### 2. 主动探测
- **开启/关闭**：在 `Finger` 标签页勾选 `Enable Active Scan`。
- **探测深度**：通过 `Scan Depth` 调整递归深度。
  - `0`: 仅探测根目录。
  - `1`: 探测根目录及当前请求的一级父目录。
- **触发机制**：当正常浏览网页时，插件会根据当前 URL 自动触发对相关敏感路径（如 `/actuator/env`, `.git/HEAD` 等）的探测。

### 3. 系统配置 (System Config)
- **并发控制**：动态设置 `Thread Count`（建议 10-50）和 `RPS`（建议 10-100）。
- **全局过滤**：
  - `Exclude Status Codes`: 设置不参与匹配的状态码列表（逗号或换行分隔）。
  - `Exclude Body Keywords`: 设置包含特定内容时跳过匹配的关键字列表（换行分隔）。

### 4. Nuclei 集成
- 在识别结果表格中点击右键，选择 `Copy Nuclei Scan Command`。
- 插件会自动根据当前识别到的所有指纹名称，格式化为 Nuclei 的 `tags`（如 `spring-boot,nginx`），生成完整的命令行。

### 4. 规则编写
详细规则规格请参考：[docs/rules-spec.md](docs/rules-spec.md)

示例规则：
```json
{
  "name": "Spring Boot Actuator",
  "type": "Leak",
  "rules": [
    {
      "location": "body",
      "path": "/actuator/env",
      "match": ["activeProfiles", "propertySources"],
      "status": 200
    }
  ]
}
```

## 📂 项目结构

- `src/main/java/com/finger/burp/engine`: 匹配引擎核心逻辑。
- `src/main/java/com/finger/burp/scanner`: 主动/被动扫描器实现。
- `src/main/java/com/finger/burp/ui`: 插件 UI 界面（表格、规则管理器）。
- `src/main/resources/rules`: 默认指纹库。

## ⚖️ 许可证

本项目遵循 MIT 许可证。仅供安全研究与授权测试使用，严禁用于非法用途。

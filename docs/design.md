# Burp 指纹识别插件设计文档

## 1. 项目概述
本项目旨在开发一款基于 Burp Suite 的 Web 指纹识别插件。该插件能够通过被动监听和主动探测两种方式，自动识别目标系统所使用的技术栈（如中间件、框架、CMS、编程语言等），为渗透测试人员提供高效的信息收集手段。

## 2. 核心功能
- **被动识别 (Passive Recognition)**: 实时分析流经 Burp Suite 的 HTTP 请求与响应报文，不产生额外流量。
- **主动识别 (Active Recognition)**: 针对特定目标发送定制化的探测请求（如获取特定路径下的文件、分析 Favicon 哈希等）。
- **指纹库管理**: 支持自定义指纹规则，支持多种匹配方式（Header、Body、MD5/MMH3 Hash、Regex、Path）。
- **结果展示**: 在 Burp 界面中直观展示识别到的技术指纹及其版本信息。

## 3. 系统架构设计

### 3.1 模块划分
- **Core Engine (核心引擎)**: 负责指纹匹配算法的实现。
- **Rule Engine (规则引擎)**: 解析并加载指纹库（建议使用 JSON ）。
- **Passive Scanner (被动扫描模块)**: 实现 `IHttpListener` 或 `IScannerCheck` 接口，监听流量。
- **Active Scanner (主动扫描模块)**: 实现 `IScannerCheck` 接口或自定义触发逻辑，执行主动探测任务。
- **UI Dashboard (用户界面)**: 基于 Java Swing/JavaFX 构建，用于展示识别结果和配置插件。

### 3.2 识别逻辑
1. **Header 匹配**: 检查 `Server`, `X-Powered-By`, `Set-Cookie` 等关键字段。
2. **Body 匹配**: 在 HTML 源码中搜索特定的关键字、路径或注释。
3. **哈希匹配**: 计算特殊文件（如 `favicon.ico`）的 MurmurHash3 或 MD5 值。
4. **主动路径探测 (Active Path Probing)**:
    - **特定文件检测**: 探测如 `/phpinfo.php`, `/.git/config`, `/WEB-INF/web.xml` 等路径。
    - **状态码校验**: 根据返回的 HTTP 状态码（如 200, 403）结合响应内容进行判断。
    - **递归探测**: 针对发现的目录结构，支持向上递归探测父级目录，深度可配置（0-N）。
    - **Nuclei 联动**: 识别指纹后，支持自动提取指纹标签并生成 Nuclei 扫描命令，实现从资产识别到漏洞扫描的闭环。
    - **并发控制**: 为了避免对目标造成过大压力，主动探测支持基于 Guava RateLimiter 的速率限制，并可在系统配置页动态调整并发线程数。
    - **智能响应过滤**: 支持全局黑名单配置，自动跳过特定状态码（如 404）或包含特定关键字（如错误页提示）的响应，提升识别精度。
    - **路径过滤策略**: 在递归探测中，自动跳过包含 `.` 的路径（如文件路径），仅对潜在的目录结构进行深度探测。

### 3.3 规则管理规范 (Modularity)
为了保证系统的可维护性与扩展性，必须遵循以下规则：
- **禁止单体巨文件 (No Monolith)**: 严禁将所有指纹规则写入单个 JSON 文件。
- **模块化目录结构**: 指纹应按类别（如 `cms/`, `frameworks/`, `middleware/`）或组件拆分为多个独立的 JSON 文件。
- **动态加载**: 规则引擎应扫描指定目录下的所有 `.json` 文件并进行合并加载。
- **配置持久化**: 插件系统配置（线程、过滤规则等）支持持久化保存，确保跨会话可用。
- **解耦设计**: 核心引擎只负责解析与匹配逻辑，不硬编码任何具体指纹信息。

## 4. 技术栈 (推荐方案)
- **编程语言**: Java 17 (LTS)
- **构建工具**: Maven 3.8+
- **API**: Burp Suite Montoya API
- **关键库**: Jackson (JSON解析), Google Guava (并发与速率限制)
- **数据格式**: JSON (用于指纹库存储)

## 5. 指纹库示例 (JSON)
```json
{
  "fingerprints": [
    {
      "name": "Shiro",
      "type": "Framework",
      "rules": [
        {
          "location": "header",
          "field": "Set-Cookie",
          "match": "rememberMe="
        }
      ]
    },
    {
      "name": "Spring Boot",
      "type": "Framework",
      "rules": [
        {
          "location": "body",
          "match": "Whitelabel Error Page"
        }
      ]
    },
    {
      "name": "ThinkPHP",
      "type": "Framework",
      "rules": [
        {
          "location": "path",
          "path": "/public/static/index/js/index.js",
          "status": 200,
          "match": "ThinkPHP"
        },
        {
          "location": "path",
          "path": "/favicon.ico",
          "hash": "mmh3:-123456789"
        }
      ]
    }
  ]
}
```

## 6. 后续演进
- 支持 Wappalyzer 指纹库转换。
- 增加导出功能（CSV/JSON）。
- 集成到 Burp 的 Dashboard 仪表盘。

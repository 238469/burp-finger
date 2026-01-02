# 指纹规则字段规格说明 (Rules Specification)

本文档定义了 Finger 插件指纹规则的 JSON 结构及其后端处理逻辑。

## 1. 整体结构 (Fingerprint)

指纹字典是一个包含多个 `Fingerprint` 对象的 JSON 数组。

| 字段 | 类型 | 说明 | 逻辑关系 |
| :--- | :--- | :--- | :--- |
| `name` | String | 指纹名称（如 "Spring Boot", "Nginx"）。 | - |
| `type` | String | 指纹分类（如 "Framework", "CMS", "Middleware"）。 | - |
| `rules` | List<Rule> | 匹配规则列表。 | **OR (或)**：只要有一条规则匹配，该指纹即命中。 |

---

## 2. 匹配规则 (Rule)

每条规则定义了具体的检测细节。

| 字段 | 类型 | 说明 | 逻辑关系 |
| :--- | :--- | :--- | :--- |
| `location` | String | **必填**。匹配位置，可选值：`header`, `body`, `hash`, `status`。 | - |
| `match` | List<String> | 匹配关键字。支持单个字符串或字符串数组。 | **AND (与)**：列表中的所有关键字必须同时出现在指定位置。 |
| `field` | String | 可选。当 `location` 为 `header` 时，指定匹配的 HTTP 头字段名。 | - |
| `path` | String | 可选。探测路径，用于主动/被动触发特定 URL 的检测。 | - |
| `status` | Integer | 可选。匹配特定的 HTTP 状态码。 | - |
| `hash` | String | 可选。当 `location` 为 `hash` 时，匹配响应体的 Hash 值。 | - |

---

## 3. 后端处理逻辑 (Backend Logic)

### 3.1 关键字匹配逻辑 (match)
- **多关键字 AND**：后端在处理 `match` 字段时，会遍历列表中的每一个关键字。如果其中任何一个关键字未在指定位置找到，该条规则即判定为不匹配。
- **单/多值兼容**：JSON 中可以使用 `"match": "keyword"` 或 `"match": ["k1", "k2"]`。

### 3.2 位置匹配逻辑 (location)
- **header**:
    - 如果指定了 `field`（如 `"field": "Set-Cookie"`）：仅在该 Header 的值中查找关键字。
    - 如果未指定 `field`：在所有 Header（包括名称和值）中查找关键字。
- **body**: 在整个响应体文本（String）中查找关键字。
- **hash**:
    - 后端会同时计算响应体的 **MurmurHash3 (Favicon Hash)** 和 **MD5**。
    - 将计算结果与 `hash` 字段进行对比，匹配其一即可。
- **status**: 直接对比响应的 HTTP 状态码。

### 3.3 指纹命中逻辑
- 一个指纹包含多条规则。只要其中**任意一条**规则满足条件，系统就会在扫描结果中展示该指纹。
- **去重逻辑**：同一个 Host/URL 下相同的指纹名称不会重复展示。

### 3.4 主动与被动分类逻辑
- **主动规则 (Active Rule)**：定义了 `path` 字段的规则。系统会根据该路径发起主动请求进行探测。
- **被动规则 (Passive Rule)**：未定义 `path` 字段的规则（如仅包含 `location: body` 或 `location: header`）。系统仅对流量中的现有请求/响应进行匹配。
- **指纹分类**：在 UI 筛选中，只要指纹中包含至少一条**主动规则**，该指纹即被归类为“包含主动探测”。

### 3.5 探测深度与递归逻辑
- **递归探测**：当用户请求 `/aaa/bbb/` 时，插件会根据设置的**探测深度 (Scan Depth)**，递归向上探测父级目录（如 `/aaa/` 和 `/`）。
- **深度定义**：
    - `0`: 仅探测根目录 `/`。
    - `1`: 探测根目录及一级子目录（如 `/aaa/`）。
    - `N`: 依此类推。
- **文件处理**：如果触发路径是具体文件（如 `/index.php`），插件会智能识别并从其所属目录开始向上递归。
- **路径过滤**：在递归和主动探测过程中，插件会自动过滤掉路径中包含 `.` 的项（通常被视为具体文件而非目录），以减少不必要的请求。

### 3.6 状态码校验与全局过滤

- **规则级校验 (status)**：
    - **强校验**：如果规则中定义了 `status` 字段，后端在匹配时会将其作为**首要条件**。
    - **逻辑**：只有当响应状态码与 `status` 完全一致时，才会继续进行 `body` 或 `header` 的内容匹配。这在探测备份文件（200 OK）或特定接口（如 403 Forbidden 提示）时非常有用。
- **全局级过滤 (Global Filter)**：
    - **状态码黑名单**：在系统配置中设置的 `Exclude Status Codes`。如果响应状态码命中该列表，将跳过后续所有指纹匹配。
    - **响应体黑名单**：在系统配置中设置的 `Exclude Body Keywords`。如果响应体包含列表中的任意关键字，将跳过该响应的指纹识别。
    - **空内容过滤**：系统会自动跳过 `Content-Length: 0` 或 Body 为空的响应，以过滤无效的探测结果。

---

## 4. 示例 JSON

```json
[
  {
    "name": "Spring Boot",
    "type": "Framework",
    "rules": [
      {
        "location": "body",
        "match": ["Whitelabel Error Page", "application/json"],
        "path": "/error"
      },
      {
        "location": "hash",
        "hash": "116323821",
        "path": "/favicon.ico"
      }
    ]
  },
  {
    "name": "Shiro",
    "type": "Framework",
    "rules": [
      {
        "location": "header",
        "field": "Set-Cookie",
        "match": "rememberMe=deleteMe"
      }
    ]
  }
]
```

---

## 5. 序列化规范 (Serialization)
- **忽略空值**：生成 JSON 时，所有值为 `null` 的字段将被忽略，以保持文件简洁。
- **容错处理**：后端加载时会自动忽略未知字段，确保向前兼容性。

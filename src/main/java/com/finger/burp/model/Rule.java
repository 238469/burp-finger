package com.finger.burp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Collections;

/**
 * 表示单条匹配规则。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Rule {
    /**
     * 匹配位置。可选值：
     * - "header": 匹配 HTTP 响应头。
     * - "body": 匹配 HTTP 响应体。
     * - "path": 匹配特定路径（主动探测）。
     * - "hash": 匹配响应体（通常是图标）的 Hash 值。
     */
    private String location;

    /**
     * 当 location 为 "header" 时，指定要匹配的具体头部字段（如 "Set-Cookie"）。
     * 如果为空，则匹配整个 Header 区域。
     */
    private String field;

    /**
     * 当需要对特定路径进行探测时使用（主动或被动）。
     * 例如："/favicon.ico" 或 "/admin/login.php"。
     */
    private String path;
    
    /**
     * 要匹配的关键字列表。
     * 如果包含多个关键字，则必须全部匹配（AND 逻辑）。
     * 支持单个字符串自动转换为列表。
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> match;
    
    /**
     * 匹配特定的 HTTP 状态码。例如：200, 403, 500。
     */
    private Integer status;

    /**
     * 当 location 为 "hash" 时，匹配响应体的 Hash 值。
     * 目前支持 MurmurHash3 (用于 favicon) 和 MD5。
     */
    private String hash;

    /**
     * 规则描述。可以用来描述指纹的具体特征或潜在漏洞。
     */
    private String description;

    // Getters and Setters
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public List<String> getMatch() { return match; }
    public void setMatch(List<String> match) { this.match = match; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

package com.finger.burp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 表示一个指纹定义，包含多个匹配规则。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Fingerprint {
    /**
     * 指纹名称。例如："Spring Boot", "Nginx"。
     */
    private String name;

    /**
     * 指纹类型。可选值：
     * - "Framework": Web 框架
     * - "CMS": 内容管理系统
     * - "Middleware": 中间件
     * - "Operating System": 操作系统
     * - "Service": 其他通用服务
     */
    private String type;

    /**
     * 该指纹包含的规则列表。
     * 规则之间是 OR 逻辑（只要有一条 Rule 匹配，该指纹即命中）。
     * 每条 Rule 内部的 match 列表是 AND 逻辑。
     */
    private List<Rule> rules;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }
}

package com.finger.burp.utils;

import java.util.HashMap;
import java.util.Map;

public class I18n {
    public enum Language {
        CHINESE("中文"),
        ENGLISH("English");

        private final String displayName;

        Language(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static Language currentLanguage = Language.CHINESE;
    private static final Map<String, Map<Language, String>> messages = new HashMap<>();

    static {
        // Tab Names
        add("tab_finger", "指纹识别", "Finger");
        add("tab_rules", "规则管理", "Rules");
        add("tab_config", "系统配置", "System Config");

        // Config Panel
        add("config_exclude_status", "排除状态码 (每行一个或逗号分隔):", "Exclude Status Codes (one per line or comma separated):");
        add("config_exclude_body", "排除 Body 关键字 (每行一个):", "Exclude Body Keywords (one per line):");
        add("config_thread_count", "线程数:", "Thread Count:");
        add("config_rps", "每秒发包数 (RPS):", "Requests Per Second (RPS):");
        add("config_save", "保存配置", "Save Configuration");
        add("config_save_success", "配置保存成功！", "Configuration saved successfully!");
        add("config_save_error", "保存配置出错: ", "Error saving configuration: ");
        add("config_invalid_status", "状态码格式错误，请输入数字。", "Invalid status code format. Please use numbers only.");
        add("config_language", "语言 (Language):", "Language:");

        // Finger Panel
        add("finger_enable_active", "启用主动扫描", "Enable Active Scan");
        add("finger_scan_depth", "扫描深度:", "Scan Depth:");
        add("finger_clear", "清空结果", "Clear Results");
        add("finger_search_placeholder", "搜索域名或指纹...", "Search host or fingerprint...");
        
        // Table Columns
        add("column_last_time", "最后发现时间", "Last Seen");
        add("column_host", "域名/地址", "Host/URL");
        add("column_fingerprint", "指纹标签", "Fingerprints");
        add("column_types", "分类", "Categories");
        add("column_methods", "匹配方式", "Methods");
        add("column_url", "URL", "URL");
        add("column_name", "名称", "Name");
        add("column_field", "匹配位置", "Matched Field");
        add("column_timestamp", "时间戳", "Timestamp");

        // Context Menu
        add("menu_copy_nuclei", "复制 Nuclei 扫描命令", "Copy Nuclei Scan Command");
        add("menu_open_browser", "在浏览器中打开", "Open in Browser");

        // Rule Manager
        add("rule_type_all", "全部规则", "All Rules");
        add("rule_type_active", "主动规则", "Active Rules");
        add("rule_type_passive", "被动规则", "Passive Rules");
        add("rule_search", "搜索指纹...", "Search fingerprints...");
        add("rule_count", "规则数量", "Rules Count");
        add("rule_search_label", "搜索: ", "Search: ");
        add("rule_type_label", "  类型: ", "  Type: ");
        add("rule_file_label", "规则文件: ", "Rule File: ");
        add("rule_import", "导入 JSON", "Import JSON");
        add("rule_export", "导出 JSON", "Export JSON");
        add("rule_add", "添加规则", "Add Rule");
        add("rule_help", "字段帮助", "Field Help");
        add("rule_edit", "编辑", "Edit");
        add("rule_delete", "删除", "Delete");
        add("rule_edit_title", "编辑规则 (JSON 格式)", "Edit Rule (JSON Format)");
        add("rule_update", "在线更新", "Online Update");
        add("rule_updating", "正在更新规则库...", "Updating rules...");
        add("rule_update_success", "规则库更新成功！共加载 %d 条规则。", "Rules updated! %d rules loaded.");
        add("rule_update_failed", "更新失败: ", "Update failed: ");
        add("rule_update_confirm", "确定要从 GitHub 更新规则库吗？\n这将覆盖本地规则文件。", "Update rules from GitHub? This will overwrite local rules.");

        // Help Dialog
        add("help_title", "=== 指纹规则字段规格说明 ===", "=== Fingerprint Rule Specification ===");
        add("help_desc", "本文档定义了指纹规则的 JSON 结构及其处理逻辑。", "This document defines the JSON structure and logic for rules.");
        add("help_section_1", "1. 基础信息 (Fingerprint)", "1. Basic Information (Fingerprint)");
        add("help_field_name", "- name: 指纹名称 (唯一)", "- name: Fingerprint name (unique)");
        add("help_field_type", "- type: 指纹类型 (如 CMS, Framework)", "- type: Fingerprint type (e.g., CMS)");
        add("help_section_2", "2. 匹配规则 (Rule)", "2. Matching Rule (Rule)");
        add("help_field_location", "- location: 匹配位置 (header, body, hash, status)", "- location: Position (header, body, hash, status)");
        add("help_field_match", "- match: 关键字列表 (AND 逻辑)", "- match: Keywords (AND logic)");
        add("help_field_path", "- path: 探测路径 (可选)", "- path: Probe path (optional)");
        add("help_field_status", "- status: 状态码 (可选)", "- status: Status code (optional)");
        add("help_field_field", "- field: Header 字段名 (可选)", "- field: Header field name (optional)");

        // Common
        add("common_error", "错误", "Error");
        add("common_confirm", "确认", "Confirm");
        add("common_info", "提示", "Information");
        add("common_total", "总数: ", "Total: ");
        add("config_update_url", "规则更新地址: ", "Update URL: ");
        add("config_update_url_hint", "提示: 若 GitHub 访问困难，可尝试使用镜像地址或代理前缀", "Hint: Try mirror or proxy if GitHub is inaccessible");
    }

    private static void add(String key, String cn, String en) {
        Map<Language, String> langMap = new HashMap<>();
        langMap.put(Language.CHINESE, cn);
        langMap.put(Language.ENGLISH, en);
        messages.put(key, langMap);
    }

    public static void setLanguage(Language language) {
        currentLanguage = language;
    }

    public static Language getLanguage() {
        return currentLanguage;
    }

    public static String get(String key) {
        Map<Language, String> langMap = messages.get(key);
        if (langMap != null) {
            return langMap.getOrDefault(currentLanguage, key);
        }
        return key;
    }
}

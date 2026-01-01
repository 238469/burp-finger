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

        // Common
        add("common_error", "错误", "Error");
        add("common_confirm", "确认", "Confirm");
        add("common_info", "提示", "Information");
        add("common_total", "总数: ", "Total: ");
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

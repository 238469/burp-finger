package com.finger.burp.ui;

import burp.api.montoya.MontoyaApi;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.rules.RuleLoader;
import com.finger.burp.utils.I18n;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RuleManagerPanel extends JPanel {
    private final MontoyaApi api;
    private final RuleLoader ruleLoader;
    private final List<Fingerprint> allFingerprints;
    private List<Fingerprint> filteredFingerprints;
    private final RuleTableModel tableModel;
    private final JTable table;
    private final JTextField searchField;
    private final JComboBox<String> scanTypeFilter;
    private final ObjectMapper mapper;
    
    private final JLabel searchLabel;
    private final JLabel typeLabel;
    private final JLabel pathLabel;
    private final JButton importButton;
    private final JButton exportButton;
    private final JButton addButton;
    private final JButton helpButton;
    private final JMenuItem editItem;
    private final JMenuItem deleteItem;

    public RuleManagerPanel(MontoyaApi api, RuleLoader ruleLoader, List<Fingerprint> fingerprints) {
        this.api = api;
        this.ruleLoader = ruleLoader;
        this.allFingerprints = fingerprints;
        this.filteredFingerprints = new ArrayList<>(fingerprints);
        this.tableModel = new RuleTableModel();
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.setLayout(new BorderLayout());

        // 1. 顶部搜索和控制栏
        JPanel topPanel = new JPanel(new BorderLayout());
        
        // 1.1 搜索和路径显示
        JPanel searchAndPathPanel = new JPanel(new GridLayout(2, 1));
        
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchLabel = new JLabel(I18n.get("rule_search_label"));
        searchPanel.add(searchLabel);
        searchField = new JTextField(30);
        searchField.setToolTipText(I18n.get("rule_search"));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });
        searchPanel.add(searchField);
        
        typeLabel = new JLabel(I18n.get("rule_type_label"));
        searchPanel.add(typeLabel);
        scanTypeFilter = new JComboBox<>();
        updateScanTypeFilter();
        scanTypeFilter.addActionListener(e -> filter());
        searchPanel.add(scanTypeFilter);
        
        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pathLabel = new JLabel(I18n.get("rule_file_label") + ruleLoader.getExternalRulePath());
        pathLabel.setForeground(Color.GRAY);
        pathPanel.add(pathLabel);
        
        searchAndPathPanel.add(searchPanel);
        searchAndPathPanel.add(pathPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importButton = new JButton(I18n.get("rule_import"));
        exportButton = new JButton(I18n.get("rule_export"));
        addButton = new JButton(I18n.get("rule_add"));
        helpButton = new JButton(I18n.get("rule_help"));

        importButton.addActionListener(e -> importRules());
        exportButton.addActionListener(e -> exportRules());
        addButton.addActionListener(e -> addRule());
        helpButton.addActionListener(e -> showHelpDialog());

        buttonPanel.add(helpButton);
        buttonPanel.add(new JSeparator(JSeparator.VERTICAL));
        buttonPanel.add(addButton);
        buttonPanel.add(importButton);
        buttonPanel.add(exportButton);

        topPanel.add(searchAndPathPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // 2. 表格
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        JScrollPane scrollPane = new JScrollPane(table);

        // 3. 右键菜单
        JPopupMenu popupMenu = new JPopupMenu();
        editItem = new JMenuItem(I18n.get("rule_edit"));
        deleteItem = new JMenuItem(I18n.get("rule_delete"));
        
        editItem.addActionListener(e -> editRule());
        deleteItem.addActionListener(e -> deleteRule());
        
        popupMenu.add(editItem);
        popupMenu.add(deleteItem);
        table.setComponentPopupMenu(popupMenu);

        this.add(topPanel, BorderLayout.NORTH);
        this.add(scrollPane, BorderLayout.CENTER);
    }

    private void showHelpDialog() {
        // 使用简单的 JTextArea 替代 JEditorPane 以解决渲染重影和乱码问题
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setBackground(UIManager.getColor("Panel.background"));
        textArea.setForeground(UIManager.getColor("Label.foreground"));
        
        StringBuilder helpText = new StringBuilder();
        if (I18n.getLanguage() == I18n.Language.CHINESE) {
            helpText.append("=== 指纹规则字段规格说明 ===\n\n");
            helpText.append("本文档定义了 Finger 插件指纹规则的 JSON 结构及其后端处理逻辑。\n\n");
            
            helpText.append("1. 整体结构 (Fingerprint)\n");
            helpText.append("----------------------------\n");
            helpText.append("- name:  指纹名称 (如 \"Spring Boot\", \"Nginx\")。\n");
            helpText.append("- type:  指纹分类 (如 \"Framework\", \"CMS\", \"Middleware\")。\n");
            helpText.append("- rules: 匹配规则列表。逻辑关系为 [OR (或)]：只要有一条规则匹配，该指纹即命中。\n\n");
            
            helpText.append("2. 匹配规则 (Rule)\n");
            helpText.append("----------------------------\n");
            helpText.append("- location: [必填] 匹配位置。可选值: header, body, hash, status。\n");
            helpText.append("- match:    匹配关键字。支持单字符串或数组。逻辑关系为 [AND (与)]：所有关键字必须同时出现。\n");
            helpText.append("- field:    [可选] 当 location 为 header 时，指定 HTTP 头字段名 (如 \"Set-Cookie\")。\n");
            helpText.append("- path:     [可选] 探测路径。用于主动/被动触发特定 URL 的检测 (如 \"/favicon.ico\")。\n");
            helpText.append("- status:   [可选] 匹配特定的 HTTP 状态码 (如 200, 403)。\n");
            helpText.append("- hash:     [可选] 当 location 为 hash 时，匹配响应体的 MurmurHash3 或 MD5 值。\n\n");
            
            helpText.append("3. 后端逻辑详解\n");
            helpText.append("----------------------------\n");
            helpText.append("- location=body: 在 HTTP 响应体中搜索 match 关键字。\n");
            helpText.append("- location=header: 在 HTTP 响应头中搜索。如果指定了 field，则仅在该字段值中搜索；否则在整个响应头文本中搜索。\n");
            helpText.append("- location=status: 检查响应状态码是否等于指定的 status 值。\n");
            helpText.append("- location=hash: 计算响应体的 MurmurHash3 或 MD5 值并进行比对。计算 favicon.ico 的 hash 时常用于指纹识别。\n");
            helpText.append("- location=path: 当该规则包含 path 字段时，被动扫描在发现主机时会自动触发该路径的探测请求。\n");
        } else {
            helpText.append("=== Fingerprint Rule Specification ===\n\n");
            helpText.append("This document defines the JSON structure and backend logic for Finger plugin rules.\n\n");
            
            helpText.append("1. Overall Structure (Fingerprint)\n");
            helpText.append("----------------------------\n");
            helpText.append("- name:  Fingerprint name (e.g., \"Spring Boot\", \"Nginx\").\n");
            helpText.append("- type:  Category (e.g., \"Framework\", \"CMS\", \"Middleware\").\n");
            helpText.append("- rules: List of matching rules. Relationship is [OR]: any rule match results in a hit.\n\n");
            
            helpText.append("2. Matching Rule (Rule)\n");
            helpText.append("----------------------------\n");
            helpText.append("- location: [Required] Match position. Options: header, body, hash, status.\n");
            helpText.append("- match:    Keywords. Supports string or array. Relationship is [AND]: all keywords must match.\n");
            helpText.append("- field:    [Optional] When location=header, specifies header field (e.g., \"Set-Cookie\").\n");
            helpText.append("- path:     [Optional] Probe path for active/passive triggering (e.g., \"/favicon.ico\").\n");
            helpText.append("- status:   [Optional] Matches specific HTTP status code (e.g., 200, 403).\n");
            helpText.append("- hash:     [Optional] When location=hash, matches MurmurHash3 or MD5 of response body.\n\n");
            
            helpText.append("3. Backend Logic Details\n");
            helpText.append("----------------------------\n");
            helpText.append("- location=body: Searches for match keywords in the HTTP response body.\n");
            helpText.append("- location=header: Searches in HTTP headers. If field is specified, searches only in that field; otherwise searches entire header text.\n");
            helpText.append("- location=status: Checks if status code equals specified status.\n");
            helpText.append("- location=hash: Calculates and compares MurmurHash3 or MD5 of response body. Often used for favicon.ico identification.\n");
            helpText.append("- location=path: Passive scan will automatically trigger probe requests to this path when a host is discovered.\n");
        }
        
        textArea.setText(helpText.toString());
        textArea.setCaretPosition(0);
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 500));
        String title = I18n.getLanguage() == I18n.Language.CHINESE ? "规则字段规格说明" : "Rule Field Specification";
        JOptionPane.showMessageDialog(this, scrollPane, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateScanTypeFilter() {
        int selectedIndex = scanTypeFilter.getSelectedIndex();
        scanTypeFilter.removeAllItems();
        scanTypeFilter.addItem(I18n.get("rule_type_all"));
        scanTypeFilter.addItem(I18n.get("rule_type_active"));
        scanTypeFilter.addItem(I18n.get("rule_type_passive"));
        if (selectedIndex != -1) {
            scanTypeFilter.setSelectedIndex(selectedIndex);
        } else {
            scanTypeFilter.setSelectedIndex(0);
        }
    }

    public void refreshI18n() {
        searchLabel.setText(I18n.get("rule_search_label"));
        searchField.setToolTipText(I18n.get("rule_search"));
        typeLabel.setText(I18n.get("rule_type_label"));
        updateScanTypeFilter();
        pathLabel.setText(I18n.get("rule_file_label") + ruleLoader.getExternalRulePath());
        importButton.setText(I18n.get("rule_import"));
        exportButton.setText(I18n.get("rule_export"));
        addButton.setText(I18n.get("rule_add"));
        helpButton.setText(I18n.get("rule_help"));
        editItem.setText(I18n.get("rule_edit"));
        deleteItem.setText(I18n.get("rule_delete"));
        
        tableModel.fireTableStructureChanged();
    }

    private void filter() {
        String query = searchField.getText().toLowerCase();
        int typeIndex = scanTypeFilter.getSelectedIndex();
        
        filteredFingerprints = allFingerprints.stream()
                .filter(f -> {
                    // 1. 关键词搜索
                    boolean matchesQuery = f.getName().toLowerCase().contains(query) || f.getType().toLowerCase().contains(query);
                    if (!matchesQuery) return false;
                    
                    // 2. 主动/被动筛选 (0: All, 1: Active, 2: Passive)
                    if (typeIndex <= 0) return true;
                    
                    boolean hasActiveRules = f.getRules() != null && f.getRules().stream()
                            .anyMatch(r -> r.getPath() != null && !r.getPath().isEmpty());
                    
                    if (typeIndex == 1) return hasActiveRules; // Active
                    if (typeIndex == 2) return !hasActiveRules; // Passive
                    
                    return true;
                })
                .collect(Collectors.toList());
        tableModel.fireTableDataChanged();
    }

    private void importRules() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                List<Fingerprint> imported = mapper.readValue(file, mapper.getTypeFactory().constructCollectionType(List.class, Fingerprint.class));
                allFingerprints.addAll(imported);
                filter();
                ruleLoader.saveRules(allFingerprints); // 自动保存
                api.logging().logToOutput("Imported " + imported.size() + " rules from " + file.getName());
            } catch (Exception e) {
                api.logging().logToError("Failed to import rules: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Import failed: " + e.getMessage(), I18n.get("common_error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportRules() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            try {
                mapper.writeValue(file, filteredFingerprints);
                api.logging().logToOutput("Exported " + filteredFingerprints.size() + " rules to " + file.getName());
                String successMsg = I18n.getLanguage() == I18n.Language.CHINESE ? "成功导出到 " + file.getName() : "Exported successfully to " + file.getName();
                JOptionPane.showMessageDialog(this, successMsg);
            } catch (Exception e) {
                api.logging().logToError("Failed to export rules: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), I18n.get("common_error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void addRule() {
        // 简化版：弹出一个文本域让用户输入 JSON
        showEditDialog(new Fingerprint(), true);
    }

    private void editRule() {
        int row = table.getSelectedRow();
        if (row != -1) {
            int modelRow = table.convertRowIndexToModel(row);
            showEditDialog(filteredFingerprints.get(modelRow), false);
        }
    }

    private void deleteRule() {
        int row = table.getSelectedRow();
        if (row != -1) {
            int modelRow = table.convertRowIndexToModel(row);
            Fingerprint fp = filteredFingerprints.get(modelRow);
            String message = I18n.getLanguage() == I18n.Language.CHINESE ? "确定要删除 " + fp.getName() + " 吗？" : "Delete " + fp.getName() + "?";
            if (JOptionPane.showConfirmDialog(this, message, I18n.get("common_confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                allFingerprints.remove(fp);
                filter();
                ruleLoader.saveRules(allFingerprints); // 自动保存
            }
        }
    }

    private void showEditDialog(Fingerprint fp, boolean isNew) {
        JTextArea textArea = new JTextArea(20, 50);
        try {
            textArea.setText(mapper.writeValueAsString(fp));
        } catch (Exception e) {
            textArea.setText("{}");
        }

        String title = I18n.get("rule_edit_title");
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(textArea), title, JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                Fingerprint updated = mapper.readValue(textArea.getText(), Fingerprint.class);
                if (isNew) {
                    allFingerprints.add(updated);
                } else {
                    int index = allFingerprints.indexOf(fp);
                    if (index != -1) {
                        allFingerprints.set(index, updated);
                    }
                }
                filter();
                ruleLoader.saveRules(allFingerprints); // 自动保存
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Invalid JSON: " + e.getMessage(), I18n.get("common_error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class RuleTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() { return filteredFingerprints.size(); }
        @Override
        public int getColumnCount() { return 3; }
        
        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0: return I18n.get("column_name");
                case 1: return I18n.get("column_types");
                case 2: return I18n.get("rule_count");
                default: return "";
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Fingerprint fp = filteredFingerprints.get(rowIndex);
            switch (columnIndex) {
                case 0: return fp.getName();
                case 1: return fp.getType();
                case 2: return fp.getRules() != null ? fp.getRules().size() : 0;
                default: return null;
            }
        }
    }
}

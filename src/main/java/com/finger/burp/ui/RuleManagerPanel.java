package com.finger.burp.ui;

import burp.api.montoya.MontoyaApi;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.ScannerConfig;
import com.finger.burp.rules.RuleLoader;
import com.finger.burp.utils.ConfigPersistence;
import com.finger.burp.utils.I18n;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final JButton updateButton;
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
        updateButton = new JButton(I18n.get("rule_update"));
        helpButton = new JButton(I18n.get("rule_help"));

        importButton.addActionListener(e -> importRules());
        exportButton.addActionListener(e -> exportRules());
        addButton.addActionListener(e -> addRule());
        updateButton.addActionListener(e -> updateRulesOnline());
        helpButton.addActionListener(e -> showHelpDialog());

        buttonPanel.add(helpButton);
        buttonPanel.add(new JSeparator(JSeparator.VERTICAL));
        buttonPanel.add(updateButton);
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
        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        
        String helpContent;
        if (I18n.getLanguage() == I18n.Language.CHINESE) {
            helpContent = "<html><body style='font-family: sans-serif; padding: 10px;'>" +
                "<h2>Finger 指纹规则编写指南</h2>" +
                "<p>指纹规则采用 JSON 格式，结构分为两层：<b>指纹定义 (Fingerprint)</b> 和 <b>匹配规则 (Rule)</b>。</p>" +
                "<h3>1. 指纹定义 (Fingerprint)</h3>" +
                "<ul>" +
                "<li><b>name</b>: 指纹的名称，如 'Spring Boot'。必须唯一。</li>" +
                "<li><b>type</b>: 指纹分类，如 'Framework', 'CMS', 'MiddleWare'。</li>" +
                "<li><b>rules</b>: 包含一个或多个匹配规则的列表。</li>" +
                "</ul>" +
                "<h3>2. 匹配规则 (Rule)</h3>" +
                "<ul>" +
                "<li><b>location</b>: 匹配位置。支持：<br/>" +
                "&nbsp;&nbsp;- <code>body</code>: 匹配响应体内容。<br/>" +
                "&nbsp;&nbsp;- <code>header</code>: 匹配响应头。<br/>" +
                "&nbsp;&nbsp;- <code>hash</code>: 匹配 favicon 的 hash 值（使用 MurmurHash3）。<br/>" +
                "&nbsp;&nbsp;- <code>status</code>: 匹配 HTTP 状态码。</li>" +
                "<li><b>match</b>: 匹配关键词列表。多个关键词之间是 <b>AND</b> 逻辑（必须全部匹配）。</li>" +
                "<li><b>path</b>: 主动探测路径。仅对主动规则有效，例如 <code>/favicon.ico</code>。</li>" +
                "<li><b>status</b>: 预期的状态码，如 <code>200</code>。</li>" +
                "<li><b>field</b>: 当 location 为 header 时，指定匹配的字段，如 <code>Server</code>。</li>" +
                "<li><b>is_active</b>: 是否为主动扫描规则 (true/false)。</li>" +
                "</ul>" +
                "<h3>3. 规则示例</h3>" +
                "<pre style='background: #f4f4f4; padding: 10px; border: 1px solid #ddd;'>" +
                "{\n" +
                "  \"name\": \"Spring Boot\",\n" +
                "  \"type\": \"Framework\",\n" +
                "  \"rules\": [\n" +
                "    {\n" +
                "      \"location\": \"body\",\n" +
                "      \"match\": [\"Whitelabel Error Page\"],\n" +
                "      \"is_active\": false\n" +
                "    }\n" +
                "  ]\n" +
                "}" +
                "</pre>" +
                "</body></html>";
        } else {
            helpContent = "<html><body style='font-family: sans-serif; padding: 10px;'>" +
                "<h2>Finger Rule Writing Guide</h2>" +
                "<p>Fingerprint rules use JSON format with two layers: <b>Definition</b> and <b>Matching Rules</b>.</p>" +
                "<h3>1. Fingerprint Definition</h3>" +
                "<ul>" +
                "<li><b>name</b>: Unique name of the fingerprint (e.g., 'Spring Boot').</li>" +
                "<li><b>type</b>: Category (e.g., 'Framework', 'CMS').</li>" +
                "<li><b>rules</b>: A list of one or more matching rules.</li>" +
                "</ul>" +
                "<h3>2. Matching Rule</h3>" +
                "<ul>" +
                "<li><b>location</b>: Where to match. Supported:<br/>" +
                "&nbsp;&nbsp;- <code>body</code>: Match response body content.<br/>" +
                "&nbsp;&nbsp;- <code>header</code>: Match response headers.<br/>" +
                "&nbsp;&nbsp;- <code>hash</code>: Match favicon hash (MurmurHash3).<br/>" +
                "&nbsp;&nbsp;- <code>status</code>: Match HTTP status code.</li>" +
                "<li><b>match</b>: List of keywords. Multiple keywords use <b>AND</b> logic.</li>" +
                "<li><b>path</b>: Probe path. For active rules only (e.g., <code>/favicon.ico</code>).</li>" +
                "<li><b>status</b>: Expected status code (e.g., <code>200</code>).</li>" +
                "<li><b>field</b>: Header field name if location is header (e.g., <code>Server</code>).</li>" +
                "<li><b>is_active</b>: Whether it's an active scan rule (true/false).</li>" +
                "</ul>" +
                "<h3>3. Example</h3>" +
                "<pre style='background: #f4f4f4; padding: 10px; border: 1px solid #ddd;'>" +
                "{\n" +
                "  \"name\": \"Spring Boot\",\n" +
                "  \"type\": \"Framework\",\n" +
                "  \"rules\": [\n" +
                "    {\n" +
                "      \"location\": \"body\",\n" +
                "      \"match\": [\"Whitelabel Error Page\"],\n" +
                "      \"is_active\": false\n" +
                "    }\n" +
                "  ]\n" +
                "}" +
                "</pre>" +
                "</body></html>";
        }
        
        editorPane.setText(helpContent);
        editorPane.setCaretPosition(0);
        
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(new Dimension(600, 500));
        JOptionPane.showMessageDialog(this, scrollPane, I18n.get("rule_help"), JOptionPane.INFORMATION_MESSAGE);
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
        updateButton.setText(I18n.get("rule_update"));
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
                    String name = f.getName() != null ? f.getName().toLowerCase() : "";
                    String type = f.getType() != null ? f.getType().toLowerCase() : "";
                    boolean matchesQuery = name.contains(query) || type.contains(query);
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

    private void updateRulesOnline() {
        if (JOptionPane.showConfirmDialog(this, I18n.get("rule_update_confirm"),
                I18n.get("common_confirm"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        updateButton.setEnabled(false);
        updateButton.setText(I18n.get("rule_updating"));

        new Thread(() -> {
            try {
                ConfigPersistence configPersistence = new ConfigPersistence(api);
                ScannerConfig config = configPersistence.loadConfig();
                String url = config.getUpdateUrl();
                
                if (url == null || url.isEmpty()) {
                    url = "https://fingerupload.oss-cn-beijing.aliyuncs.com/fingerprints.json";
                }

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<Fingerprint> newRules = mapper.readValue(response.body(),
                            mapper.getTypeFactory().constructCollectionType(List.class, Fingerprint.class));

                    if (newRules != null && !newRules.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            int addedCount = 0;
                            int updatedCount = 0;
                            List<String> preservedNames = new ArrayList<>();
                            
                            // 创建名称到指纹的映射，并记录所有本地规则名称
                            java.util.Map<String, Fingerprint> localMap = new java.util.HashMap<>();
                            java.util.Set<String> remoteNames = new java.util.HashSet<>();
                            for (Fingerprint remoteFp : newRules) {
                                if (remoteFp.getName() != null) remoteNames.add(remoteFp.getName());
                            }

                            for (Fingerprint fp : allFingerprints) {
                                if (fp.getName() != null) {
                                    localMap.put(fp.getName(), fp);
                                    if (!remoteNames.contains(fp.getName())) {
                                        preservedNames.add(fp.getName());
                                    }
                                }
                            }

                            for (Fingerprint remoteFp : newRules) {
                                if (remoteFp.getName() == null) continue;
                                
                                if (localMap.containsKey(remoteFp.getName())) {
                                    // 更新现有规则
                                    Fingerprint localFp = localMap.get(remoteFp.getName());
                                    int index = allFingerprints.indexOf(localFp);
                                    if (index != -1) {
                                        allFingerprints.set(index, remoteFp);
                                        updatedCount++;
                                    }
                                } else {
                                    // 新增规则
                                    allFingerprints.add(remoteFp);
                                    addedCount++;
                                }
                            }

                            filter();
                            ruleLoader.saveRules(allFingerprints);
                            updateButton.setEnabled(true);
                            updateButton.setText(I18n.get("rule_update"));
                            
                            StringBuilder sb = new StringBuilder();
                            if (I18n.getLanguage() == I18n.Language.CHINESE) {
                                sb.append(String.format("更新完成！\n\n新增规则: %d 条\n更新规则: %d 条\n保留自定义规则: %d 条", 
                                    addedCount, updatedCount, preservedNames.size()));
                                if (!preservedNames.isEmpty()) {
                                    sb.append("\n\n保留的规则列表:\n");
                                    for (String name : preservedNames) {
                                        sb.append("- ").append(name).append("\n");
                                    }
                                }
                            } else {
                                sb.append(String.format("Update Complete!\n\nAdded: %d\nUpdated: %d\nPreserved: %d", 
                                    addedCount, updatedCount, preservedNames.size()));
                                if (!preservedNames.isEmpty()) {
                                    sb.append("\n\nPreserved Rules:\n");
                                    for (String name : preservedNames) {
                                        sb.append("- ").append(name).append("\n");
                                    }
                                }
                            }
                            
                            // 同时打印到控制台方便复制
                            if (!preservedNames.isEmpty()) {
                                api.logging().logToOutput("--- Preserved Custom Rules ---");
                                for (String name : preservedNames) api.logging().logToOutput(name);
                            }

                            JOptionPane.showMessageDialog(this, sb.toString());
                        });
                    }
                } else {
                    throw new Exception("HTTP Status: " + response.statusCode());
                }
            } catch (Exception ex) {
                api.logging().logToError("Online update failed: " + ex.toString());
                ex.printStackTrace(); // Optional: will print to Burp's stderr
                
                SwingUtilities.invokeLater(() -> {
                    updateButton.setEnabled(true);
                    updateButton.setText(I18n.get("rule_update"));
                    
                    String errorMsg = ex.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = ex.toString(); // Use toString() if message is null (e.g. NPE)
                    }
                    
                    JOptionPane.showMessageDialog(this,
                            I18n.get("rule_update_failed") + errorMsg,
                            I18n.get("common_error"), JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
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
                
                // 校验：名称不能为空
                if (updated.getName() == null || updated.getName().trim().isEmpty()) {
                    String errorMsg = I18n.getLanguage() == I18n.Language.CHINESE ? "指纹名称不能为空！" : "Fingerprint name cannot be empty!";
                    JOptionPane.showMessageDialog(this, errorMsg, I18n.get("common_error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }

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

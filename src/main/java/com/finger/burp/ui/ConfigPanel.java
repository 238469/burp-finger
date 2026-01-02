package com.finger.burp.ui;

import burp.api.montoya.MontoyaApi;
import com.finger.burp.model.ScannerConfig;
import com.finger.burp.scanner.PassiveScanner;
import com.finger.burp.utils.ConfigPersistence;

import com.finger.burp.utils.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigPanel extends JPanel {
    private final MontoyaApi api;
    private final ConfigPersistence configPersistence;
    private final PassiveScanner passiveScanner;
    private final FingerTabPanel parentTabPanel;
    private ScannerConfig config;

    private JTextArea statusCodesArea;
    private JTextArea bodyKeywordsArea;
    private JSpinner threadCountSpinner;
    private JSpinner rpsSpinner;
    private JComboBox<I18n.Language> languageComboBox;
    private JTextField updateUrlField;

    public ConfigPanel(MontoyaApi api, PassiveScanner passiveScanner, FingerTabPanel parentTabPanel) {
        this.api = api;
        this.passiveScanner = passiveScanner;
        this.parentTabPanel = parentTabPanel;
        this.configPersistence = new ConfigPersistence(api);
        this.config = configPersistence.loadConfig();

        // 初始化语言设置
        I18n.setLanguage(config.getLanguage());

        // 同步配置到 ActiveScanner
        passiveScanner.getActiveScanner().updateConfig(this.config);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        refreshUI();
    }

    public void refreshUI() {
        removeAll();

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // 0. 语言设置
        JPanel langPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        langPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        langPanel.add(new JLabel(I18n.get("config_language")));
        languageComboBox = new JComboBox<>(I18n.Language.values());
        languageComboBox.setSelectedItem(I18n.getLanguage());
        languageComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof I18n.Language) {
                    setText(((I18n.Language) value).getDisplayName());
                }
                return this;
            }
        });
        langPanel.add(languageComboBox);
        mainPanel.add(langPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 1. 状态码过滤
        mainPanel.add(createSectionLabel(I18n.get("config_exclude_status")));
        statusCodesArea = new JTextArea(5, 40);
        statusCodesArea.setText(config.getExcludeStatusCodes().stream()
                .map(String::valueOf)
                .collect(Collectors.joining("\n")));
        mainPanel.add(new JScrollPane(statusCodesArea));
        mainPanel.add(Box.createVerticalStrut(10));

        // 2. Body 关键字过滤
        mainPanel.add(createSectionLabel(I18n.get("config_exclude_body")));
        bodyKeywordsArea = new JTextArea(5, 40);
        bodyKeywordsArea.setText(String.join("\n", config.getExcludeBodyKeywords()));
        mainPanel.add(new JScrollPane(bodyKeywordsArea));
        mainPanel.add(Box.createVerticalStrut(10));

        // 3. 线程数和 RPS
        JPanel settingsPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        settingsPanel.setMaximumSize(new Dimension(400, 60));
        settingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        settingsPanel.add(new JLabel(I18n.get("config_thread_count")));
        threadCountSpinner = new JSpinner(new SpinnerNumberModel(config.getThreadCount(), 1, 100, 1));
        settingsPanel.add(threadCountSpinner);

        settingsPanel.add(new JLabel(I18n.get("config_rps")));
        rpsSpinner = new JSpinner(new SpinnerNumberModel(config.getRequestsPerSecond(), 0.1, 1000.0, 1.0));
        settingsPanel.add(rpsSpinner);

        mainPanel.add(settingsPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // 4. 规则更新地址
        mainPanel.add(createSectionLabel(I18n.get("config_update_url")));
        updateUrlField = new JTextField(config.getUpdateUrl());
        updateUrlField.setMaximumSize(new Dimension(800, 30));
        updateUrlField.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(updateUrlField);
        JLabel hintLabel = new JLabel(I18n.get("config_update_url_hint"));
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(hintLabel.getFont().deriveFont(11f));
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(hintLabel);
        mainPanel.add(Box.createVerticalStrut(20));

        // 5. 保存按钮
        JButton saveButton = new JButton(I18n.get("config_save"));
        saveButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveButton.addActionListener(e -> saveConfig());
        mainPanel.add(saveButton);

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);
        
        revalidate();
        repaint();
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 5, 0));
        return label;
    }

    private void saveConfig() {
        try {
            // 解析状态码
            List<Integer> statusCodes = Arrays.stream(statusCodesArea.getText().split("[,\n]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            // 解析关键字
            List<String> keywords = Arrays.stream(bodyKeywordsArea.getText().split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            config.setExcludeStatusCodes(statusCodes);
            config.setExcludeBodyKeywords(keywords);
            config.setThreadCount((int) threadCountSpinner.getValue());
            config.setRequestsPerSecond((double) rpsSpinner.getValue());
            config.setUpdateUrl(updateUrlField.getText().trim());
            
            I18n.Language newLang = (I18n.Language) languageComboBox.getSelectedItem();
            config.setLanguage(newLang);
            I18n.setLanguage(newLang);

            configPersistence.saveConfig(config);
            passiveScanner.getActiveScanner().updateConfig(config);

            // 刷新所有 UI
            parentTabPanel.refreshI18n();

            JOptionPane.showMessageDialog(this, I18n.get("config_save_success"), I18n.get("config_save_success"), JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, I18n.get("config_invalid_status"), I18n.get("common_error"), JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, I18n.get("config_save_error") + e.getMessage(), I18n.get("common_error"), JOptionPane.ERROR_MESSAGE);
        }
    }
}

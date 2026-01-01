package com.finger.burp.ui;

import burp.api.montoya.MontoyaApi;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.rules.RuleLoader;
import com.finger.burp.scanner.PassiveScanner;
import com.finger.burp.utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;

public class FingerTabPanel extends JTabbedPane {
    private final MontoyaApi api;
    private final FingerTableModel tableModel;
    private final FingerDetailTableModel detailTableModel;
    private final PassiveScanner passiveScanner;
    private final RuleLoader ruleLoader;
    private final List<Fingerprint> fingerprints;
    private final JTable hostTable;
    private final JTable detailTable;

    private JButton clearButton;
    private JCheckBox activeScanCheckBox;
    private JLabel depthLabel;
    private JSpinner depthSpinner;
    private JLabel countLabel;
    private JMenuItem copyNucleiItem;
    private RuleManagerPanel ruleManagerPanel;
    private ConfigPanel configPanel;

    public FingerTabPanel(MontoyaApi api, FingerTableModel tableModel, PassiveScanner passiveScanner, RuleLoader ruleLoader, List<Fingerprint> fingerprints) {
        this.api = api;
        this.tableModel = tableModel;
        this.detailTableModel = new FingerDetailTableModel();
        this.passiveScanner = passiveScanner;
        this.ruleLoader = ruleLoader;
        this.fingerprints = fingerprints;
        
        // 1. 第一个标签页：扫描结果
        JPanel resultsPanel = new JPanel(new BorderLayout());
        
        // 1.1 顶部控制面板
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        clearButton = new JButton(I18n.get("finger_clear"));
        clearButton.addActionListener(e -> {
            tableModel.clear();
            detailTableModel.clear();
            passiveScanner.clearCache();
        });
        
        // 主动探测开关
        activeScanCheckBox = new JCheckBox(I18n.get("finger_enable_active"), passiveScanner.isActiveScanEnabled());
        
        // 探测深度配置
        depthLabel = new JLabel(I18n.get("finger_scan_depth"));
        depthSpinner = new JSpinner(new SpinnerNumberModel(passiveScanner.getActiveScanner().getScanDepth(), 0, 10, 1));
        depthSpinner.setPreferredSize(new Dimension(50, 25));
        depthSpinner.addChangeListener(e -> {
            int depth = (int) depthSpinner.getValue();
            passiveScanner.getActiveScanner().setScanDepth(depth);
            api.logging().logToOutput("[*] Active Scan Depth set to: " + depth);
        });

        activeScanCheckBox.addActionListener(e -> {
            boolean enabled = activeScanCheckBox.isSelected();
            passiveScanner.setActiveScanEnabled(enabled);
            depthSpinner.setEnabled(enabled);
            api.logging().logToOutput("[*] Active Scan " + (enabled ? "Enabled" : "Disabled"));
        });
        depthSpinner.setEnabled(activeScanCheckBox.isSelected());

        countLabel = new JLabel(I18n.get("common_total") + "0");
        tableModel.addTableModelListener(e -> updateCountLabel());

        topPanel.add(clearButton);
        topPanel.add(new JSeparator(JSeparator.VERTICAL));
        topPanel.add(activeScanCheckBox);
        topPanel.add(depthLabel);
        topPanel.add(depthSpinner);
        topPanel.add(new JSeparator(JSeparator.VERTICAL));
        topPanel.add(countLabel);

        // 1.2 主从表布局
        hostTable = new JTable(tableModel);
        hostTable.setAutoCreateRowSorter(true);
        hostTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // 添加右键菜单
        JPopupMenu popupMenu = new JPopupMenu();
        copyNucleiItem = new JMenuItem(I18n.get("menu_copy_nuclei"));
        copyNucleiItem.addActionListener(e -> {
            int selectedRow = hostTable.getSelectedRow();
            if (selectedRow != -1) {
                int modelRow = hostTable.convertRowIndexToModel(selectedRow);
                String host = tableModel.getHostAt(modelRow);
                String hostFingerprints = tableModel.getFingerprintsAt(modelRow);
                if (host != null && !host.isEmpty()) {
                    StringBuilder command = new StringBuilder("nuclei -u " + host);
                    
                    if (hostFingerprints != null && !hostFingerprints.isEmpty()) {
                        // 将指纹转换为 nuclei tag 格式：小写，空格换成横杠，逗号分隔
                        String tags = hostFingerprints.toLowerCase()
                                .replace(", ", ",")
                                .replace(" ", "-");
                        command.append(" -tags ").append(tags);
                    }
                    
                    String finalCommand = command.toString();
                    StringSelection selection = new StringSelection(finalCommand);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, selection);
                    api.logging().logToOutput("[+] Copied Nuclei command: " + finalCommand);
                }
            }
        });
        popupMenu.add(copyNucleiItem);
        hostTable.setComponentPopupMenu(popupMenu);
        
        hostTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = hostTable.getSelectedRow();
                if (selectedRow != -1) {
                    int modelRow = hostTable.convertRowIndexToModel(selectedRow);
                    detailTableModel.setDetails(tableModel.getDetailsForHost(modelRow));
                }
            }
        });

        JScrollPane hostScrollPane = new JScrollPane(hostTable);
        detailTable = new JTable(detailTableModel);
        detailTable.setAutoCreateRowSorter(true);
        JScrollPane detailScrollPane = new JScrollPane(detailTable);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, hostScrollPane, detailScrollPane);
        splitPane.setDividerLocation(300);

        resultsPanel.add(topPanel, BorderLayout.NORTH);
        resultsPanel.add(splitPane, BorderLayout.CENTER);
        
        // 2. 第二个标签页：规则管理
        ruleManagerPanel = new RuleManagerPanel(api, ruleLoader, fingerprints);
        
        // 3. 第三个标签页：系统配置
        configPanel = new ConfigPanel(api, passiveScanner, this);
        
        this.addTab(I18n.get("tab_finger"), resultsPanel);
        this.addTab(I18n.get("tab_rules"), ruleManagerPanel);
        this.addTab(I18n.get("tab_config"), configPanel);
        
        updateCountLabel();
    }

    private void updateCountLabel() {
        countLabel.setText(I18n.get("common_total") + tableModel.getRowCount());
    }

    public void refreshI18n() {
        // 更新 Tab 标题
        setTitleAt(0, I18n.get("tab_finger"));
        setTitleAt(1, I18n.get("tab_rules"));
        setTitleAt(2, I18n.get("tab_config"));

        // 更新第一个标签页组件
        clearButton.setText(I18n.get("finger_clear"));
        activeScanCheckBox.setText(I18n.get("finger_enable_active"));
        depthLabel.setText(I18n.get("finger_scan_depth"));
        copyNucleiItem.setText(I18n.get("menu_copy_nuclei"));
        updateCountLabel();

        // 更新表格列名
        tableModel.fireTableStructureChanged();
        detailTableModel.fireTableStructureChanged();

        // 更新子面板
        ruleManagerPanel.refreshI18n();
        configPanel.refreshUI();
    }
}

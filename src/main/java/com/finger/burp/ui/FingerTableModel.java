package com.finger.burp.ui;

import com.finger.burp.utils.I18n;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import java.net.URL;

public class FingerTableModel extends AbstractTableModel {
    private final List<String> hosts = new ArrayList<>();
    private final Map<String, List<ScanResult>> hostToDetails = new HashMap<>();
    private final Map<String, String> hostToAggregatedFingerprints = new HashMap<>();
    private final Map<String, String> hostToAggregatedTypes = new HashMap<>();
    private final Map<String, String> hostToAggregatedMethods = new HashMap<>();
    private final Map<String, String> hostToLastTime = new HashMap<>();
    
    // 用于全局去重：Host + FingerprintName + MatchedField
    private final Set<String> deduplicationSet = Collections.synchronizedSet(new HashSet<>());
    
    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0: return I18n.get("column_last_time");
            case 1: return I18n.get("column_host");
            case 2: return I18n.get("column_fingerprint");
            case 3: return I18n.get("column_types");
            case 4: return I18n.get("column_methods");
            default: return "";
        }
    }

    public synchronized void addResult(ScanResult newResult) {
        String host = getHost(newResult.getUrl());
        
        // 全局去重逻辑
        String dedupKey = host + "|" + newResult.getName() + "|" + newResult.getMatchedField();
        if (deduplicationSet.contains(dedupKey)) {
            return; // 已经匹配过完全相同的内容，直接忽略
        }
        deduplicationSet.add(dedupKey);
        
        // 1. 更新明细数据
        hostToDetails.computeIfAbsent(host, k -> new ArrayList<>()).add(0, newResult);
        
        // 2. 更新聚合数据
        String existingFingerprints = hostToAggregatedFingerprints.getOrDefault(host, "");
        String updatedFingerprints = mergeStrings(existingFingerprints, newResult.getName());
        hostToAggregatedFingerprints.put(host, updatedFingerprints);

        String existingTypes = hostToAggregatedTypes.getOrDefault(host, "");
        String updatedTypes = mergeStrings(existingTypes, newResult.getType());
        hostToAggregatedTypes.put(host, updatedTypes);

        String existingMethods = hostToAggregatedMethods.getOrDefault(host, "");
        String updatedMethods = mergeStrings(existingMethods, newResult.getMethod());
        hostToAggregatedMethods.put(host, updatedMethods);

        hostToLastTime.put(host, newResult.getTimestamp());

        // 3. 更新列表顺序（新发现或更新的 Host 移到顶部）
        if (hosts.contains(host)) {
            hosts.remove(host);
        }
        hosts.add(0, host);
        
        fireTableDataChanged();
    }

    public synchronized List<ScanResult> getDetailsForHost(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= hosts.size()) {
            return Collections.emptyList();
        }
        String host = hosts.get(rowIndex);
        return hostToDetails.getOrDefault(host, Collections.emptyList());
    }

    public synchronized String getHostAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= hosts.size()) {
            return "";
        }
        return hosts.get(rowIndex);
    }

    public synchronized String getFingerprintsAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= hosts.size()) {
            return "";
        }
        String host = hosts.get(rowIndex);
        return hostToAggregatedFingerprints.getOrDefault(host, "");
    }

    private String getHost(String urlString) {
        try {
            if (urlString.startsWith("http")) {
                URL url = new URL(urlString);
                return url.getProtocol() + "://" + url.getHost() + (url.getPort() == -1 ? "" : ":" + url.getPort());
            }
            return urlString;
        } catch (Exception e) {
            return urlString;
        }
    }

    private String mergeStrings(String existing, String newValue) {
        if (existing == null || existing.isEmpty()) return newValue;
        Set<String> set = new LinkedHashSet<>(Arrays.asList(existing.split(", ")));
        set.add(newValue);
        return String.join(", ", set);
    }

    public synchronized void clear() {
        hosts.clear();
        hostToDetails.clear();
        hostToAggregatedFingerprints.clear();
        hostToAggregatedTypes.clear();
        hostToAggregatedMethods.clear();
        hostToLastTime.clear();
        deduplicationSet.clear();
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return hosts.size();
    }

    @Override
    public int getColumnCount() {
        return 5;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        String host = hosts.get(rowIndex);
        switch (columnIndex) {
            case 0: return hostToLastTime.get(host);
            case 1: return host;
            case 2: return hostToAggregatedFingerprints.get(host);
            case 3: return hostToAggregatedTypes.get(host);
            case 4: {
                String method = hostToAggregatedMethods.get(host);
                if (I18n.getLanguage() == I18n.Language.CHINESE && method != null) {
                    return method.replace("Active", "主动")
                            .replace("Passive (Request)", "被动 (请求)")
                            .replace("Passive (Response)", "被动 (响应)")
                            .replace("Passive", "被动");
                }
                return method;
            }
            default: return null;
        }
    }
}

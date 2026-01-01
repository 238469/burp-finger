package com.finger.burp.ui;

import com.finger.burp.utils.I18n;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class FingerDetailTableModel extends AbstractTableModel {
    private final List<ScanResult> details = new ArrayList<>();

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0: return I18n.get("column_timestamp");
            case 1: return I18n.get("column_url");
            case 2: return I18n.get("column_name");
            case 3: return I18n.get("column_types");
            case 4: return I18n.get("column_methods");
            case 5: return I18n.get("column_field");
            default: return "";
        }
    }

    public synchronized void setDetails(List<ScanResult> newDetails) {
        details.clear();
        details.addAll(newDetails);
        fireTableDataChanged();
    }

    public synchronized void clear() {
        details.clear();
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return details.size();
    }

    @Override
    public int getColumnCount() {
        return 6;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ScanResult result = details.get(rowIndex);
        switch (columnIndex) {
            case 0: return result.getTimestamp();
            case 1: return result.getUrl();
            case 2: return result.getName();
            case 3: return result.getType();
            case 4: {
                String method = result.getMethod();
                if (I18n.getLanguage() == I18n.Language.CHINESE) {
                    return method.replace("Active", "主动")
                            .replace("Passive (Request)", "被动 (请求)")
                            .replace("Passive (Response)", "被动 (响应)")
                            .replace("Passive", "被动");
                }
                return method;
            }
            case 5: return result.getMatchedField();
            default: return null;
        }
    }
}

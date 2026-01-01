package com.finger.burp.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScanResult {
    private final String url;
    private final String name;
    private final String type;
    private final String method; // Passive or Active
    private final String matchedField; // body, header, path, etc.
    private final String timestamp;

    public ScanResult(String url, String name, String type, String method, String matchedField) {
        this.url = url;
        this.name = name;
        this.type = type;
        this.method = method;
        this.matchedField = matchedField;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getUrl() { return url; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getMethod() { return method; }
    public String getMatchedField() { return matchedField; }
    public String getTimestamp() { return timestamp; }
}

package com.finger.burp.model;

import com.finger.burp.utils.I18n;

import java.util.ArrayList;
import java.util.List;

public class ScannerConfig {
    private List<Integer> excludeStatusCodes;
    private List<String> excludeBodyKeywords;
    private int threadCount;
    private double requestsPerSecond;
    private I18n.Language language;

    public ScannerConfig() {
        // 默认配置
        this.excludeStatusCodes = new ArrayList<>();
        this.excludeStatusCodes.add(404);
        this.excludeStatusCodes.add(403);
        this.excludeStatusCodes.add(500);
        this.excludeStatusCodes.add(502);
        this.excludeStatusCodes.add(503);
        this.excludeStatusCodes.add(504);

        this.excludeBodyKeywords = new ArrayList<>();
        this.excludeBodyKeywords.add("404 Not Found");
        this.excludeBodyKeywords.add("Page Not Found");
        
        this.threadCount = 10;
        this.requestsPerSecond = 10.0;
        this.language = I18n.Language.CHINESE;
    }

    public List<Integer> getExcludeStatusCodes() {
        return excludeStatusCodes;
    }

    public void setExcludeStatusCodes(List<Integer> excludeStatusCodes) {
        this.excludeStatusCodes = excludeStatusCodes;
    }

    public List<String> getExcludeBodyKeywords() {
        return excludeBodyKeywords;
    }

    public void setExcludeBodyKeywords(List<String> excludeBodyKeywords) {
        this.excludeBodyKeywords = excludeBodyKeywords;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public double getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(double requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public I18n.Language getLanguage() {
        return language;
    }

    public void setLanguage(I18n.Language language) {
        this.language = language;
    }
}

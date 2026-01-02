package com.finger.burp.scanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import burp.api.montoya.proxy.http.InterceptedResponse;
import com.finger.burp.engine.MatchingEngine;
import com.finger.burp.engine.MatchResult;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.Rule;
import com.finger.burp.ui.FingerTableModel;
import com.finger.burp.ui.ScanResult;
import com.finger.burp.utils.ResultPersistence;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PassiveScanner implements ProxyResponseHandler, ProxyRequestHandler {
    private final MontoyaApi api;
    private final MatchingEngine matchingEngine;
    private final ResultPersistence persistence;
    private final ActiveScanner activeScanner;
    private final FingerTableModel tableModel;
    
    // 主动探测开关
    private boolean activeScanEnabled = true;
    // 已扫描过主动探测的路径集合 (Host + Path)，用于去重
    private final Set<String> scannedActivePaths = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // 已扫描过的 URL 集合，用于被动匹配去重
    private final Set<String> scannedUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PassiveScanner(MontoyaApi api, List<Fingerprint> fingerprints, FingerTableModel tableModel) {
        this.api = api;
        this.matchingEngine = new MatchingEngine(api, fingerprints);
        this.persistence = new ResultPersistence();
        this.tableModel = tableModel;
        this.activeScanner = new ActiveScanner(api, fingerprints, tableModel);
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        String url = interceptedRequest.url();
        String path = interceptedRequest.path();
        
        // 1. 请求匹配逻辑
        List<MatchResult> matches = matchingEngine.findMatches(interceptedRequest, path);
        
        if (!matches.isEmpty()) {
            processMatches(url, matches, "Passive (Request)");
        }
        
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyResponseReceivedAction handleResponseReceived(InterceptedResponse interceptedResponse) {
        String url = interceptedResponse.initiatingRequest().url();
        String path = interceptedResponse.initiatingRequest().path();
        String host = interceptedResponse.initiatingRequest().httpService().toString();
        
        // 1. URL 级别去重：如果已经扫描过该 URL，则不再进行被动匹配
        if (scannedUrls.contains(url)) {
            return ProxyResponseReceivedAction.continueWith(interceptedResponse);
        }
        scannedUrls.add(url);
        
        // 2. 被动匹配逻辑
        List<MatchResult> matches = matchingEngine.findMatches(interceptedResponse, path);
        
        if (!matches.isEmpty()) {
            processMatches(url, matches, "Passive (Response)");
        }

        // 3. 主动探测逻辑触发
        if (activeScanEnabled) {
            String fullPath = interceptedResponse.initiatingRequest().path();
            // 提取纯路径部分，去掉查询参数
            String basePath = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf("?")) : fullPath;
            
            // 进一步规范化路径：统一去掉结尾的 /，防止 /admin 和 /admin/ 重复触发
            String normalizedPath = basePath;
            if (normalizedPath.length() > 1 && normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            
            String hostPathKey = host + normalizedPath;
            if (!scannedActivePaths.contains(hostPathKey)) {
                scannedActivePaths.add(hostPathKey);
                activeScanner.scan(interceptedResponse.initiatingRequest().httpService(), basePath);
            }
        }
        
        return ProxyResponseReceivedAction.continueWith(interceptedResponse);
    }

    private void processMatches(String url, List<MatchResult> matches, String method) {
        api.logging().logToOutput("[+] Found " + method + " Fingerprint(s) at " + url);
        
        // 1. 提取指纹对象用于持久化
        List<Fingerprint> fps = matches.stream()
                .map(MatchResult::getFingerprint)
                .distinct()
                .collect(Collectors.toList());
        persistence.saveResults(url, fps);
        
        // 2. 更新 UI（增加指纹级别的去重，防止同一个 URL 下同一个指纹显示多行）
        // 使用 Set 记录当前 URL 已添加过的指纹名称
        Set<String> addedFingerprints = new java.util.HashSet<>();
        
        for (MatchResult match : matches) {
            Fingerprint fp = match.getFingerprint();
            
            // 如果这个指纹在这个 URL 下已经添加过了，就跳过
            if (addedFingerprints.contains(fp.getName())) {
                continue;
            }
            addedFingerprints.add(fp.getName());

            Rule rule = match.getMatchedRule();
            
            // 构造匹配字段描述
            String fieldDesc = rule.getLocation();
            if (rule.getDescription() != null && !rule.getDescription().isEmpty()) {
                fieldDesc = "[" + rule.getDescription() + "] " + fieldDesc;
            } else {
                if (rule.getField() != null && !rule.getField().isEmpty()) {
                    fieldDesc += " (" + rule.getField() + ")";
                }
                if (rule.getMatch() != null && !rule.getMatch().isEmpty()) {
                    fieldDesc += ": " + rule.getMatch();
                }
            }

            tableModel.addResult(new ScanResult(url, fp.getName(), fp.getType(), method, fieldDesc));
        }
    }

    @Override
    public ProxyResponseToBeSentAction handleResponseToBeSent(InterceptedResponse interceptedResponse) {
        return ProxyResponseToBeSentAction.continueWith(interceptedResponse);
    }

    public ActiveScanner getActiveScanner() {
        return activeScanner;
    }

    public void setActiveScanEnabled(boolean enabled) {
        this.activeScanEnabled = enabled;
    }

    public boolean isActiveScanEnabled() {
        return activeScanEnabled;
    }

    public void clearCache() {
        scannedActivePaths.clear();
        scannedUrls.clear();
        if (activeScanner != null) {
            activeScanner.clearCache();
        }
    }

    public void shutdown() {
        if (activeScanner != null) {
            activeScanner.shutdown();
        }
    }
}

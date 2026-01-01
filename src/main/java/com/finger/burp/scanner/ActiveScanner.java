package com.finger.burp.scanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.google.common.util.concurrent.RateLimiter;
import com.finger.burp.engine.MatchingEngine;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.Rule;
import com.finger.burp.ui.FingerTableModel;
import com.finger.burp.ui.ScanResult;
import com.finger.burp.utils.HashUtils;
import com.finger.burp.utils.ResultPersistence;

import java.util.*;
import java.util.concurrent.*;

public class ActiveScanner {
    private final MontoyaApi api;
    private final List<Fingerprint> fingerprints;
    private final MatchingEngine matchingEngine;
    private final ResultPersistence persistence;
    private Executor executor;
    private final RateLimiter rateLimiter;
    private final FingerTableModel tableModel;
    private com.finger.burp.model.ScannerConfig config;
    private int scanDepth = 1; // 默认探测深度为 1

    // 记录已经执行过完整主动探测的路径 (Host + Path)，防止递归扫描导致的重复
    private final Set<String> alreadyScannedPaths = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public ActiveScanner(MontoyaApi api, List<Fingerprint> fingerprints, FingerTableModel tableModel) {
        this(api, fingerprints, tableModel, null);
    }

    /**
     * 主要供测试使用，允许注入自定义 Executor
     */
    public ActiveScanner(MontoyaApi api, List<Fingerprint> fingerprints, FingerTableModel tableModel, Executor executor) {
        this.api = api;
        this.fingerprints = fingerprints;
        this.matchingEngine = new MatchingEngine(api, fingerprints);
        this.persistence = new ResultPersistence();
        this.tableModel = tableModel;
        
        // 加载持久化配置
        com.finger.burp.utils.ConfigPersistence configPersistence = new com.finger.burp.utils.ConfigPersistence(api);
        this.config = configPersistence.loadConfig();
        
        this.rateLimiter = RateLimiter.create(config.getRequestsPerSecond());
        
        if (executor != null) {
            this.executor = executor;
        } else {
            this.executor = Executors.newFixedThreadPool(config.getThreadCount());
        }
    }

    public synchronized void updateConfig(com.finger.burp.model.ScannerConfig newConfig) {
        this.config = newConfig;
        this.rateLimiter.setRate(newConfig.getRequestsPerSecond());
        
        // 动态调整线程池大小 (仅当 executor 是 ThreadPoolExecutor 时)
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
            tpe.setCorePoolSize(newConfig.getThreadCount());
            tpe.setMaximumPoolSize(newConfig.getThreadCount());
        }
    }

    public void shutdown() {
        if (executor instanceof ExecutorService) {
            ExecutorService es = (ExecutorService) executor;
            es.shutdown();
            try {
                if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
                    es.shutdownNow();
                }
            } catch (InterruptedException e) {
                es.shutdownNow();
            }
        }
    }

    public void scan(HttpService httpService) {
        scan(httpService, "/");
    }

    /**
     * 对指定的服务执行主动探测。
     * 
     * @param httpService 目标服务信息
     * @param currentPath 当前触发探测的路径
     */
    public void scan(HttpService httpService, String currentPath) {
        executor.execute(() -> {
            String baseUrl = httpService.toString();
            
            // 1. 处理路径递归：当请求 /aaa/bbb 时，不仅探测 /aaa/bbb/，还要探测 /aaa/ 和 /
            List<String> pathsToScan = new ArrayList<>();
            String tempPath = currentPath;
            
            // 规范化起始路径：确保以 / 开头
            if (!tempPath.startsWith("/")) tempPath = "/" + tempPath;
            
            // 递归获取所有父级目录
            while (true) {
                // 去掉末尾斜杠后的路径作为扫描基准
                String scanBase = tempPath.endsWith("/") && tempPath.length() > 1 
                                  ? tempPath.substring(0, tempPath.length() - 1) 
                                  : tempPath;
                pathsToScan.add(scanBase);
                
                if (tempPath.equals("/")) break;
                
                // 向上移动一级
                int lastSlash = tempPath.lastIndexOf('/', tempPath.length() - (tempPath.endsWith("/") ? 2 : 1));
                if (lastSlash == -1) {
                    tempPath = "/";
                } else {
                    tempPath = tempPath.substring(0, lastSlash + 1);
                }
            }
            
            // 对每个父级路径进行深度校验和扫描
            for (String pathToScan : pathsToScan) {
                // 优化：如果基准路径包含 "."（如 /index.php），通常意味着它是一个文件而非目录。
                // 这种情况下不应该在该路径下进行子路径探测（如 /index.php/config.ini），除非它是根目录。
                if (!pathToScan.equals("/") && pathToScan.contains(".")) {
                    continue;
                }

                String hostPathKey = baseUrl + pathToScan;
                if (alreadyScannedPaths.contains(hostPathKey)) {
                    continue;
                }
                alreadyScannedPaths.add(hostPathKey);

                int depth = calculateDepth(pathToScan);
                if (depth > scanDepth) continue;
                
                performScanAtPath(httpService, pathToScan, depth);
            }
        });
    }

    public void clearCache() {
        alreadyScannedPaths.clear();
    }

    /**
     * 在指定路径下执行实际的扫描任务
     */
    private void performScanAtPath(HttpService httpService, String currentPath, int currentPathDepth) {
        String baseUrl = httpService.toString();
        api.logging().logToOutput("[*] Starting active scan for: " + baseUrl + currentPath + " (Depth: " + currentPathDepth + ")");

        // 1. 提取所有定义了 path 或 hash 规则的指纹
        Map<String, List<Fingerprint>> pathRules = new HashMap<>();
        for (Fingerprint fp : fingerprints) {
            if (fp.getRules() == null) continue;
            for (Rule rule : fp.getRules()) {
                String location = rule.getLocation();
                String rulePath = rule.getPath();
                
                if ("hash".equalsIgnoreCase(location)) {
                    // hash 类型的规则强制只探测根目录
                    if (currentPathDepth == 0) {
                        String effectiveHashPath = (rulePath != null && !rulePath.isEmpty()) ? rulePath : "/favicon.ico";
                        pathRules.computeIfAbsent(effectiveHashPath, k -> new ArrayList<>()).add(fp);
                    }
                } else if (rulePath != null && !rulePath.isEmpty()) {
                    // 带有 path 的主动规则
                    pathRules.computeIfAbsent(rulePath, k -> new ArrayList<>()).add(fp);
                }
            }
        }

        // 2. 按路径并行发起请求
        for (Map.Entry<String, List<Fingerprint>> entry : pathRules.entrySet()) {
            String path = entry.getKey();
            List<Fingerprint> relatedFps = entry.getValue();

            executor.execute(() -> {
                // 确保 path 以 / 开头
                String normalizedProbePath = path;
                if (!normalizedProbePath.startsWith("/")) {
                    normalizedProbePath = "/" + normalizedProbePath;
                }
                
                // 构造完整的探测路径（基于当前层级）
                String fullPath = currentPath;
                if (fullPath.endsWith("/")) {
                    fullPath = fullPath.substring(0, fullPath.length() - 1);
                }
                fullPath = fullPath + normalizedProbePath;

                rateLimiter.acquire(); // 限流，确保即使多线程并发，总请求速率依然受控

                try {
                    // 显式构造完整的请求行和头部
                    String rawRequest = "GET " + fullPath + " HTTP/1.1\r\n" +
                                       "Host: " + httpService.host() + "\r\n" +
                                       "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36\r\n" +
                                       "Connection: close\r\n\r\n";
                    
                    HttpRequest request = HttpRequest.httpRequest(httpService, rawRequest);
                    
                    HttpRequestResponse reqResp = api.http().sendRequest(request);
                HttpResponse response = reqResp.response();

                if (response != null) {
                    // 1. 检查状态码过滤
                    if (config.getExcludeStatusCodes() != null && config.getExcludeStatusCodes().contains((int) response.statusCode())) {
                        return;
                    }

                    // 2. 检查响应体过滤
                    String body = response.bodyToString();
                    if (config.getExcludeBodyKeywords() != null) {
                        for (String keyword : config.getExcludeBodyKeywords()) {
                            if (body.contains(keyword)) {
                                return;
                            }
                        }
                    }

                    // 优化：过滤掉响应体长度为 0 的情况。
                    // 许多 WAF 或异常处理会返回 200 但内容为空，这会导致误报。
                    if (response.body().length() == 0) {
                        return;
                    }

                    for (Fingerprint fp : relatedFps) {
                            // 检查该指纹下所有对应当前路径的规则 (path 或 hash)
                            for (Rule rule : fp.getRules()) {
                                String location = rule.getLocation();
                                String rulePath = rule.getPath();
                                String effectivePath = rulePath;
                                
                                if ("hash".equalsIgnoreCase(location)) {
                                    effectivePath = (rulePath != null && !rulePath.isEmpty()) ? rulePath : "/favicon.ico";
                                }

                                if (path.equals(effectivePath)) {
                                    if (checkSingleRule(rule, response)) {
                                        String resultUrl = baseUrl + fullPath;
                                        api.logging().logToOutput("[+] Active Match Found: " + fp.getName() + " at " + resultUrl);
                                        persistence.saveResults(resultUrl, Collections.singletonList(fp));
                                        
                                        // 构造匹配字段描述
                                        String displayLocation = location;
                                        // 如果规则定义了 status 但没有定义 match/hash，说明是基于状态码的存活性探测
                                        if (rule.getStatus() != null && 
                                            (rule.getMatch() == null || rule.getMatch().isEmpty()) && 
                                            (rule.getHash() == null || rule.getHash().isEmpty())) {
                                            displayLocation = "status";
                                        }

                                        String fieldDesc = displayLocation + ": " + fullPath;
                                        if (rule.getMatch() != null && !rule.getMatch().isEmpty()) {
                                            fieldDesc += " (match: " + rule.getMatch() + ")";
                                        } else if (rule.getHash() != null && !rule.getHash().isEmpty()) {
                                            fieldDesc += " (hash: " + rule.getHash() + ")";
                                        } else if (rule.getStatus() != null) {
                                            fieldDesc += " (status: " + rule.getStatus() + ")";
                                        }

                                        // 更新 UI
                                        tableModel.addResult(new ScanResult(resultUrl, fp.getName(), fp.getType(), "Active", fieldDesc));
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    api.logging().logToError("Active scan failed for " + fullPath + ": " + e.getMessage());
                }
            });
        }
    }

    private int calculateDepth(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return 0;
        }
        
        // 预处理路径：如果是文件路径（例如 /aaa/bbb/index.php），应该看作是 /aaa/bbb/ 目录
        String dirPath = path;
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        if (lastDot > lastSlash) {
            // 包含点且点在最后一个斜杠之后，认为是文件，取其父目录
            dirPath = path.substring(0, lastSlash + 1);
        }

        if (dirPath == null || dirPath.isEmpty() || "/".equals(dirPath)) {
            return 0;
        }

        // 移除前导和尾随斜杠
        String trimmed = dirPath.startsWith("/") ? dirPath.substring(1) : dirPath;
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) return 0;
        
        // 统计斜杠数量
        int count = 0;
        for (char c : trimmed.toCharArray()) {
            if (c == '/') count++;
        }
        return count + 1;
    }

    public void setScanDepth(int scanDepth) {
        this.scanDepth = scanDepth;
    }

    public int getScanDepth() {
        return scanDepth;
    }

    private boolean checkSingleRule(Rule rule, HttpResponse response) {
        // 匹配状态码
        if (rule.getStatus() != null && response.statusCode() != rule.getStatus()) {
            return false;
        }
        
        // 匹配字符串 (AND 逻辑)
        if (rule.getMatch() != null && !rule.getMatch().isEmpty()) {
            String body = response.bodyToString();
            if (body == null) return false;
            for (String m : rule.getMatch()) {
                if (!body.contains(m)) {
                    return false;
                }
            }
        }

        // 匹配 Hash (支持 MurmurHash3 和 MD5)
        if (rule.getHash() != null && !rule.getHash().isEmpty()) {
            byte[] bodyBytes = response.body().getBytes();
            String actualMurmur = HashUtils.calculateFaviconHash(bodyBytes);
            String actualMD5 = HashUtils.calculateMD5(bodyBytes);
            
            if (!rule.getHash().equals(actualMurmur) && !rule.getHash().equalsIgnoreCase(actualMD5)) {
                return false;
            }
        }
        
        return true;
    }
    
}

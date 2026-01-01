package com.finger.burp.engine;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.HttpHeader;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.Rule;
import com.finger.burp.utils.HashUtils;

import java.util.ArrayList;
import java.util.List;

public class MatchingEngine {
    private final MontoyaApi api;
    private final List<Fingerprint> fingerprints;

    public MatchingEngine(MontoyaApi api, List<Fingerprint> fingerprints) {
        this.api = api;
        this.fingerprints = fingerprints;
    }

    /**
     * 在响应中寻找匹配的指纹。
     */
    public List<MatchResult> findMatches(HttpResponse response, String currentPath) {
        List<MatchResult> matches = new ArrayList<>();
        if (response == null) return matches;

        for (Fingerprint fp : fingerprints) {
            if (fp.getRules() == null) continue;
            for (Rule rule : fp.getRules()) {
                if (matchRule(rule, response.headers(), response.bodyToString(), response.body().getBytes(), response.statusCode(), currentPath)) {
                    matches.add(new MatchResult(fp, rule));
                }
            }
        }
        return matches;
    }

    /**
     * 在请求中寻找匹配的指纹。
     */
    public List<MatchResult> findMatches(HttpRequest request, String currentPath) {
        List<MatchResult> matches = new ArrayList<>();
        if (request == null) return matches;

        for (Fingerprint fp : fingerprints) {
            if (fp.getRules() == null) continue;
            for (Rule rule : fp.getRules()) {
                // 请求没有状态码，传 0
                if (matchRule(rule, request.headers(), request.bodyToString(), request.body().getBytes(), 0, currentPath)) {
                    matches.add(new MatchResult(fp, rule));
                }
            }
        }
        return matches;
    }

    private boolean matchRule(Rule rule, List<HttpHeader> headers, String bodyText, byte[] bodyBytes, int statusCode, String currentPath) {
        // 1. 路径校验逻辑
        // 如果规则定义了 path，则只有在当前请求路径与之匹配时才允许命中（用于区分主动/被动规则）
        if (rule.getPath() != null && !rule.getPath().isEmpty()) {
            if (currentPath == null || !isPathMatch(currentPath, rule.getPath())) {
                return false;
            }
        }

        // 2. 如果规则指定了状态码，首先检查状态码是否匹配
        if (rule.getStatus() != null && statusCode != 0 && statusCode != rule.getStatus()) {
            return false;
        }

        String location = rule.getLocation();
        if (location == null) {
            // 如果没有指定 location 但指定了 status，且上面已经匹配通过，则返回 true
            return rule.getStatus() != null;
        }

        switch (location.toLowerCase()) {
            case "header":
                return matchHeader(rule, headers);
            case "body":
                return matchBody(rule, bodyText);
            case "hash":
                return matchHash(rule, bodyBytes);
            case "status":
                // 已经在开头检查过了，如果能走到这里说明状态码匹配或规则未设置状态码
                // 对于 location 为 status 的规则，必须设置了 status 字段才算有效匹配
                return rule.getStatus() != null;
            default:
                return false;
        }
    }

    private boolean isPathMatch(String currentPath, String rulePath) {
        if (currentPath == null || rulePath == null) return false;

        // 规范化处理：去掉查询参数（如果存在）
        String p1 = currentPath.contains("?") ? currentPath.substring(0, currentPath.indexOf("?")) : currentPath;
        String p2 = rulePath.contains("?") ? rulePath.substring(0, rulePath.indexOf("?")) : rulePath;

        // 确保以 / 开头
        if (!p1.startsWith("/")) p1 = "/" + p1;
        if (!p2.startsWith("/")) p2 = "/" + p2;

        // 去掉末尾的 /
        if (p1.length() > 1 && p1.endsWith("/")) p1 = p1.substring(0, p1.length() - 1);
        if (p2.length() > 1 && p2.endsWith("/")) p2 = p2.substring(0, p2.length() - 1);

        return p1.equalsIgnoreCase(p2);
    }

    private boolean matchHash(Rule rule, byte[] bodyBytes) {
        String expectedHash = rule.getHash();
        if (expectedHash == null || expectedHash.isEmpty()) return false;
        if (bodyBytes == null || bodyBytes.length == 0) return false;

        String actualHash = HashUtils.calculateFaviconHash(bodyBytes);
        if (expectedHash.equals(actualHash)) return true;

        String actualMD5 = HashUtils.calculateMD5(bodyBytes);
        return expectedHash.equalsIgnoreCase(actualMD5);
    }

    private boolean matchHeader(Rule rule, List<HttpHeader> headers) {
        String field = rule.getField();
        List<String> matches = rule.getMatch();
        
        if (matches == null || matches.isEmpty()) return false;

        for (String match : matches) {
            boolean found = false;
            for (HttpHeader header : headers) {
                if (field != null && !field.isEmpty()) {
                    if (header.name().equalsIgnoreCase(field)) {
                        if (header.value().contains(match)) {
                            found = true;
                            break;
                        }
                    }
                } else {
                    if (header.toString().contains(match)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private boolean matchBody(Rule rule, String bodyText) {
        List<String> matches = rule.getMatch();
        if (matches == null || matches.isEmpty()) return false;
        if (bodyText == null) return false;

        for (String match : matches) {
            if (!bodyText.contains(match)) {
                return false;
            }
        }
        return true;
    }
}

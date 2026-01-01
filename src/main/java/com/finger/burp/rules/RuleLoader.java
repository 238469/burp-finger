package com.finger.burp.rules;

import burp.api.montoya.MontoyaApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.model.FingerprintList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class RuleLoader {
    private final MontoyaApi api;
    private final ObjectMapper mapper;
    private String externalRulePath;

    public RuleLoader(MontoyaApi api) {
        this.api = api;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        initExternalPath();
    }

    private void initExternalPath() {
        try {
            String jarPath = api.extension().filename();
            if (jarPath != null && !jarPath.isEmpty()) {
                Path path = Paths.get(jarPath).getParent().resolve("fingerprints.json");
                this.externalRulePath = path.toString();
            } else {
                // 回退方案：当前工作目录
                this.externalRulePath = "fingerprints.json";
            }
        } catch (Exception e) {
            this.externalRulePath = "fingerprints.json";
            api.logging().logToError("Failed to determine JAR path: " + e.getMessage());
        }
    }

    public String getExternalRulePath() {
        return externalRulePath;
    }

    /**
     * 加载所有指纹规则。
     * 优先从外部 fingerprints.json 加载，如果不存在则从内部资源初始化。
     */
    public List<Fingerprint> loadAllRules() {
        List<Fingerprint> allFingerprints = new ArrayList<>();
        File externalFile = new File(externalRulePath);

        if (externalFile.exists()) {
            api.logging().logToOutput("Loading rules from external file: " + externalRulePath);
            try (InputStream is = new FileInputStream(externalFile)) {
                List<Fingerprint> list = mapper.readValue(is, mapper.getTypeFactory().constructCollectionType(List.class, Fingerprint.class));
                if (list != null) {
                    allFingerprints.addAll(list);
                }
            } catch (Exception e) {
                api.logging().logToError("Failed to load external rules, falling back to internal: " + e.getMessage());
            }
        }

        // 如果外部加载失败或不存在，从内部加载并初始化外部文件
        if (allFingerprints.isEmpty()) {
            api.logging().logToOutput("External rules not found, initializing from internal resources...");
            String internalPath = "/rules/fingerprints.json";
            try (InputStream is = getClass().getResourceAsStream(internalPath)) {
                if (is != null) {
                    List<Fingerprint> list = mapper.readValue(is, mapper.getTypeFactory().constructCollectionType(List.class, Fingerprint.class));
                    if (list != null) {
                        allFingerprints.addAll(list);
                        // 初始化外部文件
                        saveRules(allFingerprints);
                    }
                } else {
                    api.logging().logToError("Internal rules not found: " + internalPath);
                }
            } catch (Exception e) {
                api.logging().logToError("Failed to load internal rules: " + e.getMessage());
            }
        }

        api.logging().logToOutput("Total fingerprints loaded: " + allFingerprints.size());
        return allFingerprints;
    }

    public void saveRules(List<Fingerprint> fingerprints) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(externalRulePath), fingerprints);
            api.logging().logToOutput("Rules saved to: " + externalRulePath);
        } catch (Exception e) {
            api.logging().logToError("Failed to save rules to " + externalRulePath + ": " + e.getMessage());
        }
    }
}

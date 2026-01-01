package com.finger.burp.utils;

import burp.api.montoya.MontoyaApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finger.burp.model.ScannerConfig;

public class ConfigPersistence {
    private static final String CONFIG_KEY = "finger_scanner_config";
    private final MontoyaApi api;
    private final ObjectMapper mapper;

    public ConfigPersistence(MontoyaApi api) {
        this.api = api;
        this.mapper = new ObjectMapper();
    }

    public void saveConfig(ScannerConfig config) {
        try {
            String json = mapper.writeValueAsString(config);
            api.persistence().extensionData().setString(CONFIG_KEY, json);
        } catch (JsonProcessingException e) {
            api.logging().logToError("Failed to save config: " + e.getMessage());
        }
    }

    public ScannerConfig loadConfig() {
        String json = api.persistence().extensionData().getString(CONFIG_KEY);
        if (json == null || json.isEmpty()) {
            return new ScannerConfig();
        }
        try {
            return mapper.readValue(json, ScannerConfig.class);
        } catch (JsonProcessingException e) {
            api.logging().logToError("Failed to load config, using default: " + e.getMessage());
            return new ScannerConfig();
        }
    }
}

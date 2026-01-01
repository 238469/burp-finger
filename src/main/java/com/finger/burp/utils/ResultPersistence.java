package com.finger.burp.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finger.burp.model.Fingerprint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ResultPersistence {
    private static final String FILE_NAME = "finger_results.json";
    private final ObjectMapper mapper;
    private final String projectPath;

    public ResultPersistence() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // 默认保存在当前工作目录
        this.projectPath = System.getProperty("user.dir");
    }

    public synchronized void saveResults(String url, List<Fingerprint> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }

        File file = new File(projectPath, FILE_NAME);
        ArrayNode rootNode;

        try {
            if (file.exists()) {
                rootNode = (ArrayNode) mapper.readTree(file);
            } else {
                rootNode = mapper.createArrayNode();
            }

            ObjectNode resultNode = mapper.createObjectNode();
            resultNode.put("url", url);
            resultNode.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            ArrayNode fingerprintsNode = resultNode.putArray("fingerprints");
            for (Fingerprint fp : matches) {
                ObjectNode fpNode = fingerprintsNode.addObject();
                fpNode.put("name", fp.getName());
                fpNode.put("type", fp.getType());
            }

            rootNode.add(resultNode);
            mapper.writeValue(file, rootNode);
        } catch (IOException e) {
            // 在实际 Burp 插件中，这里应该用 api.logging().logToError
            e.printStackTrace();
        }
    }
}

package com.finger.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import com.finger.burp.rules.RuleLoader;
import com.finger.burp.model.Fingerprint;
import com.finger.burp.scanner.PassiveScanner;
import com.finger.burp.ui.FingerTableModel;
import com.finger.burp.ui.FingerTabPanel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FingerExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        // 设置插件名称
        api.extension().setName("Finger");

        Logging logging = api.logging();

        // 打印加载成功的日志到 Burp 控制台
        logging.logToOutput("========================================");
        logging.logToOutput("Finger Plugin Loading...");
        
        // 1. 初始化规则加载器并加载规则
        RuleLoader ruleLoader = new RuleLoader(api);
        List<Fingerprint> fingerprints = Collections.synchronizedList(new ArrayList<>(ruleLoader.loadAllRules()));
        
        // 2. 初始化扫描器与 UI (调整顺序)
        FingerTableModel tableModel = new FingerTableModel();
        PassiveScanner passiveScanner = new PassiveScanner(api, fingerprints, tableModel);
        
        FingerTabPanel tabPanel = new FingerTabPanel(api, tableModel, passiveScanner, ruleLoader, fingerprints);
        api.userInterface().registerSuiteTab("Finger", tabPanel);
        
        // 3. 注册被动扫描处理器 (请求和响应)
        api.proxy().registerRequestHandler(passiveScanner);
        api.proxy().registerResponseHandler(passiveScanner);
        
        // 4. 注册卸载处理器，清理线程池
        api.extension().registerUnloadingHandler(() -> {
            logging.logToOutput("Finger Plugin Unloading...");
            passiveScanner.shutdown();
        });
        
        logging.logToOutput("Finger Plugin Loaded Successfully!");
        logging.logToOutput("Version: 1.0-SNAPSHOT");
        logging.logToOutput("Total Rules: " + fingerprints.size());
        logging.logToOutput("========================================");
    }
}

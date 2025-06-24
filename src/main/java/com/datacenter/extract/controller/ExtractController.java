package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ExtractController {

    private static final Logger logger = LoggerFactory.getLogger(ExtractController.class);
    private final TextExtractionService textExtractionService;

    @Autowired
    public ExtractController(TextExtractionService textExtractionService) {
        this.textExtractionService = textExtractionService;
        logger.info("ExtractController initialized successfully");
    }

    /**
     * 统一文本提取接口
     */
    @PostMapping("/extract")
    public CompletableFuture<JSONObject> extract(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String textInput = request.getString("textInput");
            String extractParams = request.getString("extractParams");

            logger.info("Received extract request - extractParams: {}, textLength: {}",
                    extractParams, textInput != null ? textInput.length() : 0);

            if (textInput == null || textInput.trim().isEmpty()) {
                logger.warn("Extract request failed - empty text content");
                JSONObject error = new JSONObject();
                error.put("error", "文本内容不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            try {
                String result = textExtractionService.extractTextData(textInput, extractParams);
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Extract request completed successfully in {}ms", duration);
                return JSONObject.parseObject(result);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Extract request failed after {}ms - error: {}", duration, e.getMessage(), e);
                JSONObject error = new JSONObject();
                error.put("error", "提取处理失败: " + e.getMessage());
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }
        });
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public JSONObject health() {
        logger.debug("Health check request received");
        try {
            String healthResult = textExtractionService.healthCheck();
            logger.debug("Health check completed successfully");
            return JSONObject.parseObject(healthResult);
        } catch (Exception e) {
            logger.error("Health check failed - error: {}", e.getMessage(), e);
            JSONObject error = new JSONObject();
            error.put("status", "unhealthy");
            error.put("error", e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return error;
        }
    }

    /**
     * 服务信息接口
     */
    @GetMapping("/info")
    public JSONObject info() {
        logger.debug("Info request received");
        JSONObject info = new JSONObject();
        info.put("service", "intelligent-extraction-service");
        info.put("version", "1.0.0");
        info.put("description", "统一文本提取服务 - 简化设计");
        info.put("architecture", "ExtractController → TextExtractionService.extractTextData");
        info.put("unified_method", "extractTextData(textInput, extractParams)");
        info.put("supported_modes", "自动识别: 三元组、批量、社交关系等");
        info.put("optimization", "极简设计，一个方法处理所有提取需求");
        info.put("usage", "POST /api/v1/extract {\"textInput\":\"文本\", \"extractParams\":\"参数\"}");
        info.put("timestamp", System.currentTimeMillis());
        logger.debug("Info request completed");
        return info;
    }
}

package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @PostMapping("/extract")
    public CompletableFuture<JSONObject> extract(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String text = request.getString("text");
            String extractType = request.getString("extractType");

            logger.info("Received extract request - extractType: {}, textLength: {}",
                    extractType, text != null ? text.length() : 0);

            if (extractType == null || extractType.isEmpty()) {
                extractType = "triples";
            }

            if (text == null || text.trim().isEmpty()) {
                logger.warn("Extract request failed - empty text content");
                JSONObject error = new JSONObject();
                error.put("error", "文本内容不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            try {
                // 正确的调用关系：Controller → TextExtractionService
                String result = textExtractionService.extractTriples(text, extractType, null);
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

    @PostMapping("/extract/batch")
    public CompletableFuture<JSONObject> batchExtract(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            List<String> texts = request.getList("texts", String.class);
            String extractType = request.getString("extractType");

            logger.info("Received batch extract request - extractType: {}, textsCount: {}",
                    extractType, texts != null ? texts.size() : 0);

            if (extractType == null || extractType.isEmpty()) {
                extractType = "triples";
            }

            if (texts == null || texts.isEmpty()) {
                logger.warn("Batch extract request failed - empty texts list");
                JSONObject error = new JSONObject();
                error.put("error", "文本列表不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            try {
                String textsJson = "[" + texts.stream()
                        .map(t -> "\"" + t.replace("\"", "\\\"") + "\"")
                        .reduce((a, b) -> a + "," + b)
                        .orElse("") + "]";

                // 正确的调用关系：Controller → TextExtractionService
                String result = textExtractionService.batchExtract(textsJson, extractType, null);
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Batch extract request completed successfully in {}ms", duration);
                return JSONObject.parseObject(result);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Batch extract request failed after {}ms - error: {}", duration, e.getMessage(), e);
                JSONObject error = new JSONObject();
                error.put("error", "批量提取处理失败: " + e.getMessage());
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }
        });
    }

    @PostMapping("/extract/social")
    public CompletableFuture<JSONObject> extractSocial(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String text = request.getString("text");
            String extractTypes = request.getString("extractTypes");
            Boolean maskSensitive = request.getBoolean("maskSensitive");

            logger.info("Received social extract request - extractTypes: {}, maskSensitive: {}, textLength: {}",
                    extractTypes, maskSensitive, text != null ? text.length() : 0);

            if (text == null || text.trim().isEmpty()) {
                logger.warn("Social extract request failed - empty text content");
                JSONObject error = new JSONObject();
                error.put("error", "文本内容不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            try {
                // 正确的调用关系：Controller → TextExtractionService
                String result = textExtractionService.extractSocialInfo(
                        text,
                        extractTypes != null ? extractTypes : "entities,relations",
                        maskSensitive != null ? maskSensitive : false);
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Social extract request completed successfully in {}ms", duration);
                return JSONObject.parseObject(result);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Social extract request failed after {}ms - error: {}", duration, e.getMessage(), e);
                JSONObject error = new JSONObject();
                error.put("error", "社交关系提取失败: " + e.getMessage());
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }
        });
    }

    @GetMapping("/health")
    public JSONObject health() {
        logger.debug("Health check request received");
        try {
            // 正确的调用关系：Controller → TextExtractionService
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

    @GetMapping("/info")
    public JSONObject info() {
        logger.debug("Info request received");
        JSONObject info = new JSONObject();
        info.put("service", "intelligent-extraction-service");
        info.put("version", "1.0.0");
        info.put("description", "按照正确分层架构设计的智能文本提取服务");
        info.put("architecture", "ExtractController → TextExtractionService → SmartAIProvider → AIModelCaller");
        info.put("principle", "Controller不直接调用大模型，严格遵循分层架构");
        info.put("call_relationship", "✅ 正确：Controller只调用TextExtractionService");
        info.put("data_flow", "文本输入 → AI提取 → 数据库保存 → 结果返回");
        info.put("timestamp", System.currentTimeMillis());
        logger.debug("Info request completed");
        return info;
    }
}

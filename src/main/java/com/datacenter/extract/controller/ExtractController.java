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

        // 基础服务信息
        info.put("service", "extract-service");
        info.put("name", "智能文本提取服务");
        info.put("version", "1.0.0");
        info.put("description", "基于Spring AI MCP的统一文本提取服务");

        // 技术架构
        info.put("framework", "Spring Boot 3.2.0 + Spring AI 1.0.0");
        info.put("java_version", "17");
        info.put("architecture", "MCP Server → Controller → Service → Database");

        // 核心组件
        JSONObject components = new JSONObject();
        components.put("controller", "ExtractController - 统一API接口");
        components.put("service", "TextExtractionService - 核心提取逻辑");
        components.put("ai_provider", "SmartAIProvider - AI模型调用");
        components.put("database", "DatabaseService - 完整7表数据保存");
        info.put("components", components);

        // 核心方法
        info.put("core_method", "extractTextData(textInput, extractParams)");
        info.put("mcp_tools", new String[] { "extract_text_data", "health_check" });

        // 支持的功能
        JSONObject features = new JSONObject();
        features.put("extraction_modes", "三元组提取、批量处理、社交关系分析");
        features.put("auto_detection", "智能识别人员、作品、事件实体类型");
        features.put("database_tables", 7);
        features.put("relation_mapping", "完整的实体关系图谱构建");
        info.put("features", features);

        // 数据库信息
        JSONObject database = new JSONObject();
        database.put("type", "MySQL");
        database.put("schema", "base_data_graph");
        database.put("main_tables", new String[] { "celebrity", "work", "event" });
        database.put("relation_tables",
                new String[] { "celebrity_celebrity", "celebrity_work", "celebrity_event", "event_work" });
        info.put("database", database);

        // API使用
        JSONObject api = new JSONObject();
        api.put("endpoint", "POST /api/v1/extract");
        api.put("request_format", "{\"textInput\":\"文本内容\", \"extractParams\":\"triples\"}");
        api.put("health_check", "GET /api/v1/health");
        api.put("service_info", "GET /api/v1/info");
        info.put("api", api);

        // 优化特点
        info.put("optimization", "极简设计 - 三个重复方法合并为一个统一方法");
        info.put("performance", "智能缓存 + 并行处理 + 数据库连接池");

        info.put("timestamp", System.currentTimeMillis());
        logger.debug("Info request completed");
        return info;
    }
}

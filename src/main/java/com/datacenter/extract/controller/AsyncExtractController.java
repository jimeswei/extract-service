package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 异步文本提取控制器
 * 专门处理异步提取请求，立即返回成功响应
 */
@RestController
@RequestMapping("/api/v1/async")
@CrossOrigin(origins = "*")
public class AsyncExtractController {

    private static final Logger logger = LoggerFactory.getLogger(AsyncExtractController.class);
    private final TextExtractionService textExtractionService;

    @Autowired
    public AsyncExtractController(TextExtractionService textExtractionService) {
        this.textExtractionService = textExtractionService;
        logger.info("AsyncExtractController initialized successfully");
    }

    /**
     * 异步文本提取接口 - 立即返回成功状态，后台异步处理
     */
    @PostMapping("/extract")
    public JSONObject extractAsync(@RequestBody JSONObject request) {
        long startTime = System.currentTimeMillis();

        try {
            // 设置默认提取参数
            String extractParams = request.getString("extractParams");
            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples";
            }

            // 处理textInput，支持字符串或JSON数组格式
            String textInput = null;
            try {
                // 尝试作为JSONArray获取
                JSONArray textInputObj = request.getJSONArray("textInput");
                if (textInputObj != null && !textInputObj.isEmpty()) {
                    // JSON数组格式，转换为JSON字符串传递给Service
                    textInput = textInputObj.toJSONString();
                    logger.info(
                            "Received async extract request - extractParams: {}, textInputType: JSONArray, arraySize: {}",
                            extractParams, textInputObj.size());
                }
            } catch (Exception e) {
                // 如果不是数组，则作为字符串获取
                textInput = request.getString("textInput");
                logger.info("Received async extract request - extractParams: {}, textInputType: String, textLength: {}",
                        extractParams, textInput != null ? textInput.length() : 0);
            }

            if (textInput == null || textInput.trim().isEmpty()) {
                logger.warn("Async extract request failed - empty text content");
                JSONObject error = new JSONObject();
                error.put("error", "文本内容不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            // 调用异步方法处理
            textExtractionService.processTextAsync(textInput, extractParams);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Async extract request submitted successfully in {}ms", duration);

            // 立即返回成功响应
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "任务已提交，正在后台处理");
            result.put("timestamp", System.currentTimeMillis());
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Async extract request failed after {}ms - error: {}", duration, e.getMessage(), e);
            JSONObject error = new JSONObject();
            error.put("error", "提取任务提交失败: " + e.getMessage());
            error.put("success", false);
            error.put("timestamp", System.currentTimeMillis());
            return error;
        }
    }

    /**
     * 获取异步提取服务信息
     */
    @GetMapping("/info")
    public JSONObject info() {
        JSONObject info = new JSONObject();

        info.put("service", "async-extract-service");
        info.put("name", "异步文本提取服务");
        info.put("version", "1.0.0");
        info.put("description", "支持异步处理的智能文本提取服务");

        // 异步处理特性
        JSONObject features = new JSONObject();
        features.put("async_processing", "立即返回响应，后台异步处理");
        features.put("thread_pool", "专用线程池处理提取任务");
        features.put("concurrent_tasks", "支持并发处理多个提取任务");
        features.put("response_format", "只返回success状态，不返回提取内容");
        info.put("features", features);

        // API接口
        JSONObject api = new JSONObject();
        api.put("async_extract", "POST /api/v1/async/extract");
        api.put("service_info", "GET /api/v1/async/info");
        api.put("health_check", "GET /api/v1/async/health");
        info.put("api", api);

        // 线程池配置
        JSONObject threadPool = new JSONObject();
        threadPool.put("core_pool_size", 5);
        threadPool.put("max_pool_size", 20);
        threadPool.put("queue_capacity", 100);
        threadPool.put("thread_name_prefix", "TextExtract-");
        info.put("thread_pool_config", threadPool);

        // 支持的数据格式
        JSONObject dataFormats = new JSONObject();
        dataFormats.put("single_text", "{\"textInput\":\"单个文本\"}");
        dataFormats.put("array_text", "{\"textInput\":[\"文本1\",\"文本2\"]}");
        info.put("supported_formats", dataFormats);

        // 提取参数选项
        JSONObject extractOptions = new JSONObject();
        extractOptions.put("triples", "三元组提取（默认）");
        extractOptions.put("entities", "实体提取");
        extractOptions.put("relations", "关系提取");
        info.put("extract_params", extractOptions);

        info.put("timestamp", System.currentTimeMillis());
        return info;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public JSONObject health() {
        logger.debug("Async health check request received");
        try {
            JSONObject health = new JSONObject();
            health.put("status", "healthy");
            health.put("service", "async-extract-controller");
            health.put("async_enabled", true);
            health.put("timestamp", System.currentTimeMillis());

            logger.debug("Async health check completed successfully");
            return health;
        } catch (Exception e) {
            logger.error("Async health check failed - error: {}", e.getMessage(), e);
            JSONObject error = new JSONObject();
            error.put("status", "unhealthy");
            error.put("error", e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return error;
        }
    }
}
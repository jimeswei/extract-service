package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.SmartAIProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ExtractController {

    private final SmartAIProvider smartAIProvider;

    @Autowired
    public ExtractController(SmartAIProvider smartAIProvider) {
        this.smartAIProvider = smartAIProvider;
    }

    @PostMapping("/extract")
    public CompletableFuture<JSONObject> extract(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            String text = request.getString("text");
            String extractTypeTemp = request.getString("extractType");
            final String extractType = (extractTypeTemp == null || extractTypeTemp.isEmpty()) ? "triples" : extractTypeTemp;

            if (text == null || text.trim().isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "文本内容不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            String result = smartAIProvider.process(text, extractType);
            return JSONObject.parseObject(result);
        });
    }

    @PostMapping("/extract/batch")
    public CompletableFuture<JSONObject> batchExtract(@RequestBody JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> texts = request.getList("texts", String.class);
            String extractTypeTemp = request.getString("extractType");
            final String extractType = (extractTypeTemp == null || extractTypeTemp.isEmpty()) ? "triples" : extractTypeTemp;

            if (texts == null || texts.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("error", "文本列表不能为空");
                error.put("success", false);
                error.put("timestamp", System.currentTimeMillis());
                return error;
            }

            List<String> results = texts.parallelStream()
                    .map(text -> smartAIProvider.process(text, extractType))
                    .toList();

            JSONObject response = new JSONObject();
            response.put("results", results);
            response.put("total", texts.size());
            response.put("extractType", extractType);
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            return response;
        });
    }

    @GetMapping("/health")
    public JSONObject health() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double memoryUsage = (double) (totalMemory - freeMemory) / totalMemory * 100;

        JSONObject health = new JSONObject();
        health.put("service", "extract-service");
        health.put("status", memoryUsage < 90 ? "healthy" : "degraded");
        health.put("memory_usage", String.format("%.2f%%", memoryUsage));
        health.put("timestamp", System.currentTimeMillis());

        Map<String, Object> cacheStats = smartAIProvider.getCacheStats();
        health.putAll(cacheStats);

        return health;
    }

    @GetMapping("/info")
    public JSONObject info() {
        JSONObject info = new JSONObject();
        info.put("service", "intelligent-extraction-service");
        info.put("version", "1.0.0");
        info.put("description", "按照系统架构设计文档实现的智能文本提取服务");
        info.put("features", new String[] {
                "知识三元组提取",
                "智能缓存",
                "容错降级",
                "批量处理"
        });
        info.put("architecture", "MCP工具接口层 → 业务编排层 → 核心处理层 → 基础设施层");
        info.put("ai_provider", "Deepseek API");
        info.put("cache_provider", "Caffeine");
        info.put("json_provider", "FastJSON2");
        info.put("timestamp", System.currentTimeMillis());
        return info;
    }
}

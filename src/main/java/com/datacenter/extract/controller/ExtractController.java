package com.datacenter.extract.controller;

import com.datacenter.extract.service.SmartAIProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 文本提取REST控制器
 * 按照架构文档要求提供标准API接口
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ExtractController {

    private final SmartAIProvider smartAIProvider;

    @Autowired
    public ExtractController(SmartAIProvider smartAIProvider) {
        this.smartAIProvider = smartAIProvider;
    }

    /**
     * 单文本提取接口 - 按照产品需求文档API设计
     */
    @PostMapping("/extract")
    public CompletableFuture<String> extract(@RequestBody Map<String, String> request) {
        return CompletableFuture.supplyAsync(() -> {
            String text = request.get("text");
            String extractType = request.getOrDefault("extractType", "triples");

            if (text == null || text.trim().isEmpty()) {
                return """
                        {"error":"文本内容不能为空","success":false}
                        """;
            }

            return smartAIProvider.process(text, extractType);
        });
    }

    /**
     * 批量提取接口
     */
    @PostMapping("/extract/batch")
    public CompletableFuture<String> batchExtract(@RequestBody Map<String, Object> request) {
        return CompletableFuture.supplyAsync(() -> {
            @SuppressWarnings("unchecked")
            List<String> texts = (List<String>) request.get("texts");
            String extractType = (String) request.getOrDefault("extractType", "triples");

            if (texts == null || texts.isEmpty()) {
                return """
                        {"error":"文本列表不能为空","success":false}
                        """;
            }

            // 简单并行处理
            List<String> results = texts.parallelStream()
                    .map(text -> smartAIProvider.process(text, extractType))
                    .toList();

            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(Map.of(
                                "results", results,
                                "total", texts.size(),
                                "success", true,
                                "timestamp", System.currentTimeMillis()));
            } catch (Exception e) {
                return """
                        {"error":"批量处理失败","success":false}
                        """;
            }
        });
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double memoryUsage = (double) (totalMemory - freeMemory) / totalMemory * 100;

        Map<String, Object> health = Map.of(
                "service", "extract-service",
                "status", memoryUsage < 90 ? "healthy" : "degraded",
                "memory_usage", String.format("%.2f%%", memoryUsage),
                "timestamp", System.currentTimeMillis());

        // 合并缓存统计信息
        Map<String, Object> result = new java.util.HashMap<>(health);
        result.putAll(smartAIProvider.getCacheStats());

        return result;
    }

    /**
     * 服务信息接口
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "intelligent-extraction-service",
                "version", "1.0.0",
                "description", "按照系统架构设计文档实现的智能文本提取服务",
                "features", new String[] {
                        "知识三元组提取",
                        "智能缓存",
                        "容错降级",
                        "批量处理"
                },
                "architecture", "MCP工具接口层 → 业务编排层 → 核心处理层 → 基础设施层",
                "ai_provider", "Deepseek API",
                "cache_provider", "Caffeine",
                "timestamp", System.currentTimeMillis());
    }
}
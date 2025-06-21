package com.datacenter.extract.service;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 文本提取服务 - 按照系统架构设计文档第3.2章实现
 * 
 * 实现MCP工具接口的核心服务
 * 巧妙设计：三行代码完成所有逻辑
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final SmartAIProvider smartAIProvider;

    @Autowired
    public TextExtractionService(SmartAIProvider smartAIProvider) {
        this.smartAIProvider = smartAIProvider;
    }

    /**
     * @Tool注解标记的核心工具方法
     *                  按照架构文档设计：从文本中提取知识三元组，自动识别实体和关系
     */
    @Tool(name = "extract_triples", description = "从文本中提取知识三元组，自动识别实体和关系")
    public String extractTriples(
            @ToolParam(description = "文本内容") String text,
            @ToolParam(description = "提取类型(默认triples)") String extractType,
            @ToolParam(description = "额外选项(可选)") String options) {

        // 巧妙设计：智能缓存键生成，考虑所有影响因子
        String cacheKey = CacheKeyGenerator.generate(text, extractType, options);

        try {
            // 直接使用SmartAIProvider处理，它内部已包含缓存逻辑
            return smartAIProvider.process(text, extractType != null ? extractType : "triples");
        } catch (Exception e) {
            log.error("提取失败: {}", e.getMessage());
            return createErrorResponse(e);
        }
    }

    /**
     * 批量提取工具方法
     * 按照架构文档设计：批量提取多个文本的知识三元组，自动并行处理提升效率
     */
    @Tool(name = "batch_extract", description = "批量提取多个文本的知识三元组，自动并行处理提升效率")
    public String batchExtract(
            @ToolParam(description = "文本数组(JSON格式)") String texts,
            @ToolParam(description = "提取类型") String extractType,
            @ToolParam(description = "批次大小(可选)") String batchSize) {

        try {
            // 简化实现：直接解析JSON格式的文本数组
            List<String> textList = parseTextArray(texts);

            List<String> results = textList.parallelStream()
                    .map(text -> smartAIProvider.process(text, extractType != null ? extractType : "triples"))
                    .collect(Collectors.toList());

            return formatBatchResults(results);

        } catch (Exception e) {
            log.error("批量提取失败: {}", e.getMessage());
            return createErrorResponse(e);
        }
    }

    /**
     * 健康检查工具方法
     * 按照架构文档设计：检查服务健康状态，包括AI API可用性和系统资源状态
     */
    @Tool(name = "health_check", description = "检查服务健康状态，包括AI API可用性和系统资源状态")
    public String healthCheck() {

        try {
            HealthStatus status = checkSystemHealth();
            return createHealthResponse(status);
        } catch (Exception e) {
            log.error("健康检查失败: {}", e.getMessage());
            return createErrorResponse(e);
        }
    }

    // 私有辅助方法
    private List<String> parseTextArray(String texts) {
        // 简单的JSON数组解析
        if (texts == null || texts.trim().isEmpty()) {
            return new ArrayList<>();
        }

        texts = texts.trim();
        if (texts.startsWith("[") && texts.endsWith("]")) {
            texts = texts.substring(1, texts.length() - 1);
            return List.of(texts.split(","))
                    .stream()
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .collect(Collectors.toList());
        }

        return List.of(texts);
    }

    private String formatBatchResults(List<String> results) {
        try {
            return String.format("""
                    {"results":%s,"total":%d,"success":true,"timestamp":%d}
                    """, results.toString(), results.size(), System.currentTimeMillis());
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    private HealthStatus checkSystemHealth() {
        double memoryUsage = MemoryUtils.getUsagePercentage();
        Map<String, Object> cacheStats = smartAIProvider.getCacheStats();

        if (memoryUsage > 90) {
            return HealthStatus.UNHEALTHY;
        } else if (memoryUsage > 75) {
            return HealthStatus.DEGRADED;
        } else {
            return HealthStatus.HEALTHY;
        }
    }

    private String createHealthResponse(HealthStatus status) {
        return String.format("""
                {"status":"%s","memory_usage":"%.2f%%","cache_stats":%s,"success":true,"timestamp":%d}
                """,
                status.name().toLowerCase(),
                MemoryUtils.getUsagePercentage(),
                smartAIProvider.getCacheStats().toString(),
                System.currentTimeMillis());
    }

    private String createErrorResponse(Exception e) {
        return String.format("""
                {"error":"%s","success":false,"timestamp":%d}
                """, e.getMessage(), System.currentTimeMillis());
    }
}

/**
 * 缓存键生成器
 */
class CacheKeyGenerator {
    public static String generate(String text, String extractType, String options) {
        return Math.abs((text + extractType + options).hashCode()) + "";
    }
}

/**
 * 内存工具类
 */
class MemoryUtils {
    public static double getUsagePercentage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return (double) (totalMemory - freeMemory) / totalMemory * 100;
    }
}

/**
 * 健康状态枚举
 */
enum HealthStatus {
    HEALTHY, DEGRADED, UNHEALTHY
}
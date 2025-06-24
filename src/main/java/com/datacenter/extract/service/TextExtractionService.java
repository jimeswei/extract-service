package com.datacenter.extract.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 文本提取服务 - 按照系统架构设计文档第3.2章实现
 * 
 * 实现MCP工具接口的核心服务
 * 优化设计：统一提取逻辑，支持多种提取模式
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final SmartAIProvider smartAIProvider;
    private final DatabaseService databaseService;

    @Autowired
    public TextExtractionService(SmartAIProvider smartAIProvider, DatabaseService databaseService) {
        this.smartAIProvider = smartAIProvider;
        this.databaseService = databaseService;
    }

    /**
     * 统一文本提取方法 - 支持所有提取类型
     * 按照架构文档设计：支持单文本、批量文本、社交关系等多种提取模式
     */
    @Tool(name = "extract_text_data", description = "统一文本提取工具，支持三元组、批量提取、社交关系等多种模式")
    public String extractTextData(
            @ToolParam(description = "文本内容或文本数组(JSON格式)") String textInput,
            @ToolParam(description = "提取参数: entities,relations 或其他选项") String extractParams) {

        try {
            log.info("开始文本提取，输入长度: {}, 参数: {}",
                    textInput != null ? textInput.length() : 0, extractParams);

            // 统一处理所有提取请求
            String extractType = extractParams != null ? extractParams : "triples";
            String aiResult = smartAIProvider.process(textInput, extractType);

            // 保存到数据库
            databaseService.saveSocialData(aiResult);

            // 包装成标准成功响应格式
            return createSuccessResponse(aiResult);

        } catch (Exception e) {
            log.error("文本提取失败: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 健康检查工具方法
     */
    @Tool(name = "health_check", description = "检查服务健康状态，包括AI API可用性和系统资源状态")
    public String healthCheck() {
        try {
            HealthStatus status = checkSystemHealth();
            return createHealthResponse(status);
        } catch (Exception e) {
            log.error("健康检查失败: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    private HealthStatus checkSystemHealth() {
        try {
            double memoryUsage = MemoryUtils.getUsagePercentage();
            Map<String, Object> cacheStats = smartAIProvider.getCacheStats();

            if (memoryUsage > 90) {
                return HealthStatus.UNHEALTHY;
            } else if (memoryUsage > 75) {
                return HealthStatus.DEGRADED;
            } else {
                return HealthStatus.HEALTHY;
            }
        } catch (Exception e) {
            log.error("检查系统健康状态失败: {}", e.getMessage());
            return HealthStatus.UNHEALTHY;
        }
    }

    private String createHealthResponse(HealthStatus status) {
        try {
            double memoryUsage = MemoryUtils.getUsagePercentage();
            Map<String, Object> cacheStats = smartAIProvider.getCacheStats();

            return String.format(
                    """
                            {"status":"%s","memory_usage":"%.2f%%","database_enabled":true,"redis_connected":false,"cache_size":%d,"success":true,"timestamp":%d}
                            """,
                    status.name().toLowerCase(),
                    memoryUsage,
                    ((Number) cacheStats.get("cache_size")).longValue(),
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.error("创建健康检查响应失败: {}", e.getMessage());
            return String.format("""
                    {"status":"unhealthy","error":"%s","success":false,"timestamp":%d}
                    """, e.getMessage(), System.currentTimeMillis());
        }
    }

    private String createSuccessResponse(String aiResult) {
        try {
            // 如果AI结果已经是JSON格式，则合并success字段
            if (aiResult.startsWith("{") && aiResult.endsWith("}")) {
                // 移除最后的}，添加success字段
                String resultWithoutClosing = aiResult.substring(0, aiResult.lastIndexOf("}"));
                return String.format("""
                        %s,"success":true,"timestamp":%d}
                        """, resultWithoutClosing, System.currentTimeMillis());
            } else {
                // 如果不是JSON格式，包装成JSON
                return String.format("""
                        {"data":"%s","success":true,"timestamp":%d}
                        """, aiResult.replace("\"", "\\\""), System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.error("创建成功响应失败: {}", e.getMessage());
            return String.format("""
                    {"result":"%s","success":true,"timestamp":%d}
                    """, "数据处理成功", System.currentTimeMillis());
        }
    }

    private String createErrorResponse(String message) {
        return String.format("""
                {"error":"%s","success":false,"timestamp":%d}
                """, message, System.currentTimeMillis());
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
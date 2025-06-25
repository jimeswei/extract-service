package com.datacenter.extract.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 智能AI提供者 - 基于纯AI的文本提取服务
 * 
 * 设计：缓存+AI调用，支持长文本分批处理
 * 不使用规则兜底机制，确保数据质量
 * Caffeine缓存自动管理
 */
@Component
public class SmartAIProvider {

    private static final Logger log = LoggerFactory.getLogger(SmartAIProvider.class);

    // 长文本阈值：超过此长度启用分批处理
    private static final int LONG_TEXT_THRESHOLD = 2000;

    private final AIModelCaller aiCaller;
    private final LongTextProcessor longTextProcessor;
    private final Cache<String, String> cache;
    private final ObjectMapper objectMapper;

    @Autowired
    public SmartAIProvider(AIModelCaller aiCaller, LongTextProcessor longTextProcessor) {
        this.aiCaller = aiCaller;
        this.longTextProcessor = longTextProcessor;
        this.cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 智能AI提取处理：根据文本长度选择处理策略
     * - 短文本：直接AI调用
     * - 长文本：分批次并行处理
     */
    public String process(String text, String extractType) {
        String cacheKey = generateKey(text, extractType);
        int textLength = text.length();

        return cache.get(cacheKey, key -> {
            log.info("🚀 开始AI提取，文本长度: {} 字符", textLength);

            try {
                String result;

                if (textLength > LONG_TEXT_THRESHOLD) {
                    // 长文本：使用分批处理
                    log.info("📄 检测到长文本，启用分批处理模式");
                    result = longTextProcessor.processLongText(text, extractType);
                } else {
                    // 短文本：直接AI调用
                    log.info("📝 短文本，使用直接处理模式");
                    result = aiCaller.callAI(text, extractType);
                }

                // 验证AI响应是否有效，无效则抛出异常
                if (isValidResponse(result)) {
                    log.info("✅ AI提取成功，文本长度: {}，处理模式: {}",
                            textLength, textLength > LONG_TEXT_THRESHOLD ? "分批处理" : "直接处理");
                    return result;
                } else {
                    log.error("❌ AI提取失败，类型: {}，文本长度: {}", extractType, textLength);
                    throw new RuntimeException("AI提取失败，无法处理请求。请检查AI服务状态或稍后重试。");
                }

            } catch (Exception e) {
                log.error("💥 AI提取过程异常: {}", e.getMessage(), e);
                throw new RuntimeException("AI提取失败: " + e.getMessage());
            }
        });
    }

    /**
     * 验证AI响应是否有效
     */
    private boolean isValidResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }

        // 直接检查是否包含错误标识
        if (response.contains("\"error\"")) {
            return false;
        }

        // 检查是否包含success:false
        if (response.contains("\"success\":false")) {
            return false;
        }

        // 检查是否包含有效的数据结构
        return response.contains("triples") ||
                response.contains("entities") ||
                response.contains("relations");
    }

    /**
     * 生成缓存键
     */
    private String generateKey(String text, String extractType) {
        // 为长文本生成更精确的缓存键
        String baseKey = text + extractType;
        int hashCode = Math.abs(baseKey.hashCode());

        // 长文本添加特殊标识
        if (text.length() > LONG_TEXT_THRESHOLD) {
            return "long_" + hashCode + "_" + text.length();
        }

        return String.valueOf(hashCode);
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        var stats = cache.stats();
        return Map.of(
                "cache_size", cache.estimatedSize(),
                "hit_rate", String.format("%.2f%%", stats.hitRate() * 100),
                "miss_rate", String.format("%.2f%%", stats.missRate() * 100),
                "eviction_count", stats.evictionCount(),
                "long_text_threshold", LONG_TEXT_THRESHOLD);
    }

    /**
     * 获取处理统计信息
     */
    public Map<String, Object> getProcessingStats() {
        try {
            return Map.of(
                    "cache_stats", getCacheStats(),
                    "long_text_processor_stats", longTextProcessor.getProcessingStats(),
                    "ai_caller_stats", aiCaller.getCallStats(),
                    "long_text_threshold", LONG_TEXT_THRESHOLD);
        } catch (Exception e) {
            log.error("获取处理统计信息失败: {}", e.getMessage());
            return Map.of("error", "获取统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        cache.invalidateAll();
        log.info("🧹 缓存已清理");
    }

    /**
     * 预热缓存 - 可用于常用文本的预处理
     */
    public void warmupCache(String text, String extractType) {
        try {
            log.info("🔥 开始缓存预热，文本长度: {}", text.length());
            process(text, extractType);
            log.info("✅ 缓存预热完成");
        } catch (Exception e) {
            log.warn("⚠️  缓存预热失败: {}", e.getMessage());
        }
    }
}
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
 * 设计：缓存+AI调用，失败时直接报错
 * 不使用规则兜底机制，确保数据质量
 * Caffeine缓存自动管理
 */
@Component
public class SmartAIProvider {

    private static final Logger log = LoggerFactory.getLogger(SmartAIProvider.class);

    private final AIModelCaller aiCaller;
    private final Cache<String, String> cache;
    private final ObjectMapper objectMapper;

    @Autowired
    public SmartAIProvider(AIModelCaller aiCaller) {
        this.aiCaller = aiCaller;
        this.cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * AI提取处理：直接使用AI调用，失败时报错
     * 不使用规则兜底逻辑
     */
    public String process(String text, String extractType) {
        String cacheKey = generateKey(text, extractType);

        return cache.get(cacheKey, key -> {
            // AI调用
            String aiResult = aiCaller.callAI(text, extractType);

            // 验证AI响应是否有效，无效则抛出异常
            if (isValidResponse(aiResult)) {
                log.info("AI提取成功，文本长度: {}", text.length());
                return aiResult;
            } else {
                log.error("AI提取失败，类型: {}，文本长度: {}", extractType, text.length());
                throw new RuntimeException("AI提取失败，无法处理请求。请检查AI服务状态或稍后重试。");
            }
        });
    }

    /**
     * 验证AI响应是否有效
     */
    private boolean isValidResponse(String response) {
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
        return Math.abs((text + extractType).hashCode()) + "";
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        var stats = cache.stats();
        return Map.of(
                "cache_size", cache.estimatedSize(),
                "hit_rate", stats.hitRate(),
                "miss_rate", stats.missRate(),
                "eviction_count", stats.evictionCount());
    }
}
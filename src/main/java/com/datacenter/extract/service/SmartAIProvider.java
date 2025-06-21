package com.datacenter.extract.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能AI提供者 - 按照系统架构设计文档第4.1.2章实现
 * 
 * 巧妙设计：缓存+AI调用+兜底的三层保障机制
 * 正则表达式实体提取作为规则兜底
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
     * 巧妙设计：缓存+AI调用+兜底的三层保障
     * 第一选择：AI调用
     * 智能判断：如果AI失败，使用规则兜底
     */
    public String process(String text, String extractType) {
        String cacheKey = generateKey(text, extractType);

        return cache.get(cacheKey, key -> {
            // 第一选择：AI调用
            String aiResult = aiCaller.callAI(text, extractType);

            // 智能判断：如果AI失败，使用规则兜底
            if (isValidResponse(aiResult)) {
                log.info("AI提取成功，文本长度: {}", text.length());
                return aiResult;
            } else {
                log.warn("AI提取失败，使用规则兜底处理");
                return createFallbackResponse(text);
            }
        });
    }

    /**
     * 巧妙设计：简单规则兜底
     * 使用简单规则提取实体作为兜底
     */
    private String createFallbackResponse(String text) {
        // 使用简单规则提取实体作为兜底
        List<String> entities = extractSimpleEntities(text);

        List<Map<String, Object>> triples = new ArrayList<>();
        for (int i = 0; i < entities.size() - 1; i++) {
            triples.add(Map.of(
                    "subject", entities.get(i),
                    "predicate", "相关",
                    "object", entities.get(i + 1),
                    "confidence", 0.6));
        }

        try {
            return objectMapper.writeValueAsString(Map.of(
                    "triples", triples,
                    "success", true,
                    "fallback", true,
                    "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("兜底处理异常", e);
            return """
                    {"triples":[],"error":"兜底处理失败","success":false}
                    """;
        }
    }

    /**
     * 巧妙设计：正则简单实体提取
     * 识别组织、人名、地点等实体
     */
    private List<String> extractSimpleEntities(String text) {
        List<String> entities = new ArrayList<>();

        // 组织机构
        Pattern orgPattern = Pattern.compile("[\u4e00-\u9fa5]{2,8}(?:公司|企业|机构|大学|学院|医院|银行|集团)");
        entities.addAll(orgPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .limit(3)
                .collect(Collectors.toList()));

        // 人名（简单规则）
        Pattern personPattern = Pattern.compile("[\u4e00-\u9fa5]{2,4}(?=是|为|担任|创建|创立)");
        entities.addAll(personPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .limit(3)
                .collect(Collectors.toList()));

        // 地点
        Pattern locationPattern = Pattern.compile("[\u4e00-\u9fa5]{2,6}(?:市|省|区|县|国)");
        entities.addAll(locationPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .limit(2)
                .collect(Collectors.toList()));

        return entities.stream().distinct().limit(5).collect(Collectors.toList());
    }

    /**
     * 验证AI响应是否有效
     */
    private boolean isValidResponse(String response) {
        return response.contains("triples") &&
                response.contains("success") &&
                !response.contains("error");
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
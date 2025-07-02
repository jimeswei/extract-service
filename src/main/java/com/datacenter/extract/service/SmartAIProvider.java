package com.datacenter.extract.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private final AIModelCaller aiModelCaller;
    private final CacheService cacheService;
    private final QualityAssessmentService qualityAssessmentService;
    private final EntityDisambiguationService entityDisambiguationService;
    private final RelationValidationService relationValidationService;
    private final EntityValidationService entityValidationService;
    private final LongTextProcessor longTextProcessor;
    private final Cache<String, String> cache;
    private final ObjectMapper objectMapper;

    @Autowired
    public SmartAIProvider(AIModelCaller aiModelCaller, 
                          CacheService cacheService,
                          QualityAssessmentService qualityAssessmentService,
                          EntityDisambiguationService entityDisambiguationService,
                          RelationValidationService relationValidationService,
                          EntityValidationService entityValidationService,
                          LongTextProcessor longTextProcessor) {
        this.aiModelCaller = aiModelCaller;
        this.cacheService = cacheService;
        this.qualityAssessmentService = qualityAssessmentService;
        this.entityDisambiguationService = entityDisambiguationService;
        this.relationValidationService = relationValidationService;
        this.entityValidationService = entityValidationService;
        this.longTextProcessor = longTextProcessor;
        this.cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
        this.objectMapper = new ObjectMapper();
        
        log.info("🤖 SmartAIProvider初始化完成 - 支持智能策略选择和知识图谱增强");
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
                    result = aiModelCaller.callAI(text, extractType);
                }

                // 验证AI响应是否有效，无效则抛出异常
                if (isValidResponse(result)) {
                    log.info("✅ AI提取成功，文本长度: {}，处理模式: {}",
                            textLength, textLength > LONG_TEXT_THRESHOLD ? "分批处理" : "直接处理");
                    
                    // 企业级知识图谱处理：消歧义 + 关系验证 + 融合
                    try {
                        String enhancedResult = performKnowledgeGraphEnhancement(result, extractType, text);
                        log.info("🔗 知识图谱增强处理完成");
                        return enhancedResult;
                    } catch (Exception e) {
                        log.warn("⚠️ 知识图谱增强处理失败，返回原始结果: {}", e.getMessage());
                        return result; // 降级处理，返回原始AI结果
                    }
                } else {
                    String errorMsg = String.format("AI提取失败，类型: %s，文本长度: %d", extractType, textLength);
                    log.error("❌ {}", errorMsg);

                    // 检查是否是AI服务错误响应
                    if (result != null && result.contains("\"error\"")) {
                        // 直接抛出AI服务的错误，不再包装
                        throw new RuntimeException("AI服务返回错误: " + extractErrorFromResponse(result));
                    } else {
                        throw new RuntimeException(errorMsg + "。请检查AI服务状态或稍后重试。");
                    }
                }

            } catch (RuntimeException e) {
                // 对于RuntimeException，直接重新抛出，避免重复包装
                log.error("💥 AI提取过程异常: {}", e.getMessage());
                throw e;
            } catch (Exception e) {
                // 对于其他异常，包装为RuntimeException
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
                    "ai_caller_stats", aiModelCaller.getCallStats(),
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

    /**
     * 企业级知识图谱增强处理 - v5.0核心功能
     * 整合实体消歧义、关系验证、知识融合
     */
    private String performKnowledgeGraphEnhancement(String aiResult, String extractType, String originalText) {
        try {
            log.info("🔗 开始知识图谱增强处理 - 类型: {}", extractType);
            
            // 1. 解析AI提取结果
            JsonNode aiResultNode = objectMapper.readTree(aiResult);
            
            // 2. 实体消歧义处理
            JsonNode disambiguatedEntities = null;
            if (aiResultNode.has("entities")) {
                disambiguatedEntities = performEntityDisambiguation(aiResultNode.get("entities"), originalText);
                log.debug("✅ 实体消歧义完成，处理{}个实体", disambiguatedEntities.size());
            }
            
            // 3. 关系验证和融合
            JsonNode validatedRelations = null;
            if (aiResultNode.has("relations")) {
                validatedRelations = relationValidationService.validateAndFuseRelations(
                        aiResultNode.get("relations"), extractType);
                log.debug("✅ 关系验证融合完成，处理{}个关系", validatedRelations.size());
            }
            
            // 4. 三元组处理（如果存在）
            JsonNode processedTriples = null;
            if (aiResultNode.has("triples")) {
                processedTriples = relationValidationService.validateAndFuseRelations(
                        aiResultNode.get("triples"), extractType);
                log.debug("✅ 三元组验证融合完成，处理{}个三元组", processedTriples.size());
            }
            
            // 5. 构建增强结果
            return buildEnhancedResult(aiResultNode, disambiguatedEntities, validatedRelations, processedTriples);
            
        } catch (Exception e) {
            log.error("❌ 知识图谱增强处理失败: {}", e.getMessage(), e);
            throw new RuntimeException("知识图谱增强处理失败: " + e.getMessage());
        }
    }

    /**
     * 执行实体消歧义
     */
    private JsonNode performEntityDisambiguation(JsonNode entities, String context) {
        try {
            // 检查是否启用消歧义
            JsonNode disambiguationSettings = entityValidationService.getDisambiguationSettings();
            if (disambiguationSettings == null) {
                log.debug("⚠️ 消歧义配置未找到，跳过消歧义处理");
                return entities;
            }

            return entityDisambiguationService.disambiguateEntities(entities, context);
            
        } catch (Exception e) {
            log.warn("⚠️ 实体消歧义处理失败，返回原始实体: {}", e.getMessage());
            return entities;
        }
    }

    /**
     * 构建增强后的结果
     */
    private String buildEnhancedResult(JsonNode originalResult, JsonNode entities, 
                                      JsonNode relations, JsonNode triples) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode enhancedResult = 
                    (com.fasterxml.jackson.databind.node.ObjectNode) originalResult.deepCopy();
            
            // 更新实体
            if (entities != null) {
                enhancedResult.set("entities", entities);
            }
            
            // 更新关系
            if (relations != null) {
                enhancedResult.set("relations", relations);
            }
            
            // 更新三元组
            if (triples != null) {
                enhancedResult.set("triples", triples);
            }
            
            // 添加增强处理元数据
            enhancedResult.put("enhanced", true);
            enhancedResult.put("enhancement_timestamp", System.currentTimeMillis());
            enhancedResult.put("enhancement_version", "v5.0");
            
            return objectMapper.writeValueAsString(enhancedResult);
            
        } catch (Exception e) {
            log.error("❌ 构建增强结果失败: {}", e.getMessage());
            throw new RuntimeException("构建增强结果失败: " + e.getMessage());
        }
    }

    /**
     * 从错误响应中提取错误信息
     */
    private String extractErrorFromResponse(String response) {
        try {
            if (response.contains("\"error\":")) {
                int errorStart = response.indexOf("\"error\":\"") + 9;
                int errorEnd = response.indexOf("\"", errorStart);
                if (errorStart > 8 && errorEnd > errorStart) {
                    return response.substring(errorStart, errorEnd);
                }
            }
            return "未知错误";
        } catch (Exception e) {
            return "解析错误信息失败";
        }
    }
}
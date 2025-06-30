package com.datacenter.extract.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识图谱引擎 - v3.0核心组件
 * 
 * 采用责任链模式和策略模式，优雅处理知识图谱增强功能
 * 设计理念：高内聚、低耦合、职责单一
 */
@Component
public class KnowledgeGraphEngine {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphEngine.class);

    private final EntityDisambiguator entityDisambiguator;
    private final KnowledgeFusion knowledgeFusion;
    private final RelationValidator relationValidator;
    private final QualityAssessor qualityAssessor;

    @Autowired
    public KnowledgeGraphEngine(
            EntityDisambiguator entityDisambiguator,
            KnowledgeFusion knowledgeFusion,
            RelationValidator relationValidator,
            QualityAssessor qualityAssessor) {
        this.entityDisambiguator = entityDisambiguator;
        this.knowledgeFusion = knowledgeFusion;
        this.relationValidator = relationValidator;
        this.qualityAssessor = qualityAssessor;
        log.info("KnowledgeGraphEngine initialized with all processors");
    }

    /**
     * 知识增强处理 - 策略模式实现
     * 
     * @param aiResult AI原始结果
     * @param kgMode   处理模式 (standard/enhanced/fusion)
     * @return 增强后的知识结果
     */
    public String enhanceKnowledge(String aiResult, String kgMode) {
        try {
            log.info("开始知识图谱增强处理，模式: {}", kgMode);

            Map<String, Object> data = parseExtractionResult(aiResult);

            // 使用策略模式选择处理链
            KnowledgeProcessingStrategy strategy = createStrategy(kgMode);
            Map<String, Object> enhancedData = strategy.process(data);

            String result = JSON.toJSONString(enhancedData);
            log.info("知识图谱增强处理完成，模式: {}", kgMode);

            return result;

        } catch (Exception e) {
            log.error("知识图谱增强处理失败，模式: {}, 错误: {}", kgMode, e.getMessage(), e);
            return aiResult; // 失败时返回原始结果，确保系统鲁棒性
        }
    }

    /**
     * 创建处理策略 - 策略模式
     */
    private KnowledgeProcessingStrategy createStrategy(String kgMode) {
        return switch (kgMode.toLowerCase()) {
            case "enhanced" -> new EnhancedStrategy();
            case "fusion" -> new FusionStrategy();
            case "standard" -> new StandardStrategy();
            default -> {
                log.warn("未知的KG模式: {}, 使用标准模式", kgMode);
                yield new StandardStrategy();
            }
        };
    }

    /**
     * 解析AI提取结果
     */
    private Map<String, Object> parseExtractionResult(String aiResult) {
        try {
            if (aiResult.startsWith("{") && aiResult.endsWith("}")) {
                return JSON.parseObject(aiResult);
            } else {
                // 如果不是JSON格式，包装成标准格式
                JSONObject wrapper = new JSONObject();
                wrapper.put("raw_result", aiResult);
                wrapper.put("format", "text");
                return wrapper;
            }
        } catch (Exception e) {
            log.warn("解析AI结果失败，使用原始格式: {}", e.getMessage());
            JSONObject wrapper = new JSONObject();
            wrapper.put("raw_result", aiResult);
            wrapper.put("format", "text");
            wrapper.put("parse_error", e.getMessage());
            return wrapper;
        }
    }

    /**
     * 知识处理策略接口
     */
    private interface KnowledgeProcessingStrategy {
        Map<String, Object> process(Map<String, Object> data);
    }

    /**
     * 标准策略 - 无额外处理，保持向后兼容
     */
    private class StandardStrategy implements KnowledgeProcessingStrategy {
        @Override
        public Map<String, Object> process(Map<String, Object> data) {
            data.put("kg_mode", "standard");
            data.put("processed_at", System.currentTimeMillis());
            return data;
        }
    }

    /**
     * 增强策略 - 实体消歧义 + 关系验证
     */
    private class EnhancedStrategy implements KnowledgeProcessingStrategy {
        @Override
        public Map<String, Object> process(Map<String, Object> data) {
            try {
                // 责任链处理
                data = entityDisambiguator.disambiguate(data);
                data = relationValidator.validate(data);

                data.put("kg_mode", "enhanced");
                data.put("processed_at", System.currentTimeMillis());
                data.put("enhancement_applied", true);

                return data;
            } catch (Exception e) {
                log.error("增强策略处理失败: {}", e.getMessage(), e);
                data.put("enhancement_error", e.getMessage());
                return data;
            }
        }
    }

    /**
     * 融合策略 - 全功能知识图谱处理
     */
    private class FusionStrategy implements KnowledgeProcessingStrategy {
        @Override
        public Map<String, Object> process(Map<String, Object> data) {
            try {
                // 完整的责任链处理
                data = entityDisambiguator.disambiguate(data);
                data = knowledgeFusion.fuseKnowledge(data);
                data = relationValidator.validate(data);
                data = qualityAssessor.assess(data);

                data.put("kg_mode", "fusion");
                data.put("processed_at", System.currentTimeMillis());
                data.put("full_processing_applied", true);

                return data;
            } catch (Exception e) {
                log.error("融合策略处理失败: {}", e.getMessage(), e);
                data.put("fusion_error", e.getMessage());
                return data;
            }
        }
    }
}
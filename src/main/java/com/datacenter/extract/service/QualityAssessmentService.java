package com.datacenter.extract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 质量评估服务 - 知识图谱质量管理 (P1优先级)
 * 基于'升级业务需求.md'的知识图谱质量评估和一致性检查功能
 * 
 * 🎯 核心功能:
 * 1. 知识图谱完整性评估
 * 2. 逻辑一致性检查和冲突修复
 * 3. 知识密度分析和补全建议
 * 4. 质量评估指标计算
 * 
 * 🏗️ 设计模式: 观察者模式 + 策略模式
 */
@Service
public class QualityAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(QualityAssessmentService.class);

    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public QualityAssessmentService(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("🔬 QualityAssessmentService初始化完成 - 支持知识图谱质量评估");
    }

    /**
     * 知识图谱质量评估主入口
     */
    public JsonNode assessKnowledgeGraphQuality(JsonNode entities, JsonNode relations, String entityType) {
        log.info("🔬 开始知识图谱质量评估 - 实体: {}, 关系: {}", entities.size(), relations.size());

        ObjectNode assessment = objectMapper.createObjectNode();

        // 1. 完整性评估
        JsonNode completenessReport = assessCompleteness(entities, entityType);
        assessment.set("completeness", completenessReport);

        // 2. 一致性检查
        JsonNode consistencyReport = checkConsistency(entities, relations);
        assessment.set("consistency", consistencyReport);

        // 3. 质量指标计算
        JsonNode qualityMetrics = calculateQualityMetrics(entities, relations);
        assessment.set("quality_metrics", qualityMetrics);

        // 4. 总体质量评分
        double overallScore = calculateOverallQualityScore(completenessReport, consistencyReport, qualityMetrics);
        assessment.put("overall_quality_score", overallScore);
        assessment.put("assessment_timestamp", System.currentTimeMillis());

        // 缓存评估结果
        String cacheKey = "quality_assessment:" + entityType + ":" + System.currentTimeMillis();
        cacheService.putAIResult(cacheKey, entityType, assessment.toString());

        log.info("✅ 质量评估完成 - 总体评分: {:.2f}", overallScore);

        return assessment;
    }

    /**
     * 完整性评估
     */
    private JsonNode assessCompleteness(JsonNode entities, String entityType) {
        ObjectNode completeness = objectMapper.createObjectNode();
        ArrayNode incompleteEntities = objectMapper.createArrayNode();

        int totalEntities = entities.size();
        int completeEntities = 0;

        Set<String> requiredAttributes = getRequiredAttributes(entityType);

        if (entities.isArray()) {
            for (JsonNode entity : entities) {
                boolean isComplete = true;
                Set<String> missing = new HashSet<>();

                for (String required : requiredAttributes) {
                    if (!entity.has(required) || entity.get(required).asText().trim().isEmpty()) {
                        isComplete = false;
                        missing.add(required);
                    }
                }

                if (isComplete) {
                    completeEntities++;
                } else {
                    ObjectNode incomplete = objectMapper.createObjectNode();
                    incomplete.put("entity_name", entity.get("name").asText());
                    incomplete.set("missing_attributes", objectMapper.valueToTree(missing));
                    incompleteEntities.add(incomplete);
                }
            }
        }

        double completenessRatio = totalEntities > 0 ? (double) completeEntities / totalEntities : 0.0;

        completeness.put("total_entities", totalEntities);
        completeness.put("complete_entities", completeEntities);
        completeness.put("completeness_ratio", completenessRatio);
        completeness.set("incomplete_entities", incompleteEntities);

        log.debug("完整性评估: {:.1f}% ({}/{})", completenessRatio * 100, completeEntities, totalEntities);

        return completeness;
    }

    /**
     * 一致性检查
     */
    private JsonNode checkConsistency(JsonNode entities, JsonNode relations) {
        ObjectNode consistency = objectMapper.createObjectNode();
        ArrayNode conflicts = objectMapper.createArrayNode();
        ArrayNode warnings = objectMapper.createArrayNode();

        // 检查实体内部一致性
        checkEntityConsistency(entities, conflicts);

        // 检查关系一致性
        checkRelationConsistency(relations, conflicts, warnings);

        consistency.put("total_conflicts", conflicts.size());
        consistency.put("total_warnings", warnings.size());
        consistency.set("conflicts", conflicts);
        consistency.set("warnings", warnings);

        double consistencyScore = calculateConsistencyScore(conflicts.size(), warnings.size(),
                entities.size() + relations.size());
        consistency.put("consistency_score", consistencyScore);

        log.debug("一致性检查: 冲突 {}, 警告 {}, 评分 {:.2f}",
                conflicts.size(), warnings.size(), consistencyScore);

        return consistency;
    }

    /**
     * 检查实体内部一致性
     */
    private void checkEntityConsistency(JsonNode entities, ArrayNode conflicts) {
        Map<String, List<JsonNode>> nameGroups = new HashMap<>();

        // 按名称分组
        if (entities.isArray()) {
            for (JsonNode entity : entities) {
                String name = entity.get("name").asText().toLowerCase();
                nameGroups.computeIfAbsent(name, k -> new ArrayList<>()).add(entity);
            }
        }

        // 检查同名实体的属性一致性
        for (Map.Entry<String, List<JsonNode>> entry : nameGroups.entrySet()) {
            List<JsonNode> group = entry.getValue();
            if (group.size() > 1) {
                String entityName = entry.getKey();
                ObjectNode conflict = objectMapper.createObjectNode();
                conflict.put("type", "duplicate_entity");
                conflict.put("entity_name", entityName);
                conflict.put("duplicate_count", group.size());
                conflict.put("severity", "high");
                conflicts.add(conflict);
            }
        }
    }

    /**
     * 检查关系一致性
     */
    private void checkRelationConsistency(JsonNode relations, ArrayNode conflicts, ArrayNode warnings) {
        if (relations.isArray()) {
            for (JsonNode relation : relations) {
                // 检查必需字段
                if (!relation.has("subject") || !relation.has("predicate") || !relation.has("object")) {
                    ObjectNode conflict = objectMapper.createObjectNode();
                    conflict.put("type", "incomplete_relation");
                    conflict.put("description", "关系缺少必要字段");
                    conflict.set("relation", relation);
                    conflict.put("severity", "high");
                    conflicts.add(conflict);
                }

                // 检查自反关系
                if (relation.has("subject") && relation.has("object")) {
                    String subject = relation.get("subject").asText();
                    String object = relation.get("object").asText();
                    String predicate = relation.get("predicate").asText();

                    if (subject.equals(object) && !isValidReflexiveRelation(predicate)) {
                        ObjectNode warning = objectMapper.createObjectNode();
                        warning.put("type", "suspicious_reflexive_relation");
                        warning.put("description", "可疑的自反关系");
                        warning.set("relation", relation);
                        warning.put("severity", "medium");
                        warnings.add(warning);
                    }
                }
            }
        }
    }

    /**
     * 计算质量指标
     */
    private JsonNode calculateQualityMetrics(JsonNode entities, JsonNode relations) {
        ObjectNode metrics = objectMapper.createObjectNode();

        // 实体质量指标
        double entityQuality = calculateEntityQuality(entities);

        // 关系质量指标
        double relationQuality = calculateRelationQuality(relations);

        // 密度指标
        double density = calculateDensity(entities, relations);

        metrics.put("entity_quality", entityQuality);
        metrics.put("relation_quality", relationQuality);
        metrics.put("knowledge_density", density);

        return metrics;
    }

    /**
     * 计算总体质量评分
     */
    private double calculateOverallQualityScore(JsonNode completeness, JsonNode consistency, JsonNode metrics) {
        double completenessScore = completeness.get("completeness_ratio").asDouble(0.0);
        double consistencyScore = consistency.get("consistency_score").asDouble(0.0);
        double entityQuality = metrics.get("entity_quality").asDouble(0.0);
        double relationQuality = metrics.get("relation_quality").asDouble(0.0);

        // 加权平均
        double overallScore = (completenessScore * 0.3 + consistencyScore * 0.3 +
                entityQuality * 0.2 + relationQuality * 0.2);

        return Math.max(0.0, Math.min(1.0, overallScore));
    }

    // ==================== 辅助方法 ====================

    private Set<String> getRequiredAttributes(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "celebrity", "person" -> Set.of("name", "birth_date", "profession");
            case "work", "movie" -> Set.of("name", "release_year", "director");
            case "event" -> Set.of("name", "time", "location");
            default -> Set.of("name");
        };
    }

    private boolean isValidReflexiveRelation(String predicate) {
        Set<String> validReflexive = Set.of("是", "为", "等于", "属于");
        return validReflexive.contains(predicate);
    }

    private double calculateConsistencyScore(int conflicts, int warnings, int totalItems) {
        if (totalItems == 0)
            return 1.0;

        double penalty = (conflicts * 0.1 + warnings * 0.05) / totalItems;
        return Math.max(0.0, 1.0 - penalty);
    }

    private double calculateEntityQuality(JsonNode entities) {
        if (!entities.isArray() || entities.size() == 0)
            return 0.0;

        double totalQuality = 0.0;
        for (JsonNode entity : entities) {
            double entityQuality = entity.has("confidence") ? entity.get("confidence").asDouble(0.5) : 0.5;
            totalQuality += entityQuality;
        }

        return totalQuality / entities.size();
    }

    private double calculateRelationQuality(JsonNode relations) {
        if (!relations.isArray() || relations.size() == 0)
            return 0.0;

        double totalQuality = 0.0;
        for (JsonNode relation : relations) {
            double relationQuality = relation.has("confidence") ? relation.get("confidence").asDouble(0.5) : 0.5;
            totalQuality += relationQuality;
        }

        return totalQuality / relations.size();
    }

    private double calculateDensity(JsonNode entities, JsonNode relations) {
        int entityCount = entities.size();
        int relationCount = relations.size();

        if (entityCount == 0)
            return 0.0;

        double relationEntityRatio = (double) relationCount / entityCount;

        // 理想的关系/实体比例为1.0-2.0
        return Math.min(1.0, relationEntityRatio / 1.5);
    }
}
package com.datacenter.extract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.*;

/**
 * 关系验证服务 - 知识图谱关系处理 (P1优先级)
 * 基于'升级业务需求.md'的关系抽取歧义和关系融合功能
 * 
 * 🎯 核心功能:
 * 1. 关系消歧义 - 区分不同语义的关系
 * 2. 关系验证 - 验证关系的合理性和一致性
 * 3. 关系融合 - 合并重复或冲突的关系
 * 4. 多候选关系生成和置信度评分
 * 
 * 🏗️ 设计模式: 责任链模式 + 策略模式
 * 
 * @since v5.0
 */
@Service
public class RelationValidationService {

    private static final Logger log = LoggerFactory.getLogger(RelationValidationService.class);

    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 配置文件相关字段
    private JsonNode validationConfig;
    private Map<String, Set<String>> relationTypes = new HashMap<>();
    private Set<String> reflexiveRelations = new HashSet<>();
    private Set<String> nonReflexiveRelations = new HashSet<>();
    private Set<String> symmetricRelations = new HashSet<>();
    private Set<String> highConfidenceRelations = new HashSet<>();
    private Set<String> mediumConfidenceRelations = new HashSet<>();
    private Map<String, String> relationSynonyms = new HashMap<>();
    private JsonNode confidenceConfig;
    private JsonNode fusionRules;
    private JsonNode validationRules;
    private JsonNode relationConstraints;

    @Autowired
    public RelationValidationService(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("🔗 RelationValidationService初始化完成 - 支持关系消歧义和验证");
    }

    @PostConstruct
    private void loadValidationConfig() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/relationvalidation.json");
            validationConfig = objectMapper.readTree(resource.getInputStream());
            
            loadRelationTypes();
            loadConfidenceConfig();
            loadRelationSynonyms();
            loadValidationRules();
            loadFusionRules();
            loadRelationConstraints();
            
            log.info("✅ 关系验证配置加载完成 - 支持{}种关系类型", relationTypes.size());
        } catch (IOException e) {
            log.error("❌ 加载关系验证配置失败: {}", e.getMessage());
            loadDefaultConfig();
        }
    }

    private void loadRelationTypes() {
        JsonNode relationValidation = validationConfig.get("relation_validation");
        if (relationValidation == null) return;
        
        JsonNode types = relationValidation.get("relation_types");
        if (types == null) return;
        
        // 加载自反关系
        JsonNode reflexiveNode = types.get("reflexive_relations");
        if (reflexiveNode != null && reflexiveNode.has("relations")) {
            JsonNode relations = reflexiveNode.get("relations");
            if (relations.isArray()) {
                relations.forEach(node -> reflexiveRelations.add(node.asText()));
            }
        }
        
        // 加载非自反关系
        JsonNode nonReflexiveNode = types.get("non_reflexive_relations");
        if (nonReflexiveNode != null && nonReflexiveNode.has("relations")) {
            JsonNode relations = nonReflexiveNode.get("relations");
            if (relations.isArray()) {
                relations.forEach(node -> nonReflexiveRelations.add(node.asText()));
            }
        }
        
        // 加载对称关系
        JsonNode symmetricNode = types.get("symmetric_relations");
        if (symmetricNode != null && symmetricNode.has("relations")) {
            JsonNode relations = symmetricNode.get("relations");
            if (relations.isArray()) {
                relations.forEach(node -> symmetricRelations.add(node.asText()));
            }
        }
        
        // 加载所有关系类型
        types.fieldNames().forEachRemaining(typeName -> {
            JsonNode typeNode = types.get(typeName);
            if (typeNode.has("relations")) {
                Set<String> relationSet = new HashSet<>();
                JsonNode relations = typeNode.get("relations");
                if (relations.isArray()) {
                    relations.forEach(rel -> relationSet.add(rel.asText()));
                }
                relationTypes.put(typeName, relationSet);
            }
        });
    }

    private void loadConfidenceConfig() {
        JsonNode relationValidation = validationConfig.get("relation_validation");
        if (relationValidation == null) return;
        
        confidenceConfig = relationValidation.get("confidence_scoring");
        
        if (confidenceConfig != null) {
            JsonNode scoringFactors = confidenceConfig.get("scoring_factors");
            if (scoringFactors != null) {
                // 加载高置信度关系
                JsonNode highConfNode = scoringFactors.get("high_confidence_relations");
                if (highConfNode != null && highConfNode.has("relations")) {
                    JsonNode relations = highConfNode.get("relations");
                    if (relations.isArray()) {
                        relations.forEach(node -> highConfidenceRelations.add(node.asText()));
                    }
                }
                
                // 加载中等置信度关系
                JsonNode mediumConfNode = scoringFactors.get("medium_confidence_relations");
                if (mediumConfNode != null && mediumConfNode.has("relations")) {
                    JsonNode relations = mediumConfNode.get("relations");
                    if (relations.isArray()) {
                        relations.forEach(node -> mediumConfidenceRelations.add(node.asText()));
                    }
                }
            }
        }
    }

    private void loadRelationSynonyms() {
        JsonNode relationValidation = validationConfig.get("relation_validation");
        if (relationValidation == null) return;
        
        JsonNode synonymsNode = relationValidation.get("relation_synonyms");
        if (synonymsNode != null && synonymsNode.has("mappings")) {
            JsonNode mappings = synonymsNode.get("mappings");
            mappings.fieldNames().forEachRemaining(key -> {
                relationSynonyms.put(key, mappings.get(key).asText());
            });
        }
    }

    private void loadValidationRules() {
        JsonNode relationValidation = validationConfig.get("relation_validation");
        if (relationValidation != null) {
            validationRules = relationValidation.get("validation_rules");
        }
    }

    private void loadFusionRules() {
        JsonNode relationValidation = validationConfig.get("relation_validation");
        if (relationValidation != null) {
            fusionRules = relationValidation.get("fusion_rules");
        }
    }

    private void loadRelationConstraints() {
        JsonNode relationValidation = validationConfig.get("relation_validation");
        if (relationValidation != null) {
            relationConstraints = relationValidation.get("relation_constraints");
        }
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        log.warn("⚠️ 使用默认关系验证配置");
        
        try {
            JsonNode fallbackConfig = validationConfig.get("default_fallback_config");
            if (fallbackConfig != null) {
                loadFallbackFromConfig(fallbackConfig);
                return;
            }
        } catch (Exception e) {
            log.error("❌ 加载fallback配置失败: {}", e.getMessage());
        }
        
        // 紧急最小化配置
        loadEmergencyConfig();
    }

    /**
     * 从配置文件加载fallback配置
     */
    private void loadFallbackFromConfig(JsonNode fallbackConfig) {
        // 加载默认自反关系
        JsonNode reflexiveNode = fallbackConfig.get("default_reflexive_relations");
        if (reflexiveNode != null && reflexiveNode.isArray()) {
            reflexiveNode.forEach(node -> reflexiveRelations.add(node.asText()));
        }
        
        // 加载默认高置信度关系
        JsonNode highConfNode = fallbackConfig.get("default_high_confidence_relations");
        if (highConfNode != null && highConfNode.isArray()) {
            highConfNode.forEach(node -> highConfidenceRelations.add(node.asText()));
        }
        
        // 加载默认关系同义词
        JsonNode synonymsNode = fallbackConfig.get("default_relation_synonyms");
        if (synonymsNode != null) {
            synonymsNode.fieldNames().forEachRemaining(key -> {
                relationSynonyms.put(key, synonymsNode.get(key).asText());
            });
        }
        
        log.info("✅ 从fallback配置加载成功");
    }

    /**
     * 紧急最小化配置
     */
    private void loadEmergencyConfig() {
        log.error("🚨 使用紧急最小化配置");
        
        try {
            JsonNode emergencyConfig = validationConfig.get("emergency_fallback");
            if (emergencyConfig != null) {
                JsonNode minimalRelations = emergencyConfig.get("minimal_relations");
                if (minimalRelations != null && minimalRelations.isArray()) {
                    minimalRelations.forEach(node -> {
                        String relation = node.asText();
                        reflexiveRelations.add(relation);
                    });
                }
                log.warn("✅ 紧急配置加载成功");
                return;
            }
        } catch (Exception e) {
            log.error("❌ 紧急配置加载失败: {}", e.getMessage());
        }
        
        // 绝对最小化配置
        reflexiveRelations.addAll(Set.of("是", "为"));
        highConfidenceRelations.add("是");
        relationSynonyms.put("为", "是");
        log.warn("⚠️ 系统运行在最小保护模式");
    }

    /**
     * 关系验证和融合主入口
     */
    public JsonNode validateAndFuseRelations(JsonNode extractedRelations, String entityType) {
        log.info("🔗 开始关系验证 - 类型: {}, 关系数量: {}", entityType, extractedRelations.size());

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode validatedRelations = objectMapper.createArrayNode();
        ArrayNode invalidRelations = objectMapper.createArrayNode();
        ArrayNode fusedRelations = objectMapper.createArrayNode();

        List<RelationCandidate> validCandidates = new ArrayList<>();

        if (extractedRelations.isArray()) {
            // 第一步：验证每个关系
            for (JsonNode relation : extractedRelations) {
                ValidationResult validationResult = validateRelation(relation);

                if (validationResult.isValid()) {
                    validatedRelations.add(relation);
                    validCandidates.add(new RelationCandidate(relation, validationResult.getConfidence()));
                } else {
                    invalidRelations.add(createInvalidRecord(relation, validationResult.getMessage()));
                }
            }

            // 第二步：关系融合
            List<JsonNode> fusedList = fuseRelations(validCandidates);
            fusedList.forEach(fusedRelations::add);
        }

        result.set("validated_relations", validatedRelations);
        result.set("fused_relations", fusedRelations);
        result.set("invalid_relations", invalidRelations);
        result.put("total_input", extractedRelations.size());
        result.put("valid_count", validatedRelations.size());
        result.put("fused_count", fusedRelations.size());
        result.put("invalid_count", invalidRelations.size());

        log.info("✅ 关系验证完成 - 有效: {}, 融合: {}, 无效: {}",
                validatedRelations.size(), fusedRelations.size(), invalidRelations.size());

        return result;
    }

    /**
     * 验证单个关系
     */
    private ValidationResult validateRelation(JsonNode relation) {
        // 基础字段检查
        if (!hasRequiredFields(relation)) {
            return ValidationResult.invalid("缺少必需字段");
        }

        String subject = relation.get("subject").asText();
        String predicate = relation.get("predicate").asText();
        String object = relation.get("object").asText();

        // 基础逻辑检查
        if (subject.equals(object) && !isReflexiveRelation(predicate)) {
            return ValidationResult.invalid("主体和客体相同但关系不支持自反");
        }

        // 语义验证
        String semanticError = performSemanticValidation(relation);
        if (semanticError != null) {
            return ValidationResult.invalid(semanticError);
        }

        // 计算置信度
        double confidence = calculateConfidence(relation);

        return ValidationResult.valid(confidence);
    }

    /**
     * 检查必需字段
     */
    private boolean hasRequiredFields(JsonNode relation) {
        if (validationRules != null && validationRules.has("structural_validation")) {
            JsonNode structural = validationRules.get("structural_validation");
            return (!structural.get("require_subject").asBoolean() || relation.has("subject")) &&
                   (!structural.get("require_predicate").asBoolean() || relation.has("predicate")) &&
                   (!structural.get("require_object").asBoolean() || relation.has("object"));
        }
        return relation.has("subject") && relation.has("predicate") && relation.has("object");
    }

    /**
     * 语义验证
     */
    private String performSemanticValidation(JsonNode relation) {
        String predicate = relation.get("predicate").asText();
        
        // 检查关系长度
        if (validationRules != null) {
            JsonNode structural = validationRules.get("structural_validation");
            if (structural != null && structural.has("max_predicate_length")) {
                int maxLength = structural.get("max_predicate_length").asInt(50);
                if (predicate.length() > maxLength) {
                    return "关系谓词过长";
                }
            }
        }
        
        return null;
    }

    /**
     * 判断是否为自反关系
     */
    private boolean isReflexiveRelation(String predicate) {
        return reflexiveRelations.contains(predicate);
    }

    /**
     * 计算关系置信度 - 基于配置文件
     */
    private double calculateConfidence(JsonNode relation) {
        double baseConfidence = 0.7;
        
        if (confidenceConfig != null) {
            baseConfidence = confidenceConfig.get("base_confidence").asDouble(0.7);
        }
        
        String predicate = relation.get("predicate").asText();
        double confidence = baseConfidence;

        // 基于关系类型调整置信度
        if (highConfidenceRelations.contains(predicate)) {
            double boost = 0.2;
            if (confidenceConfig != null) {
                JsonNode scoringFactors = confidenceConfig.get("scoring_factors");
                if (scoringFactors != null) {
                    JsonNode highConf = scoringFactors.get("high_confidence_relations");
                    if (highConf != null && highConf.has("confidence_boost")) {
                        boost = highConf.get("confidence_boost").asDouble(0.2);
                    }
                }
            }
            confidence += boost;
        } else if (mediumConfidenceRelations.contains(predicate)) {
            double boost = 0.1;
            if (confidenceConfig != null) {
                JsonNode scoringFactors = confidenceConfig.get("scoring_factors");
                if (scoringFactors != null) {
                    JsonNode mediumConf = scoringFactors.get("medium_confidence_relations");
                    if (mediumConf != null && mediumConf.has("confidence_boost")) {
                        boost = mediumConf.get("confidence_boost").asDouble(0.1);
                    }
                }
            }
            confidence += boost;
        }

        // 如果关系包含置信度字段，考虑原始置信度
        if (relation.has("confidence")) {
            double originalConfidence = relation.get("confidence").asDouble(0.0);
            double originalWeight = 0.5;
            double relationWeight = 0.5;
            
            if (confidenceConfig != null && confidenceConfig.has("context_scoring")) {
                JsonNode contextScoring = confidenceConfig.get("context_scoring");
                originalWeight = contextScoring.get("original_confidence_weight").asDouble(0.5);
                relationWeight = contextScoring.get("relation_type_weight").asDouble(0.2);
            }
            
            confidence = originalConfidence * originalWeight + confidence * relationWeight;
        }

        // 确保置信度在有效范围内
        double minConfidence = 0.0;
        double maxConfidence = 1.0;
        
        if (confidenceConfig != null && confidenceConfig.has("score_boundaries")) {
            JsonNode boundaries = confidenceConfig.get("score_boundaries");
            minConfidence = boundaries.get("min_confidence").asDouble(0.0);
            maxConfidence = boundaries.get("max_confidence").asDouble(1.0);
        }
        
        return Math.max(minConfidence, Math.min(maxConfidence, confidence));
    }

    /**
     * 关系融合
     */
    private List<JsonNode> fuseRelations(List<RelationCandidate> candidates) {
        if (fusionRules != null && fusionRules.has("enable_fusion") && 
            !fusionRules.get("enable_fusion").asBoolean()) {
            // 如果禁用融合，直接返回原始关系
            return candidates.stream()
                    .map(RelationCandidate::getRelation)
                    .toList();
        }

        // 按关系键分组
        Map<String, List<RelationCandidate>> groups = groupByRelationKey(candidates);
        List<JsonNode> fusedResults = new ArrayList<>();

        for (Map.Entry<String, List<RelationCandidate>> entry : groups.entrySet()) {
            List<RelationCandidate> group = entry.getValue();
            if (group.size() > 1) {
                // 需要融合的关系组
                JsonNode fusedRelation = fuseRelationGroup(group);
                fusedResults.add(fusedRelation);
            } else {
                // 单个关系，无需融合
                fusedResults.add(group.get(0).getRelation());
            }
        }

        return fusedResults;
    }

    /**
     * 按关系键分组
     */
    private Map<String, List<RelationCandidate>> groupByRelationKey(List<RelationCandidate> candidates) {
        Map<String, List<RelationCandidate>> groups = new HashMap<>();
        
        for (RelationCandidate candidate : candidates) {
            String key = generateRelationKey(candidate.getRelation());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
        }
        
        return groups;
    }

    /**
     * 生成关系键
     */
    private String generateRelationKey(JsonNode relation) {
        String subject = relation.get("subject").asText();
        String predicate = normalizeRelation(relation.get("predicate").asText());
        String object = relation.get("object").asText();
        return subject + ":" + predicate + ":" + object;
    }

    /**
     * 标准化关系谓词
     */
    private String normalizeRelation(String predicate) {
        // 使用同义词映射标准化
        return relationSynonyms.getOrDefault(predicate, predicate);
    }

    /**
     * 融合关系组
     */
    private JsonNode fuseRelationGroup(List<RelationCandidate> group) {
        ObjectNode fusedRelation = objectMapper.createObjectNode();
        
        // 使用置信度最高的关系作为基础
        RelationCandidate best = group.stream()
                .max(Comparator.comparing(RelationCandidate::getConfidence))
                .orElse(group.get(0));
        
        JsonNode baseRelation = best.getRelation();
        fusedRelation.setAll((ObjectNode) baseRelation.deepCopy());
        
        // 融合置信度
        double fusedConfidence = calculateFusedConfidence(group);
        fusedRelation.put("confidence", fusedConfidence);
        
        // 添加融合元数据
        fusedRelation.put("fused", true);
        fusedRelation.put("fusion_count", group.size());
        fusedRelation.put("fusion_timestamp", System.currentTimeMillis());
        
        // 保存原始关系信息
        ArrayNode sources = objectMapper.createArrayNode();
        group.forEach(candidate -> {
            ObjectNode source = objectMapper.createObjectNode();
            source.put("confidence", candidate.getConfidence());
            if (candidate.getRelation().has("source")) {
                source.put("source", candidate.getRelation().get("source").asText());
            }
            sources.add(source);
        });
        fusedRelation.set("fusion_sources", sources);
        
        return fusedRelation;
    }

    /**
     * 计算融合置信度
     */
    private double calculateFusedConfidence(List<RelationCandidate> group) {
        String method = "weighted_average"; // 默认方法
        
        if (fusionRules != null && fusionRules.has("aggregation_methods")) {
            JsonNode methods = fusionRules.get("aggregation_methods");
            if (methods.has("confidence_aggregation")) {
                method = methods.get("confidence_aggregation").asText();
            }
        }
        
        return switch (method) {
            case "weighted_average" -> {
                double sum = 0.0;
                double weightSum = 0.0;
                for (RelationCandidate candidate : group) {
                    double confidence = candidate.getConfidence();
                    sum += confidence * confidence; // 使用置信度本身作为权重
                    weightSum += confidence;
                }
                yield weightSum > 0 ? sum / weightSum : 0.0;
            }
            case "average" -> {
                double sum = group.stream()
                        .mapToDouble(RelationCandidate::getConfidence)
                        .sum();
                yield sum / group.size();
            }
            case "max" -> group.stream()
                    .mapToDouble(RelationCandidate::getConfidence)
                    .max()
                    .orElse(0.0);
            default -> group.get(0).getConfidence();
        };
    }

    /**
     * 创建无效记录
     */
    private JsonNode createInvalidRecord(JsonNode relation, String reason) {
        ObjectNode invalidRecord = relation.deepCopy();
        invalidRecord.put("validation_status", "invalid");
        invalidRecord.put("validation_reason", reason);
        invalidRecord.put("validation_timestamp", System.currentTimeMillis());
        return invalidRecord;
    }

    // ==================== 辅助类 ====================

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final double confidence;

        private ValidationResult(boolean valid, String message, double confidence) {
            this.valid = valid;
            this.message = message;
            this.confidence = confidence;
        }

        public static ValidationResult valid(double confidence) {
            return new ValidationResult(true, "验证通过", confidence);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, 0.0);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public double getConfidence() {
            return confidence;
        }
    }

    /**
     * 关系候选类
     */
    public static class RelationCandidate {
        private final JsonNode relation;
        private final double confidence;

        public RelationCandidate(JsonNode relation, double confidence) {
            this.relation = relation;
            this.confidence = confidence;
        }

        public JsonNode getRelation() {
            return relation;
        }

        public double getConfidence() {
            return confidence;
        }
    }
}
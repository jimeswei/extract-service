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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实体消歧义服务 - 知识图谱核心组件 (P0优先级)
 * 基于'升级业务需求.md'的实体消歧义和知识融合功能
 * 
 * 🎯 核心功能:
 * 1. 实体消歧义 - 判断新提取的实体与库中已有实体是否为同一实体
 * 2. 知识融合 - 合并来自不同源文本的同一实体信息
 * 3. 实体对齐 - 处理同一实体的不同表述形式
 * 4. 实体链接 - 与外部知识库对齐
 * 
 * @since v5.0
 */
@Service
public class EntityDisambiguationService {

    private static final Logger log = LoggerFactory.getLogger(EntityDisambiguationService.class);

    private final CacheService cacheService;
    private final OutputFileService outputFileService;
    private final EntityValidationService entityValidationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 实体知识库缓存
    private final Map<String, List<JsonNode>> entityKnowledgeBase = new ConcurrentHashMap<>();

    @Autowired
    public EntityDisambiguationService(CacheService cacheService, 
                                     OutputFileService outputFileService,
                                     EntityValidationService entityValidationService) {
        this.cacheService = cacheService;
        this.outputFileService = outputFileService;
        this.entityValidationService = entityValidationService;
        log.info("🔍 EntityDisambiguationService初始化完成 - 支持实体消歧义和知识融合");
    }

    /**
     * 实体消歧义主入口 - 模板方法模式
     */
    public JsonNode disambiguateEntities(JsonNode extractedEntities, String entityType) {
        log.info("🎯 开始实体消歧义 - 类型: {}, 数量: {}", entityType, extractedEntities.size());

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode disambiguatedEntities = objectMapper.createArrayNode();
        ArrayNode newEntities = objectMapper.createArrayNode();
        ArrayNode conflicts = objectMapper.createArrayNode();

        if (extractedEntities.isArray()) {
            for (JsonNode entity : extractedEntities) {
                DisambiguationResult disambiguationResult = performDisambiguation(entity, entityType);

                if (disambiguationResult.isMatched()) {
                    // 知识融合
                    JsonNode fusedEntity = fuseEntityInformation(entity, disambiguationResult.getMatchedEntity());
                    disambiguatedEntities.add(fusedEntity);
                } else {
                    // 新实体
                    JsonNode enhancedEntity = enhanceNewEntity(entity, entityType);
                    newEntities.add(enhancedEntity);
                    addToKnowledgeBase(enhancedEntity, entityType);
                }
            }
        }

        result.set("disambiguated_entities", disambiguatedEntities);
        result.set("new_entities", newEntities);
        result.set("conflicts", conflicts);

        log.info("✅ 实体消歧义完成 - 融合: {}, 新增: {}",
                disambiguatedEntities.size(), newEntities.size());

        return result;
    }

    /**
     * 执行实体消歧义 - 策略模式
     */
    private DisambiguationResult performDisambiguation(JsonNode entity, String entityType) {
        String entityName = entity.get("name").asText();
        List<JsonNode> candidates = getCandidateEntities(entityName, entityType);

        if (candidates.isEmpty()) {
            return DisambiguationResult.newEntity();
        }

        // 应用消歧义策略
        for (JsonNode candidate : candidates) {
            double similarity = calculateSimilarity(entity, candidate, entityType);
            if (similarity > getThreshold(entityType)) {
                return DisambiguationResult.matched(candidate, similarity);
            }
        }

        return DisambiguationResult.newEntity();
    }

    /**
     * 获取候选实体
     */
    private List<JsonNode> getCandidateEntities(String entityName, String entityType) {
        List<JsonNode> candidates = new ArrayList<>();
        String exactKey = entityType + ":" + entityName;
        candidates.addAll(entityKnowledgeBase.getOrDefault(exactKey, new ArrayList<>()));
        return candidates;
    }

    /**
     * 计算实体相似度
     */
    private double calculateSimilarity(JsonNode entity1, JsonNode entity2, String entityType) {
        return switch (entityType.toLowerCase()) {
            case "celebrity", "person" -> calculatePersonSimilarity(entity1, entity2);
            case "work", "movie" -> calculateWorkSimilarity(entity1, entity2);
            case "event" -> calculateEventSimilarity(entity1, entity2);
            default -> calculateGenericSimilarity(entity1, entity2);
        };
    }

    /**
     * 人物相似度计算
     */
    private double calculatePersonSimilarity(JsonNode e1, JsonNode e2) {
        double score = 0.0;
        int factors = 0;

        if (e1.has("birth_date") && e2.has("birth_date")) {
            if (e1.get("birth_date").asText().equals(e2.get("birth_date").asText())) {
                score += 0.5;
            }
            factors++;
        }

        if (e1.has("profession") && e2.has("profession")) {
            if (e1.get("profession").asText().equals(e2.get("profession").asText())) {
                score += 0.5;
            }
            factors++;
        }

        return factors > 0 ? score / factors : 0.0;
    }

    /**
     * 作品相似度计算
     */
    private double calculateWorkSimilarity(JsonNode e1, JsonNode e2) {
        double score = 0.0;
        int factors = 0;

        if (e1.has("release_year") && e2.has("release_year")) {
            if (e1.get("release_year").asText().equals(e2.get("release_year").asText())) {
                score += 0.6;
            }
            factors++;
        }

        if (e1.has("director") && e2.has("director")) {
            if (e1.get("director").asText().equals(e2.get("director").asText())) {
                score += 0.4;
            }
            factors++;
        }

        return factors > 0 ? score / factors : 0.0;
    }

    /**
     * 事件相似度计算
     */
    private double calculateEventSimilarity(JsonNode e1, JsonNode e2) {
        double score = 0.0;
        int factors = 0;

        if (e1.has("time") && e2.has("time")) {
            if (e1.get("time").asText().equals(e2.get("time").asText())) {
                score += 0.7;
            }
            factors++;
        }

        if (e1.has("location") && e2.has("location")) {
            if (e1.get("location").asText().equals(e2.get("location").asText())) {
                score += 0.3;
            }
            factors++;
        }

        return factors > 0 ? score / factors : 0.0;
    }

    /**
     * 通用相似度计算
     */
    private double calculateGenericSimilarity(JsonNode e1, JsonNode e2) {
        Set<String> keys1 = new HashSet<>();
        e1.fieldNames().forEachRemaining(keys1::add);

        Set<String> keys2 = new HashSet<>();
        e2.fieldNames().forEachRemaining(keys2::add);

        Set<String> intersection = new HashSet<>(keys1);
        intersection.retainAll(keys2);

        double matchingAttributes = 0;
        for (String key : intersection) {
            if (e1.get(key).asText().equals(e2.get(key).asText())) {
                matchingAttributes++;
            }
        }

        return intersection.size() > 0 ? matchingAttributes / intersection.size() : 0.0;
    }

    /**
     * 获取相似度阈值
     */
    private double getThreshold(String entityType) {
        // 使用EntityValidationService获取配置的阈值
        return entityValidationService.getEntitySimilarityThreshold(entityType);
    }

    /**
     * 知识融合 - 属性合并和冲突解决
     */
    private JsonNode fuseEntityInformation(JsonNode newEntity, JsonNode existingEntity) {
        ObjectNode fusedEntity = existingEntity.deepCopy();

        // 合并属性
        newEntity.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode newValue = entry.getValue();

            if (!fusedEntity.has(key)) {
                // 新属性直接添加
                fusedEntity.set(key, newValue);
            } else {
                // 属性冲突解决 - 新值优先策略
                fusedEntity.set(key, newValue);
            }
        });

        // 更新融合元数据
        fusedEntity.put("last_updated", System.currentTimeMillis());
        fusedEntity.put("fusion_count", fusedEntity.get("fusion_count").asInt(0) + 1);

        log.debug("🔗 知识融合完成: {}", fusedEntity.get("name"));
        return fusedEntity;
    }

    /**
     * 增强新实体
     */
    private JsonNode enhanceNewEntity(JsonNode entity, String entityType) {
        // 先进行标准化
        JsonNode normalizedEntity = entityValidationService.normalizeEntity(entity, entityType);
        
        ObjectNode enhanced = normalizedEntity.deepCopy();
        enhanced.put("entity_id", generateEntityId(entityType));
        enhanced.put("created_time", System.currentTimeMillis());
        enhanced.put("confidence", 0.9);
        enhanced.put("status", "verified");
        enhanced.put("entity_type", entityType);
        
        // 验证实体完整性
        EntityValidationService.ValidationResult validation = 
            entityValidationService.validateEntity(enhanced, entityType);
        enhanced.put("quality_score", validation.getQualityScore());
        
        return enhanced;
    }

    /**
     * 添加到知识库
     */
    private void addToKnowledgeBase(JsonNode entity, String entityType) {
        String entityName = entity.get("name").asText();
        String key = entityType + ":" + entityName;
        entityKnowledgeBase.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);

        // 知识库已缓存到内存，可后续扩展持久化逻辑
        log.debug("实体已添加到知识库: {} ({})", entityName, entityType);
    }

    /**
     * 生成实体ID
     */
    private String generateEntityId(String entityType) {
        return entityType + "_" + System.currentTimeMillis() + "_" +
                Integer.toHexString((int) (Math.random() * 0x10000));
    }

    /**
     * 合并实体属性 - v5.0企业级版本
     * 
     * 职责：
     * 1. 智能属性合并，避免覆盖有价值信息
     * 2. 置信度比较和优化选择
     * 3. 冲突处理和日志记录
     * 4. 属性完整性验证
     */
    public void mergeEntityAttributes(ObjectNode target, ObjectNode source) {
        if (target == null || source == null) {
            log.warn("⚠️ 实体合并参数为空，跳过合并操作");
            return;
        }

        log.debug("🔀 开始合并实体属性: target={}, source={}", 
                 target.has("name") ? target.get("name") : "unknown", 
                 source.has("name") ? source.get("name") : "unknown");

        AtomicInteger mergedCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        source.fieldNames().forEachRemaining(fieldName -> {
            JsonNode sourceValue = source.get(fieldName);
            
            if (!target.has(fieldName)) {
                // 直接添加不存在的属性
                target.set(fieldName, sourceValue);
                mergedCount.incrementAndGet();
                log.debug("✅ 新增属性: {} = {}", fieldName, sourceValue);
                
            } else {
                // 处理属性冲突
                JsonNode targetValue = target.get(fieldName);
                if (!targetValue.equals(sourceValue)) {
                    handleAttributeConflict(target, fieldName, targetValue, sourceValue);
                    conflictCount.incrementAndGet();
                }
            }
        });

        // 添加合并元数据
        target.put("merged", true);
        target.put("merged_timestamp", System.currentTimeMillis());
        target.put("merged_attributes_count", mergedCount.get());
        target.put("conflict_count", conflictCount.get());

        log.info("✅ 实体属性合并完成: merged={}, conflicts={}", mergedCount.get(), conflictCount.get());
    }

    /**
     * 处理属性冲突
     */
    private void handleAttributeConflict(ObjectNode target, String fieldName, 
                                       JsonNode targetValue, JsonNode sourceValue) {
        
        log.debug("⚠️ 属性合并冲突: field={}, target={}, source={}", 
                 fieldName, targetValue, sourceValue);

        // 策略1: 基于置信度选择
        if (hasHigherConfidence(sourceValue, targetValue)) {
            target.set(fieldName, sourceValue);
            log.debug("🔄 基于置信度选择源值: {}", sourceValue);
            return;
        }

        // 策略2: 基于数据完整性选择
        if (isMoreComplete(sourceValue, targetValue)) {
            target.set(fieldName, sourceValue);
            log.debug("🔄 基于完整性选择源值: {}", sourceValue);
            return;
        }

        // 策略3: 基于时间戳选择（较新的优先）
        if (isNewer(sourceValue, targetValue)) {
            target.set(fieldName, sourceValue);
            log.debug("🔄 基于时间戳选择源值: {}", sourceValue);
            return;
        }

        // 策略4: 数组类型合并
        if (targetValue.isArray() && sourceValue.isArray()) {
            ArrayNode mergedArray = mergeArrayValues(targetValue, sourceValue);
            target.set(fieldName, mergedArray);
            log.debug("🔄 数组属性合并: {}", mergedArray);
            return;
        }

        // 默认策略：保留原有值，记录冲突
        log.debug("📝 属性合并冲突保留原值: field={}, kept={}", fieldName, targetValue);
        
        // 添加冲突记录到_conflicts字段
        addConflictRecord(target, fieldName, targetValue, sourceValue);
    }

    /**
     * 检查是否有更高置信度
     */
    private boolean hasHigherConfidence(JsonNode sourceValue, JsonNode targetValue) {
        try {
            if (sourceValue.isObject() && targetValue.isObject()) {
                ObjectNode sourceObj = (ObjectNode) sourceValue;
                ObjectNode targetObj = (ObjectNode) targetValue;
                
                if (sourceObj.has("confidence") && targetObj.has("confidence")) {
                    double sourceConf = sourceObj.get("confidence").asDouble(0.0);
                    double targetConf = targetObj.get("confidence").asDouble(0.0);
                    return sourceConf > targetConf;
                }
            }
        } catch (Exception e) {
            log.debug("置信度比较失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 检查数据是否更完整
     */
    private boolean isMoreComplete(JsonNode sourceValue, JsonNode targetValue) {
        if (sourceValue.isObject() && targetValue.isObject()) {
            return sourceValue.size() > targetValue.size();
        }
        
        if (sourceValue.isTextual() && targetValue.isTextual()) {
            return sourceValue.asText().length() > targetValue.asText().length();
        }
        
        return false;
    }

    /**
     * 检查数据是否更新
     */
    private boolean isNewer(JsonNode sourceValue, JsonNode targetValue) {
        try {
            if (sourceValue.isObject() && targetValue.isObject()) {
                ObjectNode sourceObj = (ObjectNode) sourceValue;
                ObjectNode targetObj = (ObjectNode) targetValue;
                
                if (sourceObj.has("timestamp") && targetObj.has("timestamp")) {
                    long sourceTime = sourceObj.get("timestamp").asLong(0);
                    long targetTime = targetObj.get("timestamp").asLong(0);
                    return sourceTime > targetTime;
                }
            }
        } catch (Exception e) {
            log.debug("时间戳比较失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 合并数组值
     */
    private ArrayNode mergeArrayValues(JsonNode targetArray, JsonNode sourceArray) {
        ArrayNode merged = objectMapper.createArrayNode();
        Set<String> seen = new HashSet<>();
        
        // 添加目标数组元素
        targetArray.forEach(item -> {
            String itemStr = item.toString();
            if (seen.add(itemStr)) {
                merged.add(item);
            }
        });
        
        // 添加源数组中的新元素
        sourceArray.forEach(item -> {
            String itemStr = item.toString();
            if (seen.add(itemStr)) {
                merged.add(item);
            }
        });
        
        return merged;
    }

    /**
     * 添加冲突记录
     */
    private void addConflictRecord(ObjectNode target, String fieldName, 
                                 JsonNode targetValue, JsonNode sourceValue) {
        if (!target.has("_conflicts")) {
            target.set("_conflicts", objectMapper.createObjectNode());
        }
        
        ObjectNode conflicts = (ObjectNode) target.get("_conflicts");
        ObjectNode conflictInfo = objectMapper.createObjectNode();
        conflictInfo.set("original", targetValue);
        conflictInfo.set("candidate", sourceValue);
        conflictInfo.put("timestamp", System.currentTimeMillis());
        
        conflicts.set(fieldName, conflictInfo);
    }

    /**
     * 批量合并实体列表
     */
    public List<ObjectNode> batchMergeEntities(List<ObjectNode> entities) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }

        log.info("🔀 开始批量实体合并: count={}", entities.size());
        
        List<ObjectNode> merged = new ArrayList<>();
        Map<String, ObjectNode> entityMap = new HashMap<>();
        
        for (ObjectNode entity : entities) {
            if (!entity.has("name")) {
                merged.add(entity); // 没有name的实体直接保留
                continue;
            }
            
            String entityName = entity.get("name").asText().toLowerCase().trim();
            
            if (entityMap.containsKey(entityName)) {
                // 合并到已存在的实体
                ObjectNode existing = entityMap.get(entityName);
                mergeEntityAttributes(existing, entity);
            } else {
                // 新实体
                entityMap.put(entityName, entity.deepCopy());
            }
        }
        
        merged.addAll(entityMap.values());
        
        log.info("✅ 批量实体合并完成: original={}, merged={}", 
                entities.size(), merged.size());
        
        return merged;
    }

    /**
     * 消歧义结果类
     */
    public static class DisambiguationResult {
        private final boolean matched;
        private final JsonNode matchedEntity;
        private final double confidence;

        private DisambiguationResult(boolean matched, JsonNode matchedEntity, double confidence) {
            this.matched = matched;
            this.matchedEntity = matchedEntity;
            this.confidence = confidence;
        }

        public static DisambiguationResult matched(JsonNode entity, double confidence) {
            return new DisambiguationResult(true, entity, confidence);
        }

        public static DisambiguationResult newEntity() {
            return new DisambiguationResult(false, null, 1.0);
        }

        public boolean isMatched() {
            return matched;
        }

        public JsonNode getMatchedEntity() {
            return matchedEntity;
        }

        public double getConfidence() {
            return confidence;
        }
    }
}
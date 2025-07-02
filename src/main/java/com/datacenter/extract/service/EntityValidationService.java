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
 * 实体验证服务 - 知识图谱实体处理核心组件
 * 基于'升级业务需求.md'的实体验证和标准化功能
 * 
 * 🎯 核心功能:
 * 1. 实体属性验证 - 验证必需和可选属性
 * 2. 实体类型管理 - 管理不同类型实体的验证规则
 * 3. 实体标准化 - 名称、日期、地点等标准化处理
 * 4. 实体质量评估 - 评估实体数据的完整性和一致性
 * 
 * @since v5.0
 */
@Service
public class EntityValidationService {

    private static final Logger log = LoggerFactory.getLogger(EntityValidationService.class);

    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 配置文件相关字段
    private JsonNode validationConfig;
    private Map<String, JsonNode> entityTypes = new HashMap<>();
    private JsonNode disambiguationSettings;
    private JsonNode knowledgeFusionRules;
    private JsonNode entityNormalization;
    private JsonNode qualityAssessment;
    
    @Autowired
    public EntityValidationService(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("🔍 EntityValidationService初始化完成 - 支持实体验证和标准化");
    }

    @PostConstruct
    private void loadValidationConfig() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/entityvalidation.json");
            validationConfig = objectMapper.readTree(resource.getInputStream());
            
            loadEntityTypes();
            loadDisambiguationSettings();
            loadKnowledgeFusionRules();
            loadNormalizationRules();
            loadQualityMetrics();
            
            log.info("✅ 实体验证配置加载完成 - 支持{}种实体类型", entityTypes.size());
        } catch (IOException e) {
            log.error("❌ 加载实体验证配置失败: {}", e.getMessage());
            loadDefaultConfig();
        }
    }

    private void loadEntityTypes() {
        JsonNode entityValidation = validationConfig.get("entity_validation");
        if (entityValidation != null) {
            JsonNode types = entityValidation.get("entity_types");
            if (types != null) {
                types.fieldNames().forEachRemaining(typeName -> {
                    entityTypes.put(typeName.toLowerCase(), types.get(typeName));
                });
            }
        }
    }

    private void loadDisambiguationSettings() {
        disambiguationSettings = validationConfig.get("disambiguation_settings");
    }

    private void loadKnowledgeFusionRules() {
        knowledgeFusionRules = validationConfig.get("knowledge_fusion_rules");
    }

    private void loadNormalizationRules() {
        entityNormalization = validationConfig.get("entity_normalization");
    }

    private void loadQualityMetrics() {
        qualityAssessment = validationConfig.get("quality_assessment");
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        log.warn("⚠️ 使用默认实体验证配置");
        
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

    private void loadFallbackFromConfig(JsonNode fallbackConfig) {
        JsonNode defaultEntityTypes = fallbackConfig.get("default_entity_types");
        if (defaultEntityTypes != null) {
            defaultEntityTypes.fieldNames().forEachRemaining(typeName -> {
                entityTypes.put(typeName.toLowerCase(), defaultEntityTypes.get(typeName));
            });
        }
        log.info("✅ 从fallback配置加载成功");
    }

    private void loadEmergencyConfig() {
        log.error("🚨 使用紧急最小化配置");
        ObjectNode minimalEntity = objectMapper.createObjectNode();
        minimalEntity.set("required_attributes", 
            objectMapper.createArrayNode().add("name"));
        entityTypes.put("default", minimalEntity);
    }

    /**
     * 验证实体
     */
    public ValidationResult validateEntity(JsonNode entity, String entityType) {
        log.debug("🔍 验证实体: type={}, entity={}", entityType, 
            entity.has("name") ? entity.get("name") : "unknown");
        
        JsonNode typeConfig = getEntityTypeConfig(entityType);
        if (typeConfig == null) {
            return ValidationResult.invalid("未知的实体类型: " + entityType);
        }
        
        // 验证必需属性
        List<String> missingAttributes = checkRequiredAttributes(entity, typeConfig);
        if (!missingAttributes.isEmpty()) {
            return ValidationResult.invalid("缺少必需属性: " + String.join(", ", missingAttributes));
        }
        
        // 验证属性格式
        String formatError = validateAttributeFormats(entity, entityType);
        if (formatError != null) {
            return ValidationResult.invalid(formatError);
        }
        
        // 计算质量分数
        double qualityScore = calculateQualityScore(entity, typeConfig);
        
        return ValidationResult.valid(qualityScore);
    }

    /**
     * 获取实体类型配置
     */
    public JsonNode getEntityTypeConfig(String entityType) {
        return entityTypes.getOrDefault(entityType.toLowerCase(), 
            entityTypes.get("default"));
    }

    /**
     * 获取必需属性
     */
    public Set<String> getRequiredAttributes(String entityType) {
        JsonNode typeConfig = getEntityTypeConfig(entityType);
        Set<String> attributes = new HashSet<>();
        
        if (typeConfig != null && typeConfig.has("required_attributes")) {
            JsonNode requiredAttrs = typeConfig.get("required_attributes");
            if (requiredAttrs.isArray()) {
                requiredAttrs.forEach(attr -> attributes.add(attr.asText()));
            }
        }
        
        return attributes;
    }

    /**
     * 获取实体有效关系
     */
    public Set<String> getValidRelations(String entityType) {
        JsonNode typeConfig = getEntityTypeConfig(entityType);
        Set<String> relations = new HashSet<>();
        
        if (typeConfig != null && typeConfig.has("valid_relations")) {
            JsonNode validRelations = typeConfig.get("valid_relations");
            if (validRelations.isArray()) {
                validRelations.forEach(rel -> relations.add(rel.asText()));
            }
        }
        
        return relations;
    }

    /**
     * 检查必需属性
     */
    private List<String> checkRequiredAttributes(JsonNode entity, JsonNode typeConfig) {
        List<String> missing = new ArrayList<>();
        
        if (typeConfig.has("required_attributes")) {
            JsonNode requiredAttrs = typeConfig.get("required_attributes");
            if (requiredAttrs.isArray()) {
                requiredAttrs.forEach(attr -> {
                    String attrName = attr.asText();
                    if (!entity.has(attrName) || entity.get(attrName).isNull()) {
                        missing.add(attrName);
                    }
                });
            }
        }
        
        return missing;
    }

    /**
     * 验证属性格式
     */
    private String validateAttributeFormats(JsonNode entity, String entityType) {
        // 验证日期格式
        if (entity.has("birth_date")) {
            if (!isValidDate(entity.get("birth_date").asText())) {
                return "无效的出生日期格式";
            }
        }
        
        if (entity.has("release_year")) {
            if (!isValidYear(entity.get("release_year").asText())) {
                return "无效的发行年份格式";
            }
        }
        
        return null;
    }

    /**
     * 计算质量分数
     */
    private double calculateQualityScore(JsonNode entity, JsonNode typeConfig) {
        double score = 0.0;
        int factors = 0;
        
        // 必需属性完整性
        JsonNode requiredAttrs = typeConfig.get("required_attributes");
        if (requiredAttrs != null && requiredAttrs.isArray()) {
            int required = requiredAttrs.size();
            int present = 0;
            for (JsonNode attr : requiredAttrs) {
                if (entity.has(attr.asText())) {
                    present++;
                }
            }
            score += (double) present / required * 0.5;
            factors++;
        }
        
        // 可选属性完整性
        JsonNode optionalAttrs = typeConfig.get("optional_attributes");
        if (optionalAttrs != null && optionalAttrs.isArray() && optionalAttrs.size() > 0) {
            int optional = optionalAttrs.size();
            int present = 0;
            for (JsonNode attr : optionalAttrs) {
                if (entity.has(attr.asText())) {
                    present++;
                }
            }
            score += (double) present / optional * 0.3;
            factors++;
        }
        
        // 数据规范性
        if (entity.has("name") && isNormalized(entity.get("name").asText())) {
            score += 0.2;
            factors++;
        }
        
        return factors > 0 ? score / factors : 0.5;
    }

    /**
     * 标准化实体
     */
    public JsonNode normalizeEntity(JsonNode entity, String entityType) {
        ObjectNode normalized = entity.deepCopy();
        
        // 名称标准化
        if (normalized.has("name")) {
            String normalizedName = normalizeName(normalized.get("name").asText());
            normalized.put("name", normalizedName);
        }
        
        // 日期标准化
        if (normalized.has("birth_date")) {
            String normalizedDate = normalizeDate(normalized.get("birth_date").asText());
            normalized.put("birth_date", normalizedDate);
        }
        
        // 职业标准化
        if (normalized.has("profession")) {
            String normalizedProfession = internalNormalizeProfession(normalized.get("profession").asText());
            normalized.put("profession", normalizedProfession);
        }
        
        // 地点标准化
        if (normalized.has("location")) {
            String normalizedLocation = normalizeLocation(normalized.get("location").asText());
            normalized.put("location", normalizedLocation);
        }
        
        // 添加标准化元数据
        normalized.put("normalized", true);
        normalized.put("normalized_timestamp", System.currentTimeMillis());
        normalized.put("entity_type", entityType);
        
        return normalized;
    }

    /**
     * 名称标准化
     */
    private String normalizeName(String name) {
        if (entityNormalization == null || !entityNormalization.has("name_normalization")) {
            return name.trim();
        }
        
        JsonNode nameNorm = entityNormalization.get("name_normalization");
        String normalized = name.trim();
        
        // 移除称谓
        if (nameNorm.has("remove_titles")) {
            JsonNode titles = nameNorm.get("remove_titles");
            if (titles.isArray()) {
                for (JsonNode title : titles) {
                    normalized = normalized.replace(title.asText(), "");
                }
            }
        }
        
        // 移除括号内容
        if (nameNorm.has("remove_brackets") && nameNorm.get("remove_brackets").asBoolean()) {
            normalized = normalized.replaceAll("\\([^)]*\\)", "").trim();
        }
        
        return normalized.trim();
    }

    /**
     * 日期标准化
     */
    private String normalizeDate(String date) {
        if (entityNormalization == null || !entityNormalization.has("date_normalization")) {
            return date;
        }
        
        JsonNode dateNorm = entityNormalization.get("date_normalization");
        
        // 处理仅年份的情况
        if (date.matches("\\d{4}")) {
            String suffix = dateNorm.has("year_only_suffix") ? 
                dateNorm.get("year_only_suffix").asText() : "-01-01";
            return date + suffix;
        }
        
        // TODO: 实现更复杂的日期格式转换
        return date;
    }

    /**
     * 职业标准化 - 公共方法供外部调用
     */
    public String normalizeProfession(String profession) {
        if (entityNormalization == null || !entityNormalization.has("profession_normalization")) {
            return profession;
        }
        
        JsonNode professionNorm = entityNormalization.get("profession_normalization");
        if (professionNorm.has(profession)) {
            return professionNorm.get(profession).asText();
        }
        
        return profession;
    }

    /**
     * 日期格式标准化 - 公共方法供外部调用
     */
    public String normalizeDateFormat(String date) {
        return normalizeDate(date);
    }

    /**
     * 职业标准化（内部使用）
     */
    private String internalNormalizeProfession(String profession) {
        return normalizeProfession(profession);
    }

    /**
     * 地点标准化
     */
    private String normalizeLocation(String location) {
        if (entityNormalization == null || !entityNormalization.has("location_normalization")) {
            return location;
        }
        
        JsonNode locationNorm = entityNormalization.get("location_normalization");
        
        // 国家标准化
        if (locationNorm.has("country_mappings")) {
            JsonNode countryMappings = locationNorm.get("country_mappings");
            if (countryMappings.has(location)) {
                return countryMappings.get(location).asText();
            }
        }
        
        // 城市标准化
        if (locationNorm.has("city_mappings")) {
            JsonNode cityMappings = locationNorm.get("city_mappings");
            if (cityMappings.has(location)) {
                return cityMappings.get(location).asText();
            }
        }
        
        return location;
    }

    /**
     * 辅助方法
     */
    private boolean isValidDate(String date) {
        // 简单的日期格式验证
        return date.matches("\\d{4}(-\\d{2}-\\d{2})?");
    }

    private boolean isValidYear(String year) {
        try {
            int y = Integer.parseInt(year);
            return y >= 1000 && y <= 2100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isNormalized(String text) {
        // 检查是否已标准化（无多余空格、无特殊字符等）
        return text.equals(text.trim()) && !text.contains("  ");
    }

    /**
     * 获取消歧义设置
     */
    public JsonNode getDisambiguationSettings() {
        return disambiguationSettings;
    }

    /**
     * 获取知识融合规则
     */
    public JsonNode getKnowledgeFusionRules() {
        return knowledgeFusionRules;
    }

    /**
     * 获取实体特定的相似度阈值
     */
    public double getEntitySimilarityThreshold(String entityType) {
        if (disambiguationSettings != null && 
            disambiguationSettings.has("entity_specific_thresholds")) {
            JsonNode thresholds = disambiguationSettings.get("entity_specific_thresholds");
            if (thresholds.has(entityType)) {
                return thresholds.get(entityType).asDouble(0.7);
            }
        }
        return 0.7; // 默认阈值
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final double qualityScore;

        private ValidationResult(boolean valid, String message, double qualityScore) {
            this.valid = valid;
            this.message = message;
            this.qualityScore = qualityScore;
        }

        public static ValidationResult valid(double qualityScore) {
            return new ValidationResult(true, "验证通过", qualityScore);
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

        public double getQualityScore() {
            return qualityScore;
        }
    }
} 
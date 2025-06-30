package com.datacenter.extract.service;

import com.datacenter.extract.entity.KnowledgeQuality;
import com.datacenter.extract.repository.KnowledgeQualityRepository;
import com.datacenter.extract.repository.CelebrityRepository;
import com.datacenter.extract.repository.WorkRepository;
import com.datacenter.extract.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 质量评估服务 - v3.0核心组件
 * 
 * 负责评估知识图谱的质量，包括完整性、一致性、准确性
 * 设计理念：多维度评估、可配置规则、实时监控
 */
@Component
public class QualityAssessor {

    private static final Logger log = LoggerFactory.getLogger(QualityAssessor.class);

    private final KnowledgeQualityRepository qualityRepository;
    private final CelebrityRepository celebrityRepository;
    private final WorkRepository workRepository;
    private final EventRepository eventRepository;

    @Autowired
    public QualityAssessor(KnowledgeQualityRepository qualityRepository,
            CelebrityRepository celebrityRepository,
            WorkRepository workRepository,
            EventRepository eventRepository) {
        this.qualityRepository = qualityRepository;
        this.celebrityRepository = celebrityRepository;
        this.workRepository = workRepository;
        this.eventRepository = eventRepository;
        log.info("QualityAssessor initialized successfully");
    }

    /**
     * 质量评估主入口
     */
    public Map<String, Object> assess(Map<String, Object> data) {
        try {
            log.info("开始知识质量评估");

            QualityAssessmentResult result = new QualityAssessmentResult();

            // 评估实体质量
            if (data.containsKey("triples")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> triples = (List<Map<String, Object>>) data.get("triples");
                assessTriples(triples, result);
            }

            // 评估整体质量
            calculateOverallQuality(result);

            // 生成质量报告
            Map<String, Object> qualityReport = generateQualityReport(result);

            // 更新数据
            data.put("quality_assessment", qualityReport);
            data.put("quality_score", result.overallScore);
            data.put("quality_grade", result.qualityGrade);
            data.put("assessment_timestamp", System.currentTimeMillis());

            // 保存质量评估结果到数据库
            saveQualityAssessment(result);

            log.info("知识质量评估完成，总体质量分数: {}, 等级: {}",
                    result.overallScore, result.qualityGrade);

            return data;

        } catch (Exception e) {
            log.error("质量评估处理失败: {}", e.getMessage(), e);
            data.put("quality_error", e.getMessage());
            return data;
        }
    }

    /**
     * 评估三元组质量
     */
    private void assessTriples(List<Map<String, Object>> triples, QualityAssessmentResult result) {
        Map<String, EntityQuality> entityQualityMap = new HashMap<>();

        for (Map<String, Object> triple : triples) {
            String subject = (String) triple.get("subject");
            String predicate = (String) triple.get("predicate");
            String object = (String) triple.get("object");

            // 评估主语实体
            EntityQuality subjectQuality = entityQualityMap.computeIfAbsent(
                    subject, k -> new EntityQuality(k));
            assessEntityFromTriple(subjectQuality, triple, true);

            // 评估宾语实体（如果是实体）
            if (isEntity(object)) {
                EntityQuality objectQuality = entityQualityMap.computeIfAbsent(
                        object, k -> new EntityQuality(k));
                assessEntityFromTriple(objectQuality, triple, false);
            }

            // 评估关系质量
            assessRelationQuality(triple, result);
        }

        // 汇总实体质量
        for (EntityQuality entityQuality : entityQualityMap.values()) {
            calculateEntityQualityScore(entityQuality);
            result.entityQualities.add(entityQuality);
        }
    }

    /**
     * 评估实体质量
     */
    private void assessEntityFromTriple(EntityQuality entityQuality,
            Map<String, Object> triple,
            boolean isSubject) {
        // 增加属性计数
        entityQuality.attributeCount++;

        // 检查置信度
        Double confidence = getConfidence(triple, isSubject);
        entityQuality.confidenceScores.add(confidence);

        // 检查完整性
        String predicate = (String) triple.get("predicate");
        if (isImportantAttribute(predicate)) {
            entityQuality.hasImportantAttributes = true;
        }

        // 检查数据源
        if (triple.containsKey("source_info")) {
            entityQuality.sourceCount++;
        }
    }

    /**
     * 评估关系质量
     */
    private void assessRelationQuality(Map<String, Object> triple,
            QualityAssessmentResult result) {
        RelationQuality relationQuality = new RelationQuality();

        // 检查验证状态
        String validationStatus = (String) triple.get("validation_status");
        relationQuality.isValid = !"invalid".equals(validationStatus);

        // 检查置信度
        relationQuality.confidence = getRelationConfidence(triple);

        // 检查冲突
        relationQuality.hasConflict = Boolean.TRUE.equals(triple.get("has_conflict"));

        // 检查时序信息
        relationQuality.hasTemporal = triple.containsKey("temporal_info");

        result.relationQualities.add(relationQuality);
    }

    /**
     * 计算实体质量分数
     */
    private void calculateEntityQualityScore(EntityQuality entityQuality) {
        // 完整性分数（基于属性数量）
        double completeness = Math.min(entityQuality.attributeCount / 5.0, 1.0);

        // 一致性分数（基于置信度方差）
        double consistency = calculateConsistency(entityQuality.confidenceScores);

        // 准确性分数（基于平均置信度）
        double accuracy = entityQuality.confidenceScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);

        // 综合质量分数
        entityQuality.completeness = BigDecimal.valueOf(completeness)
                .setScale(2, RoundingMode.HALF_UP);
        entityQuality.consistency = BigDecimal.valueOf(consistency)
                .setScale(2, RoundingMode.HALF_UP);
        entityQuality.accuracy = BigDecimal.valueOf(accuracy)
                .setScale(2, RoundingMode.HALF_UP);

        entityQuality.qualityScore = BigDecimal.valueOf(
                completeness * 0.3 + consistency * 0.3 + accuracy * 0.4).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算一致性分数
     */
    private double calculateConsistency(List<Double> confidenceScores) {
        if (confidenceScores.size() <= 1) {
            return 1.0;
        }

        // 计算标准差
        double mean = confidenceScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double variance = confidenceScores.stream()
                .mapToDouble(score -> Math.pow(score - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);

        // 标准差越小，一致性越高
        return Math.max(0, 1 - stdDev);
    }

    /**
     * 计算整体质量
     */
    private void calculateOverallQuality(QualityAssessmentResult result) {
        // 实体质量平均分
        double avgEntityQuality = result.entityQualities.stream()
                .mapToDouble(eq -> eq.qualityScore.doubleValue())
                .average()
                .orElse(0.0);

        // 关系质量分数
        long validRelations = result.relationQualities.stream()
                .filter(rq -> rq.isValid)
                .count();
        double relationQuality = result.relationQualities.isEmpty() ? 0.0
                : (double) validRelations / result.relationQualities.size();

        // 综合质量分数
        result.overallScore = BigDecimal.valueOf(
                avgEntityQuality * 0.6 + relationQuality * 0.4).setScale(2, RoundingMode.HALF_UP);

        // 确定质量等级
        result.qualityGrade = determineQualityGrade(result.overallScore.doubleValue());
    }

    /**
     * 确定质量等级
     */
    private KnowledgeQuality.QualityGrade determineQualityGrade(double score) {
        if (score >= 0.9)
            return KnowledgeQuality.QualityGrade.EXCELLENT;
        if (score >= 0.8)
            return KnowledgeQuality.QualityGrade.GOOD;
        if (score >= 0.6)
            return KnowledgeQuality.QualityGrade.FAIR;
        if (score >= 0.4)
            return KnowledgeQuality.QualityGrade.POOR;
        return KnowledgeQuality.QualityGrade.VERY_POOR;
    }

    /**
     * 生成质量报告
     */
    private Map<String, Object> generateQualityReport(QualityAssessmentResult result) {
        Map<String, Object> report = new HashMap<>();

        // 总体指标
        report.put("overall_score", result.overallScore);
        report.put("quality_grade", result.qualityGrade);

        // 实体质量统计
        Map<String, Object> entityStats = new HashMap<>();
        entityStats.put("total_entities", result.entityQualities.size());
        entityStats.put("avg_quality", result.entityQualities.stream()
                .mapToDouble(eq -> eq.qualityScore.doubleValue())
                .average()
                .orElse(0.0));
        entityStats.put("high_quality_count", result.entityQualities.stream()
                .filter(eq -> eq.qualityScore.doubleValue() >= 0.8)
                .count());
        report.put("entity_statistics", entityStats);

        // 关系质量统计
        Map<String, Object> relationStats = new HashMap<>();
        relationStats.put("total_relations", result.relationQualities.size());
        relationStats.put("valid_relations", result.relationQualities.stream()
                .filter(rq -> rq.isValid)
                .count());
        relationStats.put("conflict_count", result.relationQualities.stream()
                .filter(rq -> rq.hasConflict)
                .count());
        report.put("relation_statistics", relationStats);

        // 问题和建议
        report.put("issues_found", identifyIssues(result));
        report.put("improvement_suggestions", generateSuggestions(result));

        return report;
    }

    /**
     * 识别质量问题
     */
    private List<String> identifyIssues(QualityAssessmentResult result) {
        List<String> issues = new ArrayList<>();

        // 检查低质量实体
        long lowQualityEntities = result.entityQualities.stream()
                .filter(eq -> eq.qualityScore.doubleValue() < 0.6)
                .count();
        if (lowQualityEntities > 0) {
            issues.add("发现 " + lowQualityEntities + " 个低质量实体");
        }

        // 检查关系冲突
        long conflicts = result.relationQualities.stream()
                .filter(rq -> rq.hasConflict)
                .count();
        if (conflicts > 0) {
            issues.add("发现 " + conflicts + " 个关系冲突");
        }

        // 检查缺失重要属性
        long incompleteEntities = result.entityQualities.stream()
                .filter(eq -> !eq.hasImportantAttributes)
                .count();
        if (incompleteEntities > 0) {
            issues.add("有 " + incompleteEntities + " 个实体缺少重要属性");
        }

        return issues;
    }

    /**
     * 生成改进建议
     */
    private List<String> generateSuggestions(QualityAssessmentResult result) {
        List<String> suggestions = new ArrayList<>();

        if (result.overallScore.doubleValue() < 0.8) {
            suggestions.add("建议增加数据源以提高准确性");
        }

        // 基于具体问题的建议
        long lowConfidenceRelations = result.relationQualities.stream()
                .filter(rq -> rq.confidence < 0.7)
                .count();
        if (lowConfidenceRelations > 0) {
            suggestions.add("建议人工审核低置信度的关系");
        }

        // 完整性建议
        double avgCompleteness = result.entityQualities.stream()
                .mapToDouble(eq -> eq.completeness.doubleValue())
                .average()
                .orElse(0.0);
        if (avgCompleteness < 0.7) {
            suggestions.add("建议补充实体的基本属性信息");
        }

        return suggestions;
    }

    /**
     * 保存质量评估结果
     */
    private void saveQualityAssessment(QualityAssessmentResult result) {
        try {
            // 为每个实体保存质量记录
            for (EntityQuality eq : result.entityQualities) {
                KnowledgeQuality quality = new KnowledgeQuality();
                quality.setEntityType(KnowledgeQuality.EntityType.celebrity); // 简化处理
                quality.setEntityId(eq.entityName);
                quality.setQualityScore(eq.qualityScore);
                quality.setCompleteness(eq.completeness);
                quality.setConsistency(eq.consistency);
                quality.setAccuracy(eq.accuracy);
                quality.setQualityGrade(determineQualityGrade(eq.qualityScore.doubleValue()));
                quality.setAssessmentMethod("auto");

                qualityRepository.save(quality);
            }

            log.info("成功保存 {} 条质量评估记录", result.entityQualities.size());

        } catch (Exception e) {
            log.error("保存质量评估结果失败: {}", e.getMessage());
        }
    }

    // 辅助方法

    private boolean isEntity(String value) {
        return value != null && value.matches(".*[\u4e00-\u9fa5]+.*") && value.length() > 1;
    }

    private boolean isImportantAttribute(String predicate) {
        return Arrays.asList("职业", "出生", "国籍", "类型", "导演", "主演")
                .contains(predicate);
    }

    private Double getConfidence(Map<String, Object> triple, boolean isSubject) {
        String key = isSubject ? "subject_confidence" : "object_confidence";
        Object conf = triple.get(key);
        if (conf instanceof Number) {
            return ((Number) conf).doubleValue();
        }
        return 0.8; // 默认置信度
    }

    private double getRelationConfidence(Map<String, Object> triple) {
        Object conf = triple.get("validation_confidence");
        if (conf instanceof Number) {
            return ((Number) conf).doubleValue();
        }
        return 0.8;
    }

    // 内部类

    /**
     * 质量评估结果
     */
    private static class QualityAssessmentResult {
        List<EntityQuality> entityQualities = new ArrayList<>();
        List<RelationQuality> relationQualities = new ArrayList<>();
        BigDecimal overallScore = BigDecimal.ZERO;
        KnowledgeQuality.QualityGrade qualityGrade;
    }

    /**
     * 实体质量
     */
    private static class EntityQuality {
        String entityName;
        int attributeCount = 0;
        boolean hasImportantAttributes = false;
        int sourceCount = 0;
        List<Double> confidenceScores = new ArrayList<>();
        BigDecimal qualityScore = BigDecimal.ZERO;
        BigDecimal completeness = BigDecimal.ZERO;
        BigDecimal consistency = BigDecimal.ZERO;
        BigDecimal accuracy = BigDecimal.ZERO;

        EntityQuality(String entityName) {
            this.entityName = entityName;
        }
    }

    /**
     * 关系质量
     */
    private static class RelationQuality {
        boolean isValid = true;
        double confidence = 0.8;
        boolean hasConflict = false;
        boolean hasTemporal = false;
    }
}
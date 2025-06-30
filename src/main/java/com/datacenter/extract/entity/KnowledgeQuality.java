package com.datacenter.extract.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识质量评估实体 - v3.0知识图谱功能
 * 
 * 不使用JPA注解，采用简单POJO + 手动SQL操作
 */
public class KnowledgeQuality {

    private Long id;
    private EntityType entityType;
    private String entityId;
    private BigDecimal qualityScore;
    private BigDecimal completeness;
    private BigDecimal consistency;
    private BigDecimal accuracy;
    private String assessmentMethod;
    private QualityGrade qualityGrade;
    private String issuesFound;
    private String improvementSuggestions;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAssessed;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public enum EntityType {
        celebrity, work, event, relation
    }

    public enum QualityGrade {
        EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
    }

    // 构造函数
    public KnowledgeQuality() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastAssessed = LocalDateTime.now();
        this.assessmentMethod = "auto";
        this.qualityGrade = QualityGrade.FAIR;
    }

    public KnowledgeQuality(EntityType entityType, String entityId,
            BigDecimal qualityScore, BigDecimal completeness,
            BigDecimal consistency, BigDecimal accuracy) {
        this();
        this.entityType = entityType;
        this.entityId = entityId;
        this.qualityScore = qualityScore;
        this.completeness = completeness;
        this.consistency = consistency;
        this.accuracy = accuracy;
        this.qualityGrade = calculateQualityGrade(qualityScore);
    }

    private QualityGrade calculateQualityGrade(BigDecimal score) {
        if (score == null)
            return QualityGrade.FAIR;

        double scoreValue = score.doubleValue();
        if (scoreValue >= 0.9)
            return QualityGrade.EXCELLENT;
        if (scoreValue >= 0.8)
            return QualityGrade.GOOD;
        if (scoreValue >= 0.7)
            return QualityGrade.FAIR;
        if (scoreValue >= 0.6)
            return QualityGrade.POOR;
        return QualityGrade.VERY_POOR;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
        this.qualityGrade = calculateQualityGrade(qualityScore);
    }

    public BigDecimal getCompleteness() {
        return completeness;
    }

    public void setCompleteness(BigDecimal completeness) {
        this.completeness = completeness;
    }

    public BigDecimal getConsistency() {
        return consistency;
    }

    public void setConsistency(BigDecimal consistency) {
        this.consistency = consistency;
    }

    public BigDecimal getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(BigDecimal accuracy) {
        this.accuracy = accuracy;
    }

    public String getAssessmentMethod() {
        return assessmentMethod;
    }

    public void setAssessmentMethod(String assessmentMethod) {
        this.assessmentMethod = assessmentMethod;
    }

    public QualityGrade getQualityGrade() {
        return qualityGrade;
    }

    public void setQualityGrade(QualityGrade qualityGrade) {
        this.qualityGrade = qualityGrade;
    }

    public String getIssuesFound() {
        return issuesFound;
    }

    public void setIssuesFound(String issuesFound) {
        this.issuesFound = issuesFound;
    }

    public String getImprovementSuggestions() {
        return improvementSuggestions;
    }

    public void setImprovementSuggestions(String improvementSuggestions) {
        this.improvementSuggestions = improvementSuggestions;
    }

    public LocalDateTime getLastAssessed() {
        return lastAssessed;
    }

    public void setLastAssessed(LocalDateTime lastAssessed) {
        this.lastAssessed = lastAssessed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "KnowledgeQuality{" +
                "id=" + id +
                ", entityType=" + entityType +
                ", entityId='" + entityId + '\'' +
                ", qualityScore=" + qualityScore +
                ", qualityGrade=" + qualityGrade +
                ", lastAssessed=" + lastAssessed +
                '}';
    }
}
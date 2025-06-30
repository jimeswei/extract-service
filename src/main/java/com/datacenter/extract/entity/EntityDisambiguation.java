package com.datacenter.extract.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实体消歧义记录实体 - v3.0知识图谱功能
 * 
 * 简单POJO，配合JdbcTemplate使用
 */
public class EntityDisambiguation {

    private Long id;
    private String entityName;
    private String canonicalName;
    private BigDecimal similarityScore;
    private String disambiguationRule;
    private EntityType entityType;
    private String contextInfo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public enum EntityType {
        celebrity, work, event
    }

    // 构造函数
    public EntityDisambiguation() {
        this.createdAt = LocalDateTime.now();
    }

    public EntityDisambiguation(String entityName, String canonicalName,
            BigDecimal similarityScore, String disambiguationRule,
            EntityType entityType, String contextInfo) {
        this();
        this.entityName = entityName;
        this.canonicalName = canonicalName;
        this.similarityScore = similarityScore;
        this.disambiguationRule = disambiguationRule;
        this.entityType = entityType;
        this.contextInfo = contextInfo;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public BigDecimal getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(BigDecimal similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getDisambiguationRule() {
        return disambiguationRule;
    }

    public void setDisambiguationRule(String disambiguationRule) {
        this.disambiguationRule = disambiguationRule;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public String getContextInfo() {
        return contextInfo;
    }

    public void setContextInfo(String contextInfo) {
        this.contextInfo = contextInfo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "EntityDisambiguation{" +
                "id=" + id +
                ", entityName='" + entityName + '\'' +
                ", canonicalName='" + canonicalName + '\'' +
                ", similarityScore=" + similarityScore +
                ", entityType=" + entityType +
                ", createdAt=" + createdAt +
                '}';
    }
}
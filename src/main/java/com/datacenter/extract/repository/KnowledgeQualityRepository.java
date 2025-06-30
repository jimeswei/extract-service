package com.datacenter.extract.repository;

import com.datacenter.extract.entity.KnowledgeQuality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识质量评估Repository - v3.0知识图谱功能
 * 
 * 采用JdbcTemplate，简单高效的数据库操作
 */
@Repository
public class KnowledgeQualityRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeQualityRepository.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public KnowledgeQualityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存知识质量评估记录
     */
    public void save(KnowledgeQuality quality) {
        try {
            String sql = """
                    INSERT INTO knowledge_quality
                    (entity_type, entity_id, quality_score, completeness, consistency, accuracy,
                     assessment_method, quality_grade, issues_found, improvement_suggestions)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            jdbcTemplate.update(sql,
                    quality.getEntityType().name(),
                    quality.getEntityId(),
                    quality.getQualityScore(),
                    quality.getCompleteness(),
                    quality.getConsistency(),
                    quality.getAccuracy(),
                    quality.getAssessmentMethod(),
                    quality.getQualityGrade().name(),
                    quality.getIssuesFound(),
                    quality.getImprovementSuggestions());

            log.debug("保存知识质量评估记录: {} {}, 分数: {}",
                    quality.getEntityType(), quality.getEntityId(), quality.getQualityScore());

        } catch (Exception e) {
            log.error("保存知识质量评估记录失败: {}", e.getMessage());
        }
    }

    /**
     * 根据实体类型和ID查找质量评估记录
     */
    public Optional<KnowledgeQuality> findByEntityTypeAndId(KnowledgeQuality.EntityType entityType,
            String entityId) {
        try {
            String sql = """
                    SELECT id, entity_type, entity_id, quality_score, completeness, consistency, accuracy,
                           assessment_method, quality_grade, issues_found, improvement_suggestions,
                           last_assessed, created_at, updated_at
                    FROM knowledge_quality
                    WHERE entity_type = ? AND entity_id = ?
                    ORDER BY last_assessed DESC
                    LIMIT 1
                    """;

            List<KnowledgeQuality> results = jdbcTemplate.query(sql,
                    new BeanPropertyRowMapper<>(KnowledgeQuality.class),
                    entityType.name(), entityId);

            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

        } catch (Exception e) {
            log.error("查找知识质量评估记录失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查找低质量实体
     */
    public List<KnowledgeQuality> findLowQualityEntities(double threshold) {
        try {
            String sql = """
                    SELECT * FROM knowledge_quality
                    WHERE quality_score < ?
                    ORDER BY quality_score ASC
                    LIMIT 50
                    """;

            return jdbcTemplate.query(sql,
                    new BeanPropertyRowMapper<>(KnowledgeQuality.class),
                    threshold);

        } catch (Exception e) {
            log.error("查找低质量实体失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 更新质量评估记录
     */
    public void update(KnowledgeQuality quality) {
        try {
            String sql = """
                    UPDATE knowledge_quality SET
                    quality_score = ?, completeness = ?, consistency = ?, accuracy = ?,
                    assessment_method = ?, quality_grade = ?, issues_found = ?,
                    improvement_suggestions = ?, last_assessed = NOW(), updated_at = NOW()
                    WHERE entity_type = ? AND entity_id = ?
                    """;

            int updated = jdbcTemplate.update(sql,
                    quality.getQualityScore(),
                    quality.getCompleteness(),
                    quality.getConsistency(),
                    quality.getAccuracy(),
                    quality.getAssessmentMethod(),
                    quality.getQualityGrade().name(),
                    quality.getIssuesFound(),
                    quality.getImprovementSuggestions(),
                    quality.getEntityType().name(),
                    quality.getEntityId());

            if (updated == 0) {
                // 如果没有更新，则插入新记录
                save(quality);
            }

        } catch (Exception e) {
            log.error("更新知识质量评估记录失败: {}", e.getMessage());
        }
    }

    /**
     * 统计各质量等级的数量
     */
    public long countByQualityGrade(KnowledgeQuality.QualityGrade grade) {
        try {
            String sql = "SELECT COUNT(*) FROM knowledge_quality WHERE quality_grade = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, grade.name());
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("统计质量等级数量失败: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 计算平均质量分数
     */
    public double getAverageQualityScore() {
        try {
            String sql = "SELECT AVG(quality_score) FROM knowledge_quality";
            Double avg = jdbcTemplate.queryForObject(sql, Double.class);
            return avg != null ? avg : 0.0;
        } catch (Exception e) {
            log.error("计算平均质量分数失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 根据实体ID查找质量评估记录
     */
    public Optional<KnowledgeQuality> findByEntityId(String entityId) {
        try {
            String sql = """
                    SELECT id, entity_type, entity_id, quality_score, completeness, consistency, accuracy,
                           assessment_method, quality_grade, issues_found, improvement_suggestions,
                           last_assessed, created_at, updated_at
                    FROM knowledge_quality
                    WHERE entity_id = ?
                    ORDER BY last_assessed DESC
                    LIMIT 1
                    """;

            List<KnowledgeQuality> results = jdbcTemplate.query(sql,
                    new BeanPropertyRowMapper<>(KnowledgeQuality.class),
                    entityId);

            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

        } catch (Exception e) {
            log.error("根据实体ID查找知识质量评估记录失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查找所有质量评估记录
     */
    public List<KnowledgeQuality> findAll() {
        try {
            String sql = """
                    SELECT * FROM knowledge_quality
                    ORDER BY last_assessed DESC
                    LIMIT 1000
                    """;

            return jdbcTemplate.query(sql,
                    new BeanPropertyRowMapper<>(KnowledgeQuality.class));

        } catch (Exception e) {
            log.error("查找所有质量评估记录失败: {}", e.getMessage());
            return List.of();
        }
    }
}
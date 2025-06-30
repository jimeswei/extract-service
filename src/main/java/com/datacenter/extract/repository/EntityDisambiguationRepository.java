package com.datacenter.extract.repository;

import com.datacenter.extract.entity.EntityDisambiguation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 实体消歧义记录Repository - v3.0知识图谱功能
 * 
 * 采用JdbcTemplate，简单高效的数据库操作
 */
@Repository
public class EntityDisambiguationRepository {

    private static final Logger log = LoggerFactory.getLogger(EntityDisambiguationRepository.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public EntityDisambiguationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存实体消歧义记录
     */
    public void save(EntityDisambiguation disambiguation) {
        try {
            String sql = """
                    INSERT INTO entity_disambiguation
                    (entity_name, canonical_name, similarity_score, disambiguation_rule, entity_type, context_info)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

            jdbcTemplate.update(sql,
                    disambiguation.getEntityName(),
                    disambiguation.getCanonicalName(),
                    disambiguation.getSimilarityScore(),
                    disambiguation.getDisambiguationRule(),
                    disambiguation.getEntityType().name(),
                    disambiguation.getContextInfo());

            log.debug("保存实体消歧义记录: {} -> {}",
                    disambiguation.getEntityName(), disambiguation.getCanonicalName());

        } catch (Exception e) {
            log.error("保存实体消歧义记录失败: {}", e.getMessage());
        }
    }

    /**
     * 根据实体名称查找消歧义记录
     */
    public List<EntityDisambiguation> findByEntityName(String entityName) {
        try {
            String sql = """
                    SELECT id, entity_name, canonical_name, similarity_score,
                           disambiguation_rule, entity_type, context_info, created_at
                    FROM entity_disambiguation
                    WHERE entity_name = ?
                    ORDER BY similarity_score DESC, created_at DESC
                    """;

            return jdbcTemplate.query(sql,
                    new BeanPropertyRowMapper<>(EntityDisambiguation.class),
                    entityName);

        } catch (Exception e) {
            log.error("查找实体消歧义记录失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 根据实体名称和类型查找消歧义记录
     */
    public List<EntityDisambiguation> findByEntityNameAndType(String entityName,
            EntityDisambiguation.EntityType entityType) {
        String sql = """
                SELECT * FROM entity_disambiguation
                WHERE entity_name = ? AND entity_type = ?
                ORDER BY similarity_score DESC, created_at DESC
                """;

        return jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(EntityDisambiguation.class),
                entityName, entityType.name());
    }

    /**
     * 查找最佳匹配的消歧义记录
     */
    public Optional<EntityDisambiguation> findBestMatch(String entityName, double minSimilarity) {
        try {
            String sql = """
                    SELECT id, entity_name, canonical_name, similarity_score,
                           disambiguation_rule, entity_type, context_info, created_at
                    FROM entity_disambiguation
                    WHERE entity_name = ? AND similarity_score >= ?
                    ORDER BY similarity_score DESC, created_at DESC
                    LIMIT 1
                    """;

            List<EntityDisambiguation> results = jdbcTemplate.query(sql,
                    new BeanPropertyRowMapper<>(EntityDisambiguation.class),
                    entityName, BigDecimal.valueOf(minSimilarity));

            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

        } catch (Exception e) {
            log.error("查找最佳匹配记录失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查询消歧义统计信息
     */
    public long countByEntityType(EntityDisambiguation.EntityType entityType) {
        String sql = "SELECT COUNT(*) FROM entity_disambiguation WHERE entity_type = ?";

        Long count = jdbcTemplate.queryForObject(sql, Long.class, entityType.name());
        return count != null ? count : 0L;
    }

    /**
     * 查询总消歧义记录数
     */
    public long countAll() {
        try {
            String sql = "SELECT COUNT(*) FROM entity_disambiguation";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("统计消歧义记录数失败: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 查询总消歧义记录数 - JPA风格的方法名
     */
    public long count() {
        return countAll();
    }

    /**
     * 删除旧的消歧义记录（数据清理）
     */
    public int deleteOldRecords(int daysToKeep) {
        String sql = """
                DELETE FROM entity_disambiguation
                WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)
                """;

        return jdbcTemplate.update(sql, daysToKeep);
    }

    /**
     * 查询高相似度的消歧义记录
     */
    public List<EntityDisambiguation> findHighSimilarityRecords(double minSimilarity) {
        String sql = """
                SELECT * FROM entity_disambiguation
                WHERE similarity_score >= ?
                ORDER BY created_at DESC
                LIMIT 100
                """;

        return jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(EntityDisambiguation.class),
                BigDecimal.valueOf(minSimilarity));
    }
}
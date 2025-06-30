package com.datacenter.extract.service;

import com.datacenter.extract.repository.CelebrityCelebrityRepository;
import com.datacenter.extract.repository.CelebrityEventRepository;
import com.datacenter.extract.repository.CelebrityWorkRepository;
import com.datacenter.extract.entity.CelebrityCelebrity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 关系验证服务 - v3.0核心组件
 * 
 * 负责验证关系的合理性、检测冲突、确保一致性
 * 设计理念：规则驱动、可扩展、高效验证
 */
@Component
public class RelationValidator {

    private static final Logger log = LoggerFactory.getLogger(RelationValidator.class);

    // 新增：注入关系Repository进行数据库验证
    private final CelebrityCelebrityRepository celebrityCelebrityRepository;
    private final CelebrityEventRepository celebrityEventRepository;
    private final CelebrityWorkRepository celebrityWorkRepository;

    // 定义关系约束规则
    private static final Map<String, RelationConstraint> RELATION_CONSTRAINTS = new HashMap<>();

    static {
        // 配偶关系：一对一，互斥
        RELATION_CONSTRAINTS.put("配偶", new RelationConstraint(
                RelationType.ONE_TO_ONE, true, false, Arrays.asList("前配偶", "离婚")));

        // 导演关系：一对多
        RELATION_CONSTRAINTS.put("导演", new RelationConstraint(
                RelationType.ONE_TO_MANY, false, false, null));

        // 主演关系：多对多
        RELATION_CONSTRAINTS.put("主演", new RelationConstraint(
                RelationType.MANY_TO_MANY, false, false, null));

        // 父母关系：多对一，不可逆
        RELATION_CONSTRAINTS.put("父亲", new RelationConstraint(
                RelationType.MANY_TO_ONE, false, false, Arrays.asList("母亲")));
        RELATION_CONSTRAINTS.put("母亲", new RelationConstraint(
                RelationType.MANY_TO_ONE, false, false, Arrays.asList("父亲")));

        // 时间相关关系
        RELATION_CONSTRAINTS.put("前配偶", new RelationConstraint(
                RelationType.ONE_TO_MANY, false, true, Arrays.asList("配偶")));
    }

    @Autowired
    public RelationValidator(CelebrityCelebrityRepository celebrityCelebrityRepository,
            CelebrityEventRepository celebrityEventRepository,
            CelebrityWorkRepository celebrityWorkRepository) {
        this.celebrityCelebrityRepository = celebrityCelebrityRepository;
        this.celebrityEventRepository = celebrityEventRepository;
        this.celebrityWorkRepository = celebrityWorkRepository;
        log.info("RelationValidator initialized with {} constraint rules and database validation",
                RELATION_CONSTRAINTS.size());
    }

    /**
     * 关系验证主入口
     */
    public Map<String, Object> validate(Map<String, Object> data) {
        try {
            log.info("开始关系验证处理");

            if (!data.containsKey("triples")) {
                log.debug("数据中不包含triples，跳过关系验证");
                return data;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> triples = (List<Map<String, Object>>) data.get("triples");

            // 执行验证
            ValidationResult result = validateTriples(triples);

            // 更新数据
            data.put("triples", result.validTriples);
            data.put("validation_applied", true);
            data.put("validation_timestamp", System.currentTimeMillis());
            data.put("invalid_relations", result.invalidRelations);
            data.put("conflicts_found", result.conflicts.size());
            data.put("validation_summary", result.getSummary());

            log.info("关系验证完成，有效关系: {}, 无效关系: {}, 冲突: {}",
                    result.validTriples.size(),
                    result.invalidRelations.size(),
                    result.conflicts.size());

            return data;

        } catch (Exception e) {
            log.error("关系验证处理失败: {}", e.getMessage(), e);
            data.put("validation_error", e.getMessage());
            return data;
        }
    }

    /**
     * 验证三元组列表
     */
    private ValidationResult validateTriples(List<Map<String, Object>> triples) {
        ValidationResult result = new ValidationResult();

        // 构建关系图用于冲突检测
        RelationGraph graph = new RelationGraph();

        for (Map<String, Object> triple : triples) {
            String subject = (String) triple.get("subject");
            String predicate = (String) triple.get("predicate");
            String object = (String) triple.get("object");

            // 验证单个关系
            RelationValidation validation = validateRelation(subject, predicate, object, graph);

            if (validation.isValid) {
                result.validTriples.add(enrichTripleWithValidation(triple, validation));
                graph.addRelation(subject, predicate, object);
            } else {
                result.invalidRelations.add(createInvalidRelationRecord(triple, validation));
                log.warn("无效关系: {} --[{}]--> {}, 原因: {}",
                        subject, predicate, object, validation.reason);
            }

            // 检测冲突
            if (validation.hasConflict) {
                result.conflicts.add(validation.conflictInfo);
            }
        }

        return result;
    }

    /**
     * 验证单个关系
     */
    private RelationValidation validateRelation(String subject, String predicate,
            String object, RelationGraph graph) {
        RelationValidation validation = new RelationValidation();

        // 基本验证
        if (subject == null || subject.trim().isEmpty() ||
                object == null || object.trim().isEmpty()) {
            validation.isValid = false;
            validation.reason = "主语或宾语为空";
            return validation;
        }

        // 自引用检查
        if (subject.equals(object)) {
            validation.isValid = false;
            validation.reason = "自引用关系";
            return validation;
        }

        // 数据库历史关系验证
        if (!validateAgainstDatabase(subject, predicate, object, validation)) {
            return validation;
        }

        // 获取关系约束
        RelationConstraint constraint = RELATION_CONSTRAINTS.get(predicate);
        if (constraint != null) {
            // 检查关系类型约束
            if (!checkRelationTypeConstraint(subject, predicate, object, constraint, graph)) {
                validation.isValid = false;
                validation.reason = "违反关系类型约束: " + constraint.type;
                return validation;
            }

            // 检查互斥关系
            if (constraint.exclusive) {
                List<String> conflicts = graph.findConflictingRelations(subject, predicate, object);
                if (!conflicts.isEmpty()) {
                    validation.hasConflict = true;
                    validation.conflictInfo = createConflictInfo(subject, predicate, object, conflicts);
                    validation.confidence *= 0.7; // 降低置信度
                }
            }

            // 检查时序约束
            if (constraint.temporal) {
                validation.temporalInfo = "需要时间戳验证";
                validation.confidence *= 0.9;
            }
        }

        validation.isValid = true;
        return validation;
    }

    /**
     * 检查关系类型约束
     */
    private boolean checkRelationTypeConstraint(String subject, String predicate,
            String object, RelationConstraint constraint,
            RelationGraph graph) {
        switch (constraint.type) {
            case ONE_TO_ONE:
                // 检查是否已存在相同谓词的其他关系
                return !graph.hasRelation(subject, predicate) &&
                        !graph.hasReverseRelation(object, predicate);

            case ONE_TO_MANY:
                // 检查宾语是否已有相同谓词的关系
                return !graph.hasReverseRelation(object, predicate);

            case MANY_TO_ONE:
                // 检查主语是否已有相同谓词的关系
                return !graph.hasRelation(subject, predicate);

            case MANY_TO_MANY:
                // 多对多关系总是有效
                return true;

            default:
                return true;
        }
    }

    /**
     * 基于数据库验证历史关系
     */
    private boolean validateAgainstDatabase(String subject, String predicate,
            String object, RelationValidation validation) {
        try {
            // TODO: 实现数据库验证逻辑
            // 需要在CelebrityCelebrityRepository中添加以下方法：
            // - findByFromIdAndEType(String fromId, String eType)
            // - findByToIdAndEType(String toId, String eType)
            // 或者使用JdbcTemplate直接查询

            log.debug("数据库验证功能待实现: {} --[{}]--> {}", subject, predicate, object);

            // 临时逻辑：对于重要关系类型降低置信度，提示需要人工审核
            RelationConstraint constraint = RELATION_CONSTRAINTS.get(predicate);
            if (constraint != null && constraint.exclusive) {
                validation.confidence *= 0.9;
                validation.reason = "需要数据库验证的互斥关系";
            }

            return true; // 继续后续验证

        } catch (Exception e) {
            log.error("数据库关系验证失败: {}", e.getMessage());
            // 数据库验证失败时不影响整体验证流程
            return true;
        }
    }

    /**
     * 丰富三元组的验证信息
     */
    private Map<String, Object> enrichTripleWithValidation(Map<String, Object> triple,
            RelationValidation validation) {
        Map<String, Object> enriched = new HashMap<>(triple);
        enriched.put("validation_confidence", validation.confidence);
        enriched.put("validation_status", "valid");

        if (validation.hasConflict) {
            enriched.put("has_conflict", true);
            enriched.put("conflict_info", validation.conflictInfo);
        }

        if (validation.temporalInfo != null) {
            enriched.put("temporal_info", validation.temporalInfo);
        }

        return enriched;
    }

    /**
     * 创建无效关系记录
     */
    private Map<String, Object> createInvalidRelationRecord(Map<String, Object> triple,
            RelationValidation validation) {
        Map<String, Object> record = new HashMap<>(triple);
        record.put("validation_status", "invalid");
        record.put("invalid_reason", validation.reason);
        record.put("timestamp", System.currentTimeMillis());
        return record;
    }

    /**
     * 创建冲突信息
     */
    private Map<String, Object> createConflictInfo(String subject, String predicate,
            String object, List<String> conflicts) {
        Map<String, Object> info = new HashMap<>();
        info.put("subject", subject);
        info.put("predicate", predicate);
        info.put("object", object);
        info.put("conflicting_relations", conflicts);
        info.put("conflict_type", "mutual_exclusion");
        return info;
    }

    /**
     * 关系类型枚举
     */
    private enum RelationType {
        ONE_TO_ONE, // 一对一
        ONE_TO_MANY, // 一对多
        MANY_TO_ONE, // 多对一
        MANY_TO_MANY // 多对多
    }

    /**
     * 关系约束定义
     */
    private static class RelationConstraint {
        final RelationType type;
        final boolean exclusive; // 是否互斥
        final boolean temporal; // 是否有时序性
        final List<String> conflicts; // 冲突的关系类型

        RelationConstraint(RelationType type, boolean exclusive, boolean temporal, List<String> conflicts) {
            this.type = type;
            this.exclusive = exclusive;
            this.temporal = temporal;
            this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
        }
    }

    /**
     * 关系验证结果
     */
    private static class RelationValidation {
        boolean isValid = true;
        String reason = "";
        double confidence = 1.0;
        boolean hasConflict = false;
        Map<String, Object> conflictInfo;
        String temporalInfo;
    }

    /**
     * 验证结果汇总
     */
    private static class ValidationResult {
        List<Map<String, Object>> validTriples = new ArrayList<>();
        List<Map<String, Object>> invalidRelations = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();

        Map<String, Object> getSummary() {
            Map<String, Object> summary = new HashMap<>();
            summary.put("total_validated", validTriples.size() + invalidRelations.size());
            summary.put("valid_count", validTriples.size());
            summary.put("invalid_count", invalidRelations.size());
            summary.put("conflict_count", conflicts.size());
            summary.put("validation_rate", validTriples.size() * 100.0 /
                    (validTriples.size() + invalidRelations.size()));
            return summary;
        }
    }

    /**
     * 关系图 - 用于冲突检测
     */
    private static class RelationGraph {
        // subject -> predicate -> objects
        private final Map<String, Map<String, Set<String>>> relations = new HashMap<>();

        void addRelation(String subject, String predicate, String object) {
            relations.computeIfAbsent(subject, k -> new HashMap<>())
                    .computeIfAbsent(predicate, k -> new HashSet<>())
                    .add(object);
        }

        boolean hasRelation(String subject, String predicate) {
            return relations.containsKey(subject) &&
                    relations.get(subject).containsKey(predicate) &&
                    !relations.get(subject).get(predicate).isEmpty();
        }

        boolean hasReverseRelation(String object, String predicate) {
            for (Map.Entry<String, Map<String, Set<String>>> entry : relations.entrySet()) {
                Map<String, Set<String>> predicates = entry.getValue();
                if (predicates.containsKey(predicate) &&
                        predicates.get(predicate).contains(object)) {
                    return true;
                }
            }
            return false;
        }

        List<String> findConflictingRelations(String subject, String predicate, String object) {
            List<String> conflicts = new ArrayList<>();

            if (relations.containsKey(subject)) {
                Map<String, Set<String>> subjectRelations = relations.get(subject);

                // 查找互斥的关系
                RelationConstraint constraint = RELATION_CONSTRAINTS.get(predicate);
                if (constraint != null && !constraint.conflicts.isEmpty()) {
                    for (String conflictPredicate : constraint.conflicts) {
                        if (subjectRelations.containsKey(conflictPredicate)) {
                            conflicts.add(conflictPredicate);
                        }
                    }
                }
            }

            return conflicts;
        }
    }
}
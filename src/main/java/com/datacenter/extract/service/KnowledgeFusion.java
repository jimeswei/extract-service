package com.datacenter.extract.service;

import com.datacenter.extract.entity.Celebrity;
import com.datacenter.extract.entity.Work;
import com.datacenter.extract.entity.Event;
import com.datacenter.extract.repository.CelebrityRepository;
import com.datacenter.extract.repository.WorkRepository;
import com.datacenter.extract.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 知识融合服务 - v3.0核心组件
 * 
 * 负责多源知识的智能合并、冲突解决和属性补全
 * 设计理念：策略驱动、规则可配、质量优先
 */
@Component
public class KnowledgeFusion {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFusion.class);

    private final CelebrityRepository celebrityRepository;
    private final WorkRepository workRepository;
    private final EventRepository eventRepository;

    @Autowired
    public KnowledgeFusion(CelebrityRepository celebrityRepository,
            WorkRepository workRepository,
            EventRepository eventRepository) {
        this.celebrityRepository = celebrityRepository;
        this.workRepository = workRepository;
        this.eventRepository = eventRepository;
        log.info("KnowledgeFusion service initialized");
    }

    /**
     * 知识融合主入口
     */
    public Map<String, Object> fuseKnowledge(Map<String, Object> data) {
        try {
            log.info("开始知识融合处理");

            if (!data.containsKey("triples")) {
                log.debug("数据中不包含triples，跳过融合处理");
                return data;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> triples = (List<Map<String, Object>>) data.get("triples");
            List<Map<String, Object>> fusedTriples = new ArrayList<>();

            // 按实体分组
            Map<String, List<Map<String, Object>>> entityGroups = groupByEntity(triples);

            // 对每个实体进行融合
            for (Map.Entry<String, List<Map<String, Object>>> entry : entityGroups.entrySet()) {
                String entityName = entry.getKey();
                List<Map<String, Object>> entityTriples = entry.getValue();

                // 融合同一实体的多个三元组
                List<Map<String, Object>> fused = fuseEntityTriples(entityName, entityTriples);
                fusedTriples.addAll(fused);
            }

            data.put("triples", fusedTriples);
            data.put("fusion_applied", true);
            data.put("fusion_timestamp", System.currentTimeMillis());
            data.put("original_triple_count", triples.size());
            data.put("fused_triple_count", fusedTriples.size());

            log.info("知识融合完成，原始三元组: {}, 融合后: {}",
                    triples.size(), fusedTriples.size());

            return data;

        } catch (Exception e) {
            log.error("知识融合处理失败: {}", e.getMessage(), e);
            data.put("fusion_error", e.getMessage());
            return data;
        }
    }

    /**
     * 按实体分组三元组
     */
    private Map<String, List<Map<String, Object>>> groupByEntity(List<Map<String, Object>> triples) {
        Map<String, List<Map<String, Object>>> groups = new HashMap<>();

        for (Map<String, Object> triple : triples) {
            String subject = (String) triple.get("subject");
            String object = (String) triple.get("object");

            // 主语分组
            groups.computeIfAbsent(subject, k -> new ArrayList<>()).add(triple);

            // 宾语也可能是实体，也需要分组
            if (isEntity(object)) {
                Map<String, Object> reversedTriple = new HashMap<>(triple);
                reversedTriple.put("subject", object);
                reversedTriple.put("object", subject);
                reversedTriple.put("predicate", "reverse_" + triple.get("predicate"));
                groups.computeIfAbsent(object, k -> new ArrayList<>()).add(reversedTriple);
            }
        }

        return groups;
    }

    /**
     * 融合同一实体的多个三元组
     */
    private List<Map<String, Object>> fuseEntityTriples(String entityName,
            List<Map<String, Object>> entityTriples) {
        List<Map<String, Object>> fusedTriples = new ArrayList<>();

        // 按谓词分组
        Map<String, List<Map<String, Object>>> predicateGroups = new HashMap<>();
        for (Map<String, Object> triple : entityTriples) {
            String predicate = (String) triple.get("predicate");
            predicateGroups.computeIfAbsent(predicate, k -> new ArrayList<>()).add(triple);
        }

        // 对每个谓词进行融合
        for (Map.Entry<String, List<Map<String, Object>>> entry : predicateGroups.entrySet()) {
            String predicate = entry.getKey();
            List<Map<String, Object>> samePredicateTriples = entry.getValue();

            if (samePredicateTriples.size() == 1) {
                // 只有一个，直接使用
                fusedTriples.add(samePredicateTriples.get(0));
            } else {
                // 多个相同谓词的三元组，需要融合
                Map<String, Object> fusedTriple = fuseMultipleTriples(entityName, predicate,
                        samePredicateTriples);
                fusedTriples.add(fusedTriple);
            }
        }

        return fusedTriples;
    }

    /**
     * 融合多个相同谓词的三元组
     */
    private Map<String, Object> fuseMultipleTriples(String entityName, String predicate,
            List<Map<String, Object>> triples) {
        Map<String, Object> fusedTriple = new HashMap<>();
        fusedTriple.put("subject", entityName);
        fusedTriple.put("predicate", predicate);

        // 收集所有宾语
        List<String> objects = new ArrayList<>();
        double totalConfidence = 0.0;
        int count = 0;

        for (Map<String, Object> triple : triples) {
            String object = (String) triple.get("object");
            if (!objects.contains(object)) {
                objects.add(object);
            }

            // 累加置信度
            Object conf = triple.get("subject_confidence");
            if (conf instanceof Number) {
                totalConfidence += ((Number) conf).doubleValue();
                count++;
            }
        }

        // 融合策略
        if (objects.size() == 1) {
            // 所有三元组的宾语相同，直接使用
            fusedTriple.put("object", objects.get(0));
        } else {
            // 多个不同的宾语，需要选择或合并
            String fusedObject = selectBestObject(predicate, objects);
            fusedTriple.put("object", fusedObject);
            fusedTriple.put("alternative_objects", objects);
        }

        // 计算融合后的置信度
        double fusedConfidence = count > 0 ? totalConfidence / count : 0.8;
        fusedTriple.put("subject_confidence", fusedConfidence);
        fusedTriple.put("fusion_method", "multi_source_average");
        fusedTriple.put("source_count", triples.size());

        return fusedTriple;
    }

    /**
     * 选择最佳宾语
     */
    private String selectBestObject(String predicate, List<String> objects) {
        // 简化实现：选择最长的（通常信息更完整）
        return objects.stream()
                .max(Comparator.comparingInt(String::length))
                .orElse(objects.get(0));
    }

    /**
     * 判断是否为实体（而非属性值）
     */
    private boolean isEntity(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        // 简单规则：包含中文且长度大于1的可能是实体
        return value.matches(".*[\u4e00-\u9fa5]+.*") && value.length() > 1;
    }

    /**
     * 融合实体属性 - 用于数据库层面的实体融合
     */
    public Celebrity fuseCelebrityAttributes(Celebrity existing, Celebrity newData) {
        try {
            boolean updated = false;

            // 属性补全策略
            if (existing.getNationality() == null && newData.getNationality() != null) {
                existing.setNationality(newData.getNationality());
                updated = true;
            }
            if (existing.getBirthdate() == null && newData.getBirthdate() != null) {
                existing.setBirthdate(newData.getBirthdate());
                updated = true;
            }
            if (existing.getGender() == null && newData.getGender() != null) {
                existing.setGender(newData.getGender());
                updated = true;
            }
            if (existing.getProfession() == null && newData.getProfession() != null) {
                existing.setProfession(newData.getProfession());
                updated = true;
            }
            if (existing.getSpouse() == null && newData.getSpouse() != null) {
                existing.setSpouse(newData.getSpouse());
                updated = true;
            }
            if (existing.getCompany() == null && newData.getCompany() != null) {
                existing.setCompany(newData.getCompany());
                updated = true;
            }
            if (existing.getEducation() == null && newData.getEducation() != null) {
                existing.setEducation(newData.getEducation());
                updated = true;
            }

            // 更新置信度和版本
            if (updated) {
                // 置信度融合：加权平均
                BigDecimal existingScore = existing.getConfidenceScore() != null ? existing.getConfidenceScore()
                        : BigDecimal.valueOf(0.8);
                BigDecimal newScore = newData.getConfidenceScore() != null ? newData.getConfidenceScore()
                        : BigDecimal.valueOf(0.8);

                BigDecimal fusedScore = existingScore.add(newScore)
                        .divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
                existing.setConfidenceScore(fusedScore);

                // 版本递增
                existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);

                log.info("成功融合实体属性: {}, 版本: {}, 置信度: {}",
                        existing.getName(), existing.getVersion(), fusedScore);
            }

            return existing;

        } catch (Exception e) {
            log.error("融合实体属性失败: {}", e.getMessage());
            return existing;
        }
    }

    /**
     * 融合作品属性
     */
    public Work fuseWorkAttributes(Work existing, Work newData) {
        try {
            boolean updated = false;

            // 属性补全策略
            if (existing.getWorkType() == null && newData.getWorkType() != null) {
                existing.setWorkType(newData.getWorkType());
                updated = true;
            }
            if (existing.getReleaseDate() == null && newData.getReleaseDate() != null) {
                existing.setReleaseDate(newData.getReleaseDate());
                updated = true;
            }
            if (existing.getPlatform() == null && newData.getPlatform() != null) {
                existing.setPlatform(newData.getPlatform());
                updated = true;
            }
            if (existing.getAwards() == null && newData.getAwards() != null) {
                existing.setAwards(newData.getAwards());
                updated = true;
            }
            if (existing.getDescription() == null && newData.getDescription() != null) {
                existing.setDescription(newData.getDescription());
                updated = true;
            }

            // 更新置信度和版本
            if (updated) {
                BigDecimal fusedScore = calculateFusedConfidence(
                        existing.getConfidenceScore(),
                        newData.getConfidenceScore());
                existing.setConfidenceScore(fusedScore);
                existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);

                log.info("成功融合作品属性: {}, 版本: {}, 置信度: {}",
                        existing.getTitle(), existing.getVersion(), fusedScore);
            }

            return existing;

        } catch (Exception e) {
            log.error("融合作品属性失败: {}", e.getMessage());
            return existing;
        }
    }

    /**
     * 计算融合后的置信度
     */
    private BigDecimal calculateFusedConfidence(BigDecimal score1, BigDecimal score2) {
        BigDecimal s1 = score1 != null ? score1 : BigDecimal.valueOf(0.8);
        BigDecimal s2 = score2 != null ? score2 : BigDecimal.valueOf(0.8);
        return s1.add(s2).divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
    }
}
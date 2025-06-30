package com.datacenter.extract.service;

import com.datacenter.extract.entity.Celebrity;
import com.datacenter.extract.entity.EntityDisambiguation;
import com.datacenter.extract.repository.CelebrityRepository;
import com.datacenter.extract.repository.EntityDisambiguationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体消歧义服务 - v3.0核心组件
 * 
 * 采用策略模式和模板方法模式，智能识别同名实体
 * 设计理念：算法与数据分离，高度可配置
 */
@Component
public class EntityDisambiguator {

    private static final Logger log = LoggerFactory.getLogger(EntityDisambiguator.class);
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;

    private final CelebrityRepository celebrityRepository;
    private final EntityDisambiguationRepository disambiguationRepository;
    private final EntitySimilarityCalculator similarityCalculator;

    @Autowired
    public EntityDisambiguator(CelebrityRepository celebrityRepository,
            EntityDisambiguationRepository disambiguationRepository) {
        this.celebrityRepository = celebrityRepository;
        this.disambiguationRepository = disambiguationRepository;
        this.similarityCalculator = new EntitySimilarityCalculator();
        log.info("EntityDisambiguator initialized successfully");
    }

    /**
     * 实体消歧义处理 - 主入口方法
     */
    public Map<String, Object> disambiguate(Map<String, Object> data) {
        try {
            log.info("开始实体消歧义处理");

            if (!data.containsKey("triples")) {
                log.debug("数据中不包含triples，跳过消歧义处理");
                return data;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> triples = (List<Map<String, Object>>) data.get("triples");
            List<Map<String, Object>> disambiguatedTriples = new ArrayList<>();

            for (Map<String, Object> triple : triples) {
                Map<String, Object> processedTriple = processTriple(triple);
                disambiguatedTriples.add(processedTriple);
            }

            data.put("triples", disambiguatedTriples);
            data.put("disambiguation_applied", true);
            data.put("disambiguation_timestamp", System.currentTimeMillis());

            log.info("实体消歧义处理完成，处理了{}个三元组", disambiguatedTriples.size());
            return data;

        } catch (Exception e) {
            log.error("实体消歧义处理失败: {}", e.getMessage(), e);
            data.put("disambiguation_error", e.getMessage());
            return data;
        }
    }

    /**
     * 处理单个三元组 - 模板方法模式
     */
    private Map<String, Object> processTriple(Map<String, Object> triple) {
        try {
            String subject = (String) triple.get("subject");
            String object = (String) triple.get("object");

            // 对主语和宾语分别进行消歧义
            DisambiguationResult subjectResult = disambiguateEntity(subject, triple);
            DisambiguationResult objectResult = disambiguateEntity(object, triple);

            // 更新三元组
            triple.put("subject", subjectResult.canonicalName);
            triple.put("object", objectResult.canonicalName);
            triple.put("subject_confidence", subjectResult.confidence);
            triple.put("object_confidence", objectResult.confidence);
            triple.put("disambiguation_applied", true);

            // 保存消歧义记录到数据库
            saveDisambiguationRecord(subject, subjectResult, triple);
            saveDisambiguationRecord(object, objectResult, triple);

            return triple;

        } catch (Exception e) {
            log.warn("处理三元组失败: {}", e.getMessage());
            triple.put("disambiguation_error", e.getMessage());
            return triple;
        }
    }

    /**
     * 保存消歧义记录到数据库
     */
    private void saveDisambiguationRecord(String originalName, DisambiguationResult result,
            Map<String, Object> context) {
        try {
            // 只有当消歧义有意义时才保存（非空且有变化）
            if (originalName != null && !originalName.trim().isEmpty() &&
                    !originalName.equals(result.canonicalName)) {

                EntityDisambiguation record = new EntityDisambiguation(
                        originalName,
                        result.canonicalName,
                        BigDecimal.valueOf(result.confidence),
                        result.reason,
                        EntityDisambiguation.EntityType.celebrity,
                        buildContextInfo(context));

                disambiguationRepository.save(record);
                log.debug("保存消歧义记录: {} -> {} (置信度: {})",
                        originalName, result.canonicalName, result.confidence);
            }

        } catch (Exception e) {
            log.warn("保存消歧义记录失败: {}", e.getMessage());
        }
    }

    /**
     * 构建上下文信息
     */
    private String buildContextInfo(Map<String, Object> context) {
        try {
            StringBuilder sb = new StringBuilder();
            String predicate = (String) context.get("predicate");
            if (predicate != null) {
                sb.append("predicate:").append(predicate);
            }
            return sb.toString();
        } catch (Exception e) {
            return "context_unavailable";
        }
    }

    /**
     * 实体消歧义核心算法 - 策略模式
     */
    private DisambiguationResult disambiguateEntity(String entityName, Map<String, Object> context) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return new DisambiguationResult(entityName, 0.0, "empty_name");
        }

        try {
            // 判断实体类型（基于上下文推断）
            EntityDisambiguation.EntityType entityType = inferEntityType(entityName, context);

            // 1. 先查询历史消歧义记录（增强版：根据类型查询）
            List<EntityDisambiguation> typeSpecificRecords = disambiguationRepository
                    .findByEntityNameAndType(entityName, entityType);

            if (!typeSpecificRecords.isEmpty()) {
                // 选择相似度最高的记录
                EntityDisambiguation bestRecord = typeSpecificRecords.stream()
                        .max((a, b) -> a.getSimilarityScore().compareTo(b.getSimilarityScore()))
                        .orElse(typeSpecificRecords.get(0));

                log.debug("使用类型特定的历史消歧义记录: {} -> {} (类型: {})",
                        entityName, bestRecord.getCanonicalName(), entityType);
                return new DisambiguationResult(
                        bestRecord.getCanonicalName(),
                        bestRecord.getSimilarityScore().doubleValue(),
                        "type_specific_history");
            }

            // 2. 如果没有类型特定记录，尝试通用查询
            Optional<EntityDisambiguation> historyRecord = disambiguationRepository.findBestMatch(entityName,
                    DEFAULT_SIMILARITY_THRESHOLD);

            if (historyRecord.isPresent()) {
                EntityDisambiguation record = historyRecord.get();
                log.debug("使用历史消歧义记录: {} -> {}", entityName, record.getCanonicalName());
                return new DisambiguationResult(
                        record.getCanonicalName(),
                        record.getSimilarityScore().doubleValue(),
                        "history_match");
            }

            // 3. 查找候选实体
            List<Celebrity> candidates = findCandidateEntities(entityName);

            if (candidates.isEmpty()) {
                return new DisambiguationResult(entityName, 1.0, "new_entity");
            }

            if (candidates.size() == 1) {
                return new DisambiguationResult(candidates.get(0).getName(), 0.95, "unique_match");
            }

            // 4. 多候选实体消歧义
            return resolveAmbiguity(entityName, candidates, context);

        } catch (Exception e) {
            log.warn("实体消歧义失败，实体: {}, 错误: {}", entityName, e.getMessage());
            return new DisambiguationResult(entityName, 0.0, "error: " + e.getMessage());
        }
    }

    /**
     * 根据上下文推断实体类型
     */
    private EntityDisambiguation.EntityType inferEntityType(String entityName, Map<String, Object> context) {
        try {
            // 基于谓词推断
            String predicate = (String) context.get("predicate");
            if (predicate != null) {
                if (predicate.contains("导演") || predicate.contains("主演") ||
                        predicate.contains("演员") || predicate.contains("歌手")) {
                    return EntityDisambiguation.EntityType.celebrity;
                }
                if (predicate.contains("作品") || predicate.contains("电影") ||
                        predicate.contains("歌曲") || predicate.contains("专辑")) {
                    return EntityDisambiguation.EntityType.work;
                }
                if (predicate.contains("活动") || predicate.contains("典礼") ||
                        predicate.contains("颁奖") || predicate.contains("发布会")) {
                    return EntityDisambiguation.EntityType.event;
                }
            }

            // 基于实体名称特征推断
            if (entityName.matches(".*[节会展赛]$") || entityName.contains("活动")) {
                return EntityDisambiguation.EntityType.event;
            }
            if (entityName.matches("《.*》") || entityName.contains("专辑") ||
                    entityName.contains("电影")) {
                return EntityDisambiguation.EntityType.work;
            }

            // 默认为人物类型
            return EntityDisambiguation.EntityType.celebrity;

        } catch (Exception e) {
            log.debug("推断实体类型失败，使用默认类型: {}", e.getMessage());
            return EntityDisambiguation.EntityType.celebrity;
        }
    }

    /**
     * 查找候选实体 - 多策略匹配
     */
    private List<Celebrity> findCandidateEntities(String entityName) {
        List<Celebrity> candidates = new ArrayList<>();

        try {
            // 精确匹配
            Optional<Celebrity> exactMatch = celebrityRepository.findByName(entityName);
            exactMatch.ifPresent(candidates::add);

            // 模糊匹配
            if (candidates.isEmpty()) {
                List<Celebrity> fuzzyMatches = celebrityRepository.findAll().stream()
                        .filter(celebrity -> celebrity.getName() != null &&
                                celebrity.getName().contains(entityName))
                        .limit(5) // 限制候选数量
                        .collect(Collectors.toList());
                candidates.addAll(fuzzyMatches);
            }

        } catch (Exception e) {
            log.warn("查找候选实体失败: {}", e.getMessage());
        }

        return candidates;
    }

    /**
     * 解决实体歧义 - 综合相似度计算
     */
    private DisambiguationResult resolveAmbiguity(String entityName, List<Celebrity> candidates,
            Map<String, Object> context) {
        Celebrity bestMatch = null;
        double maxSimilarity = 0.0;
        String reason = "similarity_based";

        for (Celebrity candidate : candidates) {
            double similarity = similarityCalculator.calculate(entityName, candidate, context);

            if (similarity > maxSimilarity && similarity > DEFAULT_SIMILARITY_THRESHOLD) {
                maxSimilarity = similarity;
                bestMatch = candidate;
            }
        }

        if (bestMatch != null) {
            return new DisambiguationResult(bestMatch.getName(), maxSimilarity, reason);
        } else {
            // 如果没有找到合适的匹配，返回原名称
            return new DisambiguationResult(entityName, 0.5, "no_suitable_match");
        }
    }

    /**
     * 消歧义结果封装类
     */
    private static class DisambiguationResult {
        final String canonicalName;
        final double confidence;
        final String reason;

        DisambiguationResult(String canonicalName, double confidence, String reason) {
            this.canonicalName = canonicalName;
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    /**
     * 实体相似度计算器 - 策略模式实现
     */
    private static class EntitySimilarityCalculator {

        /**
         * 计算实体相似度 - 多维度综合评分
         */
        public double calculate(String entityName, Celebrity candidate, Map<String, Object> context) {
            try {
                // 名称相似度 (权重: 0.6)
                double nameSimilarity = calculateNameSimilarity(entityName, candidate.getName());

                // 上下文相似度 (权重: 0.3)
                double contextSimilarity = calculateContextSimilarity(candidate, context);

                // 属性匹配度 (权重: 0.1)
                double attributeSimilarity = calculateAttributeSimilarity(candidate, context);

                // 加权综合评分
                return nameSimilarity * 0.6 + contextSimilarity * 0.3 + attributeSimilarity * 0.1;

            } catch (Exception e) {
                return 0.0;
            }
        }

        /**
         * 计算名称相似度 - 编辑距离算法
         */
        private double calculateNameSimilarity(String name1, String name2) {
            if (name1 == null || name2 == null)
                return 0.0;
            if (name1.equals(name2))
                return 1.0;

            int maxLength = Math.max(name1.length(), name2.length());
            if (maxLength == 0)
                return 1.0;

            int editDistance = levenshteinDistance(name1, name2);
            return 1.0 - (double) editDistance / maxLength;
        }

        /**
         * 计算上下文相似度 - 基于谓词和关联实体
         */
        private double calculateContextSimilarity(Celebrity candidate, Map<String, Object> context) {
            // 简化实现，可根据实际需求扩展
            return 0.5; // 默认中等相似度
        }

        /**
         * 计算属性匹配度 - 基于职业、公司等属性
         */
        private double calculateAttributeSimilarity(Celebrity candidate, Map<String, Object> context) {
            // 简化实现，可根据实际需求扩展
            return 0.5; // 默认中等匹配度
        }

        /**
         * 计算编辑距离 - 动态规划算法
         */
        private int levenshteinDistance(String s1, String s2) {
            int[][] dp = new int[s1.length() + 1][s2.length() + 1];

            for (int i = 0; i <= s1.length(); i++) {
                dp[i][0] = i;
            }

            for (int j = 0; j <= s2.length(); j++) {
                dp[0][j] = j;
            }

            for (int i = 1; i <= s1.length(); i++) {
                for (int j = 1; j <= s2.length(); j++) {
                    if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                        dp[i][j] = dp[i - 1][j - 1];
                    } else {
                        dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                    }
                }
            }

            return dp[s1.length()][s2.length()];
        }
    }
}
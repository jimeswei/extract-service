package com.datacenter.extract.service;

import com.datacenter.extract.entity.EntityDisambiguation;
import com.datacenter.extract.entity.KnowledgeQuality;
import com.datacenter.extract.repository.EntityDisambiguationRepository;
import com.datacenter.extract.repository.KnowledgeQualityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

/**
 * 数据维护服务 - 负责定时清理和质量监控
 * 
 * 执行定期数据清理、质量评估和报告生成
 */
@Service
public class DataMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(DataMaintenanceService.class);

    private final EntityDisambiguationRepository disambiguationRepository;
    private final KnowledgeQualityRepository qualityRepository;

    @Autowired
    public DataMaintenanceService(EntityDisambiguationRepository disambiguationRepository,
            KnowledgeQualityRepository qualityRepository) {
        this.disambiguationRepository = disambiguationRepository;
        this.qualityRepository = qualityRepository;
        log.info("DataMaintenanceService initialized");
    }

    /**
     * 每天凌晨2点执行数据清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldDisambiguationRecords() {
        try {
            log.info("开始执行消歧义记录清理任务");

            // 删除30天前的旧记录
            int deletedCount = disambiguationRepository.deleteOldRecords(30);

            log.info("成功清理 {} 条旧的消歧义记录", deletedCount);

        } catch (Exception e) {
            log.error("清理消歧义记录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每小时执行一次质量监控
     */
    @Scheduled(fixedDelay = 3600000) // 1小时
    public void monitorDataQuality() {
        try {
            log.info("开始执行数据质量监控");

            // 1. 检查高相似度的消歧义记录
            List<EntityDisambiguation> highSimilarityRecords = disambiguationRepository.findHighSimilarityRecords(0.9);

            if (!highSimilarityRecords.isEmpty()) {
                log.info("发现 {} 条高相似度消歧义记录（相似度>=0.9）", highSimilarityRecords.size());
            }

            // 2. 统计各类型实体的消歧义情况
            Map<String, Long> disambiguationStats = new HashMap<>();
            for (EntityDisambiguation.EntityType type : EntityDisambiguation.EntityType.values()) {
                long count = disambiguationRepository.countByEntityType(type);
                disambiguationStats.put(type.name(), count);
            }

            log.info("消歧义统计: {}", disambiguationStats);

            // 3. 分析常见实体名称的消歧义情况
            analyzeCommonEntityDisambiguation();

            // 4. 检查低质量实体
            List<KnowledgeQuality> lowQualityEntities = qualityRepository.findLowQualityEntities(0.6);

            if (!lowQualityEntities.isEmpty()) {
                log.warn("发现 {} 个低质量实体（质量分数<0.6），需要人工审核", lowQualityEntities.size());

                // 记录前5个低质量实体供审核
                lowQualityEntities.stream()
                        .limit(5)
                        .forEach(entity -> {
                            log.warn("低质量实体: {} {}, 分数: {}, 问题: {}",
                                    entity.getEntityType(),
                                    entity.getEntityId(),
                                    entity.getQualityScore(),
                                    entity.getIssuesFound());
                        });
            }

            // 5. 计算并报告平均质量分数
            double avgQualityScore = qualityRepository.getAverageQualityScore();
            log.info("知识图谱平均质量分数: {}", String.format("%.2f", avgQualityScore));

            // 6. 统计各质量等级分布
            Map<String, Long> gradeDistribution = new HashMap<>();
            for (KnowledgeQuality.QualityGrade grade : KnowledgeQuality.QualityGrade.values()) {
                long count = qualityRepository.countByQualityGrade(grade);
                gradeDistribution.put(grade.name(), count);
            }

            log.info("质量等级分布: {}", gradeDistribution);

        } catch (Exception e) {
            log.error("数据质量监控失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取数据质量报告
     */
    public Map<String, Object> getQualityReport() {
        Map<String, Object> report = new HashMap<>();

        try {
            // 消歧义统计
            Map<String, Long> disambiguationStats = new HashMap<>();
            for (EntityDisambiguation.EntityType type : EntityDisambiguation.EntityType.values()) {
                long count = disambiguationRepository.countByEntityType(type);
                disambiguationStats.put(type.name(), count);
            }
            report.put("disambiguation_stats", disambiguationStats);
            report.put("total_disambiguations", disambiguationRepository.count());

            // 质量统计
            double avgQualityScore = qualityRepository.getAverageQualityScore();
            report.put("average_quality_score", avgQualityScore);

            // 质量等级分布
            Map<String, Long> gradeDistribution = new HashMap<>();
            for (KnowledgeQuality.QualityGrade grade : KnowledgeQuality.QualityGrade.values()) {
                long count = qualityRepository.countByQualityGrade(grade);
                gradeDistribution.put(grade.name(), count);
            }
            report.put("quality_grade_distribution", gradeDistribution);

            // 低质量实体数
            List<KnowledgeQuality> lowQualityEntities = qualityRepository.findLowQualityEntities(0.6);
            report.put("low_quality_entity_count", lowQualityEntities.size());

            report.put("report_time", System.currentTimeMillis());
            report.put("status", "success");

        } catch (Exception e) {
            log.error("生成质量报告失败: {}", e.getMessage(), e);
            report.put("status", "error");
            report.put("error", e.getMessage());
        }

        return report;
    }

    /**
     * 分析常见实体名称的消歧义情况
     */
    private void analyzeCommonEntityDisambiguation() {
        // 定义一些常见的需要消歧义的名称
        List<String> commonAmbiguousNames = Arrays.asList("张伟", "李娜", "王强", "刘洋", "陈静");

        for (String name : commonAmbiguousNames) {
            List<EntityDisambiguation> records = disambiguationRepository.findByEntityName(name);

            if (!records.isEmpty()) {
                // 统计该名称被消歧义到多少个不同的标准名称
                long uniqueCanonicalNames = records.stream()
                        .map(EntityDisambiguation::getCanonicalName)
                        .distinct()
                        .count();

                // 计算平均相似度分数
                double avgSimilarity = records.stream()
                        .mapToDouble(r -> r.getSimilarityScore().doubleValue())
                        .average()
                        .orElse(0.0);

                log.info("实体名称 '{}' 的消歧义分析: 记录数={}, 不同标准名称数={}, 平均相似度={}",
                        name, records.size(), uniqueCanonicalNames,
                        String.format("%.2f", avgSimilarity));

                // 如果一个名称被消歧义到太多不同的标准名称，可能需要人工审核
                if (uniqueCanonicalNames > 5) {
                    log.warn("实体名称 '{}' 可能存在过度消歧义问题，建议人工审核", name);
                }
            }
        }
    }
}
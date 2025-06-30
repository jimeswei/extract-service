package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.OptimizedBusinessService;
import com.datacenter.extract.service.AsyncTaskMonitor;
import com.datacenter.extract.service.TextExtractionService;
import com.datacenter.extract.service.DataMaintenanceService;
import com.datacenter.extract.util.ResponseBuilder;
import com.datacenter.extract.entity.Celebrity;
import com.datacenter.extract.entity.KnowledgeQuality;
import com.datacenter.extract.entity.EntityDisambiguation;
import com.datacenter.extract.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.Arrays;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 异步提取控制器 - 企业级优化版本 v5.0
 * 
 * 🏗️ 优化重点：
 * 1. 统一业务编排：完全使用OptimizedBusinessService
 * 2. 极简代码逻辑：消除重复代码，符合DRY原则
 * 3. 统一响应格式：使用ResponseBuilder标准化响应
 * 4. 增强监控集成：全链路监控
 * 5. 优雅异常处理：分层异常处理机制
 * 
 * 🚀 设计模式应用：
 * - 门面模式(Facade)：统一业务入口
 * - 建造者模式：ResponseBuilder标准化响应
 * - 策略模式：不同处理模式切换
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class AsyncExtractController {

    private static final Logger log = LoggerFactory.getLogger(AsyncExtractController.class);

    @Autowired
    private OptimizedBusinessService businessService;

    @Autowired
    private TextExtractionService textExtractionService;

    @Autowired
    private CelebrityRepository celebrityRepository;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CelebrityCelebrityRepository celebrityCelebrityRepository;

    @Autowired
    private CelebrityWorkRepository celebrityWorkRepository;

    @Autowired
    private CelebrityEventRepository celebrityEventRepository;

    @Autowired
    private EventWorkRepository eventWorkRepository;

    @Autowired
    private KnowledgeQualityRepository qualityRepository;

    @Autowired
    private EntityDisambiguationRepository disambiguationRepository;

    @Autowired
    private DataMaintenanceService dataMaintenanceService;

    private final AsyncTaskMonitor taskMonitor;

    @Autowired
    public AsyncExtractController(AsyncTaskMonitor taskMonitor) {
        this.taskMonitor = taskMonitor;
        log.info("AsyncExtractController initialized with enterprise-grade business service");
    }

    /**
     * 统一文本提取接口 - v3.0增强版
     * 支持知识图谱处理模式
     */
    @PostMapping("/extract")
    public JSONObject extractAsync(@RequestBody JSONObject request) {
        try {
            // 提取参数
            String textInput = request.getString("textInput");
            String extractParams = request.getString("extractParams");

            // v3.0新增：知识图谱处理模式
            String kgMode = request.getString("kgMode");
            if (kgMode == null || kgMode.trim().isEmpty()) {
                kgMode = "standard";
            }

            // 参数验证
            if (textInput == null || textInput.trim().isEmpty()) {
                return ResponseBuilder.error("textInput不能为空").build();
            }

            // 默认提取参数
            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples";
            }

            // 验证kgMode参数
            if (!Arrays.asList("standard", "enhanced", "fusion").contains(kgMode)) {
                log.warn("无效的kgMode参数: {}, 使用默认值: standard", kgMode);
                kgMode = "standard";
            }

            log.info("收到提取请求，文本长度: {}, 提取参数: {}, KG模式: {}",
                    textInput.length(), extractParams, kgMode);

            // 提交异步任务 - v3.0使用增强版方法
            textExtractionService.processTextAsync(textInput, extractParams, kgMode);

            // 立即返回成功响应
            return ResponseBuilder.success("任务已提交，正在智能处理中...")
                    .data("kg_mode", kgMode)
                    .data("text_length", textInput.length())
                    .data("extract_type", extractParams)
                    .build();

        } catch (Exception e) {
            log.error("处理提取请求失败", e);
            return ResponseBuilder.error("处理请求失败: " + e.getMessage()).build();
        }
    }

    /**
     * 服务信息接口 - 增强版本
     */
    @GetMapping("/info")
    public JSONObject getServiceInfo() {
        return ResponseBuilder.success("智能文本提取服务 - 企业级版本")
                .data("version", "v5.0-enterprise")
                .data("features", new String[] {
                        "异步处理", "知识图谱增强", "实体消歧义", "关系验证",
                        "质量评估", "长文本分片", "智能缓存", "性能监控"
                })
                .data("supported_modes", new String[] { "standard", "enhanced", "fusion", "batch" })
                .data("max_text_length", 50000)
                .data("concurrent_processing", true)
                .data("author", "Enterprise Development Team")
                .build();
    }

    /**
     * 健康检查接口 - 企业级监控
     */
    @GetMapping("/health")
    public JSONObject healthCheck() {
        try {
            var monitorStats = taskMonitor.getMonitorStats();
            double successRate = (Double) monitorStats.get("success_rate");
            boolean systemHealthy = successRate > 80.0;

            String message = systemHealthy ? "系统运行正常" : "系统性能下降";
            String status = systemHealthy ? "healthy" : "degraded";

            return ResponseBuilder.success(message)
                    .data("system_status", status)
                    .data("success_rate", successRate + "%")
                    .data("active_tasks", taskMonitor.getActiveTasks().size())
                    .data("memory_usage", getMemoryUsage())
                    .data("uptime", getSystemUptime())
                    .metrics("total_tasks", monitorStats.get("total_tasks"))
                    .metrics("success_tasks", monitorStats.get("success_tasks"))
                    .metrics("failed_tasks", monitorStats.get("failed_tasks"))
                    .build();

        } catch (Exception e) {
            log.error("健康检查失败: {}", e.getMessage());
            return ResponseBuilder.error("健康检查失败")
                    .data("system_status", "unhealthy")
                    .data("error_detail", e.getMessage())
                    .build();
        }
    }

    /**
     * 任务监控接口 - 详细统计
     */
    @GetMapping("/monitor")
    public JSONObject getMonitorStats() {
        try {
            var stats = taskMonitor.getMonitorStats();
            var activeTasks = taskMonitor.getActiveTasks();

            return ResponseBuilder.success("任务监控统计")
                    .data("total_tasks", stats.get("total_tasks"))
                    .data("success_tasks", stats.get("success_tasks"))
                    .data("failed_tasks", stats.get("failed_tasks"))
                    .data("active_tasks_count", activeTasks.size())
                    .data("success_rate", stats.get("success_rate") + "%")
                    .data("failure_rate", stats.get("failure_rate") + "%")
                    .data("avg_processing_time", stats.get("avg_processing_time") + "ms")
                    .data("system_load", getSystemLoad())
                    .build();
        } catch (Exception e) {
            log.error("获取监控统计失败: {}", e.getMessage());
            return ResponseBuilder.error("获取监控统计失败: " + e.getMessage()).build();
        }
    }

    /**
     * 清理超时任务接口 - 运维接口
     */
    @PostMapping("/admin/cleanup")
    public JSONObject cleanupTimeoutTasks() {
        try {
            taskMonitor.cleanupTimeoutTasks();
            return ResponseBuilder.success("超时任务清理完成")
                    .data("cleanup_time", System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("清理超时任务失败: {}", e.getMessage());
            return ResponseBuilder.error("清理失败: " + e.getMessage()).build();
        }
    }

    /**
     * 知识图谱统计接口 - v3.0新增
     */
    @GetMapping("/kg-stats")
    public JSONObject getKnowledgeGraphStats() {
        try {
            // 统计实体数量
            long celebrityCount = celebrityRepository.count();
            long workCount = workRepository.count();
            long eventCount = eventRepository.count();
            long totalEntities = celebrityCount + workCount + eventCount;

            // 统计关系数量
            long ccRelations = celebrityCelebrityRepository.count();
            long cwRelations = celebrityWorkRepository.count();
            long ceRelations = celebrityEventRepository.count();
            long ewRelations = eventWorkRepository.count();
            long totalRelations = ccRelations + cwRelations + ceRelations + ewRelations;

            // 计算平均质量分数
            BigDecimal avgQualityScore = calculateAverageQualityScore();

            // 计算消歧义率
            BigDecimal disambiguationRate = calculateDisambiguationRate();

            return ResponseBuilder.success("知识图谱统计信息")
                    .data("total_entities", totalEntities)
                    .data("total_relations", totalRelations)
                    .data("celebrity_count", celebrityCount)
                    .data("work_count", workCount)
                    .data("event_count", eventCount)
                    .data("avg_quality_score", avgQualityScore)
                    .data("disambiguation_rate", disambiguationRate)
                    .data("relation_breakdown", Map.of(
                            "celebrity_celebrity", ccRelations,
                            "celebrity_work", cwRelations,
                            "celebrity_event", ceRelations,
                            "event_work", ewRelations))
                    .data("update_time", System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("获取知识图谱统计失败: {}", e.getMessage());
            return ResponseBuilder.error("获取统计信息失败: " + e.getMessage()).build();
        }
    }

    /**
     * 实体消歧义查询接口 - v3.0新增
     */
    @GetMapping("/entity-disambiguation")
    public JSONObject queryEntityDisambiguation(@RequestParam String name) {
        try {
            // 查找候选实体
            List<Celebrity> candidates = celebrityRepository.findAll().stream()
                    .filter(c -> c.getName() != null && c.getName().contains(name))
                    .limit(10)
                    .collect(Collectors.toList());

            List<Map<String, Object>> candidateList = new ArrayList<>();
            for (Celebrity candidate : candidates) {
                Map<String, Object> info = new HashMap<>();
                info.put("id", candidate.getCelebrityId());
                info.put("name", candidate.getName());
                info.put("profession", candidate.getProfession());
                info.put("confidence", candidate.getConfidenceScore());
                info.put("version", candidate.getVersion());
                candidateList.add(info);
            }

            // 查询该名称的消歧义历史记录
            List<EntityDisambiguation> disambiguationHistory = disambiguationRepository.findByEntityName(name);

            List<Map<String, Object>> historyList = new ArrayList<>();
            for (EntityDisambiguation record : disambiguationHistory) {
                Map<String, Object> historyInfo = new HashMap<>();
                historyInfo.put("canonical_name", record.getCanonicalName());
                historyInfo.put("similarity_score", record.getSimilarityScore());
                historyInfo.put("disambiguation_rule", record.getDisambiguationRule());
                historyInfo.put("entity_type", record.getEntityType());
                historyInfo.put("context_info", record.getContextInfo());
                historyInfo.put("created_at", record.getCreatedAt());
                historyList.add(historyInfo);
            }

            return ResponseBuilder.success("实体消歧义查询结果")
                    .data("query", name)
                    .data("candidate_count", candidateList.size())
                    .data("candidates", candidateList)
                    .data("disambiguation_history_count", historyList.size())
                    .data("disambiguation_history", historyList)
                    .build();

        } catch (Exception e) {
            log.error("查询实体消歧义失败: {}", e.getMessage());
            return ResponseBuilder.error("查询失败: " + e.getMessage()).build();
        }
    }

    /**
     * 知识质量评估查询接口 - v3.0新增
     */
    @GetMapping("/knowledge-quality")
    public JSONObject queryKnowledgeQuality(@RequestParam String entityId) {
        try {
            // 查询质量评估记录
            Optional<KnowledgeQuality> qualityOpt = qualityRepository.findByEntityId(entityId);

            if (qualityOpt.isPresent()) {
                KnowledgeQuality quality = qualityOpt.get();
                return ResponseBuilder.success("知识质量评估结果")
                        .data("entity_id", entityId)
                        .data("quality_score", quality.getQualityScore())
                        .data("completeness", quality.getCompleteness())
                        .data("consistency", quality.getConsistency())
                        .data("accuracy", quality.getAccuracy())
                        .data("quality_grade", quality.getQualityGrade())
                        .data("last_assessed", quality.getLastAssessed())
                        .build();
            } else {
                return ResponseBuilder.error("未找到该实体的质量评估记录").build();
            }

        } catch (Exception e) {
            log.error("查询知识质量失败: {}", e.getMessage());
            return ResponseBuilder.error("查询失败: " + e.getMessage()).build();
        }
    }

    /**
     * 数据质量报告接口 - v3.0新增
     * 获取全面的数据质量分析报告
     */
    @GetMapping("/quality-report")
    public JSONObject getQualityReport() {
        try {
            Map<String, Object> report = dataMaintenanceService.getQualityReport();

            return ResponseBuilder.success("数据质量报告")
                    .data("report", report)
                    .data("generated_at", System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("生成数据质量报告失败: {}", e.getMessage());
            return ResponseBuilder.error("生成报告失败: " + e.getMessage()).build();
        }
    }

    // ========================= 私有辅助方法 =========================

    /**
     * 获取内存使用情况
     */
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double usagePercent = (double) (totalMemory - freeMemory) / totalMemory * 100;
        return String.format("%.1f%%", usagePercent);
    }

    /**
     * 获取系统运行时间
     */
    private String getSystemUptime() {
        long uptimeMs = System.currentTimeMillis() - startupTime;
        long hours = uptimeMs / (1000 * 60 * 60);
        long minutes = (uptimeMs % (1000 * 60 * 60)) / (1000 * 60);
        return String.format("%d小时%d分钟", hours, minutes);
    }

    /**
     * 获取系统负载
     */
    private String getSystemLoad() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return String.format("CPU核心数: %d", availableProcessors);
    }

    // 启动时间
    private static final long startupTime = System.currentTimeMillis();

    private BigDecimal calculateAverageQualityScore() {
        try {
            List<KnowledgeQuality> qualityRecords = qualityRepository.findAll();
            if (qualityRecords.isEmpty()) {
                return BigDecimal.ZERO;
            }

            double sum = qualityRecords.stream()
                    .mapToDouble(q -> q.getQualityScore().doubleValue())
                    .sum();

            return BigDecimal.valueOf(sum / qualityRecords.size())
                    .setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.warn("计算平均质量分数失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateDisambiguationRate() {
        try {
            long totalDisambiguations = disambiguationRepository.count();
            long totalEntities = celebrityRepository.count() +
                    workRepository.count() +
                    eventRepository.count();

            if (totalEntities == 0) {
                return BigDecimal.ZERO;
            }

            return BigDecimal.valueOf((double) totalDisambiguations / totalEntities)
                    .setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.warn("计算消歧义率失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
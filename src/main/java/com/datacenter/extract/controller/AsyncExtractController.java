package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.OptimizedBusinessService;
import com.datacenter.extract.service.AsyncTaskMonitor;
import com.datacenter.extract.util.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

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

    private final OptimizedBusinessService businessService;
    private final AsyncTaskMonitor taskMonitor;

    @Autowired
    public AsyncExtractController(OptimizedBusinessService businessService, AsyncTaskMonitor taskMonitor) {
        this.businessService = businessService;
        this.taskMonitor = taskMonitor;
        log.info("AsyncExtractController initialized with enterprise-grade business service");
    }

    /**
     * 统一文本提取接口 - 企业级优化版本
     * 
     * 支持四种处理模式：
     * - standard: 标准AI提取
     * - enhanced: AI + 实体消歧义 + 关系验证
     * - fusion: 完整知识图谱处理链
     * - batch: 批量处理模式
     */
    @PostMapping("/async/extract")
    public JSONObject extractAsync(@RequestBody JSONObject request) {
        long startTime = System.currentTimeMillis();

        try {
            // 参数提取和验证
            ExtractRequest extractRequest = parseAndValidateRequest(request);

            log.info("收到异步提取请求 - TextLength: {}, ExtractParams: {}, KgMode: {}",
                    extractRequest.getTextInput().length(),
                    extractRequest.getExtractParams(),
                    extractRequest.getKgMode());

            // 提交异步业务处理
            CompletableFuture<OptimizedBusinessService.BusinessResult> future = businessService.processAsync(
                    extractRequest.getTextInput(),
                    extractRequest.getExtractParams(),
                    extractRequest.getKgMode());

            // 立即返回成功响应
            return ResponseBuilder.success("任务已提交，正在后台智能处理")
                    .data("task_submitted", true)
                    .data("kg_mode", extractRequest.getKgMode())
                    .data("processing_mode", getProcessingModeDescription(extractRequest.getKgMode()))
                    .data("text_length", extractRequest.getTextInput().length())
                    .responseTime(System.currentTimeMillis() - startTime)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("请求参数错误: {}", e.getMessage());
            return ResponseBuilder.error(e.getMessage())
                    .responseTime(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.error("处理请求异常: {}", e.getMessage(), e);
            return ResponseBuilder.error("系统繁忙，请稍后重试")
                    .responseTime(System.currentTimeMillis() - startTime)
                    .build();
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

    // ========================= 私有辅助方法 =========================

    /**
     * 解析和验证请求参数
     */
    private ExtractRequest parseAndValidateRequest(JSONObject request) {
        String textInput = extractTextInput(request);
        String extractParams = request.getString("extractParams");
        if (extractParams == null || extractParams.trim().isEmpty()) {
            extractParams = "triples";
        }
        String kgMode = request.getString("kgMode");
        if (kgMode == null || kgMode.trim().isEmpty()) {
            kgMode = "standard";
        }

        // 参数验证
        if (textInput.trim().isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }

        if (textInput.length() > 50000) {
            throw new IllegalArgumentException("文本长度超过限制(50000字符)");
        }

        if (!isValidKgMode(kgMode)) {
            throw new IllegalArgumentException("不支持的知识图谱模式: " + kgMode);
        }

        return new ExtractRequest(textInput, extractParams, kgMode);
    }

    /**
     * 提取文本输入 - 支持字符串和数组格式
     */
    private String extractTextInput(JSONObject request) {
        try {
            // 尝试作为JSONArray获取
            JSONArray textInputArray = request.getJSONArray("textInput");
            if (textInputArray != null && !textInputArray.isEmpty()) {
                return textInputArray.toJSONString();
            }
        } catch (Exception ignored) {
            // 忽略异常，尝试作为字符串获取
        }

        String textInput = request.getString("textInput");
        if (textInput == null) {
            throw new IllegalArgumentException("textInput参数是必需的");
        }

        return textInput;
    }

    /**
     * 验证知识图谱模式
     */
    private boolean isValidKgMode(String kgMode) {
        return switch (kgMode.toLowerCase()) {
            case "standard", "enhanced", "fusion", "batch" -> true;
            default -> false;
        };
    }

    /**
     * 获取处理模式描述
     */
    private String getProcessingModeDescription(String kgMode) {
        return switch (kgMode.toLowerCase()) {
            case "standard" -> "标准AI提取";
            case "enhanced" -> "AI提取 + 实体消歧义 + 关系验证";
            case "fusion" -> "完整知识图谱处理(消歧义+融合+验证+质量评估)";
            case "batch" -> "批量处理模式";
            default -> "标准模式";
        };
    }

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

    // ========================= 内部类 =========================

    /**
     * 请求数据传输对象 - 值对象模式
     */
    private static class ExtractRequest {
        private final String textInput;
        private final String extractParams;
        private final String kgMode;

        public ExtractRequest(String textInput, String extractParams, String kgMode) {
            this.textInput = textInput;
            this.extractParams = extractParams;
            this.kgMode = kgMode;
        }

        public String getTextInput() {
            return textInput;
        }

        public String getExtractParams() {
            return extractParams;
        }

        public String getKgMode() {
            return kgMode;
        }
    }
}
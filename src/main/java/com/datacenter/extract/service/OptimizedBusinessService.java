package com.datacenter.extract.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Optional;

/**
 * 优化的业务服务 - 企业级业务编排中心
 * 
 * 🏗️ 设计模式应用：
 * 1. 模板方法模式：standardProcessingTemplate() - 统一业务流程框架
 * 2. 策略模式：ProcessingStrategy - 灵活的处理策略选择
 * 3. 建造者模式：ProcessingContext - 复杂对象构建
 * 4. 责任链模式：Pipeline - 流水线式处理
 * 
 * 🚀 优化亮点：
 * - 统一错误处理机制
 * - 性能监控集成
 * - 异步处理优化
 * - 缓存策略优化
 */
@Service
public class OptimizedBusinessService {

    private static final Logger log = LoggerFactory.getLogger(OptimizedBusinessService.class);

    private final SmartAIProvider aiProvider;
    private final KnowledgeGraphEngine kgEngine;
    private final DatabaseService databaseService;
    private final AsyncTaskMonitor taskMonitor;

    @Autowired
    public OptimizedBusinessService(
            SmartAIProvider aiProvider,
            KnowledgeGraphEngine kgEngine,
            DatabaseService databaseService,
            AsyncTaskMonitor taskMonitor) {
        this.aiProvider = aiProvider;
        this.kgEngine = kgEngine;
        this.databaseService = databaseService;
        this.taskMonitor = taskMonitor;
        log.info("OptimizedBusinessService initialized with enterprise-grade components");
    }

    /**
     * 统一异步处理入口 - 模板方法模式
     * 
     * @param textInput     输入文本
     * @param extractParams 提取参数
     * @param kgMode        知识图谱模式
     * @return 异步处理结果
     */
    public CompletableFuture<BusinessResult> processAsync(String textInput, String extractParams, String kgMode) {
        // 构建处理上下文
        ProcessingContext context = ProcessingContext.builder()
                .textInput(textInput)
                .extractParams(extractParams)
                .kgMode(kgMode)
                .requestId(generateRequestId())
                .startTime(System.currentTimeMillis())
                .build();

        log.info("开始业务处理 - RequestId: {}, KgMode: {}, TextLength: {}",
                context.getRequestId(), kgMode, textInput.length());

        // 记录任务开始
        taskMonitor.recordTaskStart(textInput, extractParams);

        // 选择处理策略并执行
        ProcessingStrategy strategy = selectProcessingStrategy(kgMode);

        return standardProcessingTemplate(context, strategy)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        taskMonitor.recordTaskSuccess(context.getRequestId());
                        log.info("业务处理成功 - RequestId: {}, Duration: {}ms",
                                context.getRequestId(), result.getProcessingTime());
                    } else {
                        taskMonitor.recordTaskFailed(context.getRequestId(), throwable.getMessage());
                        log.error("业务处理失败 - RequestId: {}, Error: {}",
                                context.getRequestId(), throwable.getMessage());
                    }
                });
    }

    /**
     * 标准处理模板 - 模板方法模式核心实现
     */
    private CompletableFuture<BusinessResult> standardProcessingTemplate(
            ProcessingContext initialContext, ProcessingStrategy strategy) {

        return CompletableFuture
                .supplyAsync(() -> {
                    // 阶段1: 预处理
                    ProcessingContext processedContext = strategy.preProcess(initialContext);
                    log.debug("预处理完成 - RequestId: {}", processedContext.getRequestId());
                    return processedContext;
                })
                .thenApply(ctx -> {
                    // 阶段2: AI处理
                    String aiResult = strategy.aiProcess(ctx, aiProvider);
                    ctx.setAiResult(aiResult);
                    log.debug("AI处理完成 - RequestId: {}", ctx.getRequestId());
                    return ctx;
                })
                .thenApply(ctx -> {
                    // 阶段3: 知识图谱增强
                    String enhancedResult = strategy.enhanceWithKnowledgeGraph(ctx, kgEngine);
                    ctx.setEnhancedResult(enhancedResult);
                    log.debug("知识图谱增强完成 - RequestId: {}", ctx.getRequestId());
                    return ctx;
                })
                .thenApply(ctx -> {
                    // 阶段4: 数据持久化
                    boolean saveSuccess = strategy.persistData(ctx, databaseService);
                    ctx.setSaveSuccess(saveSuccess);
                    log.debug("数据持久化完成 - RequestId: {}", ctx.getRequestId());
                    return ctx;
                })
                .thenApply(ctx -> {
                    // 阶段5: 后处理
                    return strategy.postProcess(ctx);
                })
                .handle((result, throwable) -> {
                    // 统一异常处理
                    if (throwable != null) {
                        log.error("处理异常 - RequestId: {}, Error: {}", initialContext.getRequestId(),
                                throwable.getMessage());
                        return BusinessResult.failure(initialContext.getRequestId(), throwable.getMessage());
                    }
                    return result;
                });
    }

    /**
     * 策略选择器 - 策略模式
     */
    private ProcessingStrategy selectProcessingStrategy(String kgMode) {
        return switch (Optional.ofNullable(kgMode).orElse("standard").toLowerCase()) {
            case "enhanced" -> new EnhancedProcessingStrategy();
            case "fusion" -> new FusionProcessingStrategy();
            case "batch" -> new BatchProcessingStrategy();
            default -> new StandardProcessingStrategy();
        };
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return "biz_" + System.currentTimeMillis() + "_" +
                Integer.toHexString((int) (Math.random() * 0x10000));
    }

    // ========================= 策略模式实现 =========================

    /**
     * 处理策略接口
     */
    public interface ProcessingStrategy {
        ProcessingContext preProcess(ProcessingContext context);

        String aiProcess(ProcessingContext context, SmartAIProvider aiProvider);

        String enhanceWithKnowledgeGraph(ProcessingContext context, KnowledgeGraphEngine kgEngine);

        boolean persistData(ProcessingContext context, DatabaseService databaseService);

        BusinessResult postProcess(ProcessingContext context);
    }

    /**
     * 标准处理策略
     */
    public static class StandardProcessingStrategy implements ProcessingStrategy {
        @Override
        public ProcessingContext preProcess(ProcessingContext context) {
            // 标准预处理：验证输入，设置默认参数
            if (context.getExtractParams() == null) {
                context.setExtractParams("triples");
            }
            return context;
        }

        @Override
        public String aiProcess(ProcessingContext context, SmartAIProvider aiProvider) {
            return aiProvider.process(context.getTextInput(), context.getExtractParams());
        }

        @Override
        public String enhanceWithKnowledgeGraph(ProcessingContext context, KnowledgeGraphEngine kgEngine) {
            // 标准模式不使用知识图谱增强
            return context.getAiResult();
        }

        @Override
        public boolean persistData(ProcessingContext context, DatabaseService databaseService) {
            try {
                // 批量保存优化 - 降级为普通保存
                databaseService.saveSocialData(context.getEnhancedResult());
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public BusinessResult postProcess(ProcessingContext context) {
            long duration = System.currentTimeMillis() - context.getStartTime();
            context.setProcessingTime(duration);
            return BusinessResult.success(context);
        }
    }

    /**
     * 增强处理策略
     */
    public static class EnhancedProcessingStrategy extends StandardProcessingStrategy {
        @Override
        public String enhanceWithKnowledgeGraph(ProcessingContext context, KnowledgeGraphEngine kgEngine) {
            return kgEngine.enhanceKnowledge(context.getAiResult(), "enhanced");
        }
    }

    /**
     * 融合处理策略
     */
    public static class FusionProcessingStrategy extends StandardProcessingStrategy {
        @Override
        public String enhanceWithKnowledgeGraph(ProcessingContext context, KnowledgeGraphEngine kgEngine) {
            return kgEngine.enhanceKnowledge(context.getAiResult(), "fusion");
        }
    }

    /**
     * 批处理策略
     */
    public static class BatchProcessingStrategy extends StandardProcessingStrategy {
        @Override
        public ProcessingContext preProcess(ProcessingContext context) {
            context = super.preProcess(context);
            // 批处理特殊预处理逻辑
            context.setBatchMode(true);
            return context;
        }

        @Override
        public boolean persistData(ProcessingContext context, DatabaseService databaseService) {
            try {
                // 批量保存优化 - 使用普通保存方法
                databaseService.saveSocialData(context.getEnhancedResult());
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ========================= 数据类 =========================

    /**
     * 处理上下文 - 建造者模式
     */
    public static class ProcessingContext {
        private String requestId;
        private String textInput;
        private String extractParams;
        private String kgMode;
        private long startTime;
        private String aiResult;
        private String enhancedResult;
        private boolean saveSuccess;
        private long processingTime;
        private boolean batchMode = false;
        private Map<String, Object> metadata;

        // 建造者模式
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private ProcessingContext context = new ProcessingContext();

            public Builder textInput(String textInput) {
                context.textInput = textInput;
                return this;
            }

            public Builder extractParams(String extractParams) {
                context.extractParams = extractParams;
                return this;
            }

            public Builder kgMode(String kgMode) {
                context.kgMode = kgMode;
                return this;
            }

            public Builder requestId(String requestId) {
                context.requestId = requestId;
                return this;
            }

            public Builder startTime(long startTime) {
                context.startTime = startTime;
                return this;
            }

            public ProcessingContext build() {
                return context;
            }
        }

        // Getters and Setters
        public String getRequestId() {
            return requestId;
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

        public long getStartTime() {
            return startTime;
        }

        public String getAiResult() {
            return aiResult;
        }

        public String getEnhancedResult() {
            return enhancedResult;
        }

        public boolean isSaveSuccess() {
            return saveSuccess;
        }

        public long getProcessingTime() {
            return processingTime;
        }

        public boolean isBatchMode() {
            return batchMode;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setExtractParams(String extractParams) {
            this.extractParams = extractParams;
        }

        public void setAiResult(String aiResult) {
            this.aiResult = aiResult;
        }

        public void setEnhancedResult(String enhancedResult) {
            this.enhancedResult = enhancedResult;
        }

        public void setSaveSuccess(boolean saveSuccess) {
            this.saveSuccess = saveSuccess;
        }

        public void setProcessingTime(long processingTime) {
            this.processingTime = processingTime;
        }

        public void setBatchMode(boolean batchMode) {
            this.batchMode = batchMode;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * 业务结果类 - 值对象模式
     */
    public static class BusinessResult {
        private final boolean success;
        private final String requestId;
        private final String result;
        private final String errorMessage;
        private final long processingTime;
        private final Map<String, Object> metrics;

        private BusinessResult(boolean success, String requestId, String result,
                String errorMessage, long processingTime, Map<String, Object> metrics) {
            this.success = success;
            this.requestId = requestId;
            this.result = result;
            this.errorMessage = errorMessage;
            this.processingTime = processingTime;
            this.metrics = metrics;
        }

        public static BusinessResult success(ProcessingContext context) {
            return new BusinessResult(true, context.getRequestId(), context.getEnhancedResult(),
                    null, context.getProcessingTime(), Map.of(
                            "aiProcessed", context.getAiResult() != null,
                            "kgEnhanced", !context.getAiResult().equals(context.getEnhancedResult()),
                            "dataSaved", context.isSaveSuccess(),
                            "batchMode", context.isBatchMode()));
        }

        public static BusinessResult failure(String requestId, String errorMessage) {
            return new BusinessResult(false, requestId, null, errorMessage, 0, Map.of());
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getResult() {
            return result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public long getProcessingTime() {
            return processingTime;
        }

        public Map<String, Object> getMetrics() {
            return metrics;
        }
    }
}
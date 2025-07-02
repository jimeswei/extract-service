package com.datacenter.extract.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 文本提取服务 - v4.0重构版本
 * 
 * 🚀 v4.0重构特性：
 * 1. 完全移除数据库依赖
 * 2. 基于模板的提取配置
 * 3. 文件系统输出
 * 4. 优化的异步处理
 * 5. 增强的错误处理
 * 
 * 实现MCP工具接口的核心服务，专注于智能文本提取
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final SmartAIProvider smartAIProvider;
    private final FileBasedProcessingService fileBasedProcessingService;
    private final TemplateManager templateManager;
    private final OptimizedBusinessService businessService;
    private final EntityValidationService entityValidationService;
    private final EntityDisambiguationService entityDisambiguationService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TextExtractionService(SmartAIProvider smartAIProvider,
            FileBasedProcessingService fileBasedProcessingService,
            TemplateManager templateManager,
            OptimizedBusinessService businessService,
            EntityValidationService entityValidationService,
            EntityDisambiguationService entityDisambiguationService) {
        this.smartAIProvider = smartAIProvider;
        this.fileBasedProcessingService = fileBasedProcessingService;
        this.templateManager = templateManager;
        this.businessService = businessService;
        this.entityValidationService = entityValidationService;
        this.entityDisambiguationService = entityDisambiguationService;
        this.objectMapper = new ObjectMapper(); // 初始化ObjectMapper
        log.info("🚀 TextExtractionService v5.0初始化完成 - 基于文件系统的智能提取服务，支持消歧义处理");
    }

    /**
     * 统一文本提取方法 - MCP工具接口
     * v4.0优化：模板化配置 + 文件输出
     */
    @Tool(name = "extract_text_data", description = "基于模板的智能文本提取工具，支持多种提取类型和输出格式")
    public String extractTextData(
            @ToolParam(description = "文本内容或文本数组(JSON格式)") String textInput,
            @ToolParam(description = "提取参数: celebrity,work,event,triples,celebritycelebrity 等") String extractParams) {

        try {
            log.info("📝 提交异步文本提取任务 - 类型: {}, 文本长度: {}",
                    extractParams != null ? extractParams : "默认",
                    textInput != null ? textInput.length() : 0);

            // 验证提取类型
            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples"; // 默认使用三元组提取
            }

            // 验证模板是否存在
            if (templateManager.getTemplate(extractParams) == null) {
                log.warn("⚠️ 未找到提取类型{}的模板，使用默认模板", extractParams);
                extractParams = "triples";
            }

            // 调用异步处理
            processTextAsync(textInput, extractParams, "standard");

            // 立即返回成功响应
            return String.format("""
                    {
                        "success": true,
                        "message": "文本提取任务已提交",
                        "extract_params": "%s",
                        "text_length": %d,
                        "timestamp": %d,
                        "supported_types": %s
                    }
                    """, extractParams, textInput.length(), System.currentTimeMillis(),
                    java.util.Arrays.toString(templateManager.getSupportedTypes()));

        } catch (Exception e) {
            log.error("❌ 提交异步文本提取任务失败: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * 统一文本提取处理 - v5.0企业级版本
     * 支持单个文本异步处理和批量文本处理
     */
    @Async("textExtractionExecutor")
    public CompletableFuture<String> processTextAsync(String textInput, String extractParams, String kgMode) {
        // 单个文本处理
        if (textInput != null) {
            return processUnified(java.util.List.of(textInput), extractParams, kgMode, false);
        }
        throw new IllegalArgumentException("textInput不能为null");
    }

    /**
     * 批量文本提取 - v5.0统一处理版本
     */
    public CompletableFuture<String> processBatchTexts(java.util.List<String> textInputs, String extractParams) {
        return processUnified(textInputs, extractParams, "standard", true);
    }

    /**
     * 统一处理核心方法 - 支持异步+批量+知识图谱增强
     */
    private CompletableFuture<String> processUnified(java.util.List<String> textInputs, String extractParams, 
                                                    String kgMode, boolean isBatch) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("🎯 开始{}文本提取 - 类型: {}, KG模式: {}, 数量: {}",
                        isBatch ? "批量" : "异步", extractParams, kgMode, textInputs.size());

                java.util.List<String> results = new java.util.ArrayList<>();
                int successCount = 0;
                int errorCount = 0;

                for (int i = 0; i < textInputs.size(); i++) {
                    try {
                        String text = textInputs.get(i);
                        
                        // 主处理流程：TextExtractionService -> SmartAIProvider -> AIModelCaller
                        String aiResult;
                        if (!isBatch && "enhanced".equals(kgMode) || "fusion".equals(kgMode)) {
                            // 使用业务服务进行知识图谱增强处理
                            CompletableFuture<OptimizedBusinessService.BusinessResult> processingFuture = 
                                    businessService.processAsync(text, extractParams, kgMode);
                            OptimizedBusinessService.BusinessResult businessResult = processingFuture.get();

                            if (businessResult.isSuccess()) {
                                aiResult = businessResult.getResult();
                                log.info("✅ 知识图谱增强处理成功 - 耗时: {}ms", businessResult.getProcessingTime());
                            } else {
                                throw new RuntimeException(businessResult.getErrorMessage());
                            }
                        } else {
                            // 标准AI处理流程
                            aiResult = smartAIProvider.process(text, extractParams);
                        }
                        
                        results.add(aiResult);
                        successCount++;

                        log.debug("{}处理第{}项成功", isBatch ? "批量" : "异步", i + 1);

                    } catch (Exception e) {
                        log.error("{}处理第{}项失败: {}", isBatch ? "批量" : "异步", i + 1, e.getMessage());
                        
                        String errorResult = String.format("{\"error\":\"%s\",\"index\":%d,\"input_preview\":\"%s\"}", 
                                e.getMessage(), i, 
                                textInputs.get(i).substring(0, Math.min(textInputs.get(i).length(), 100)));
                        results.add(errorResult);
                        errorCount++;
                    }
                }

                // 保存处理结果
                boolean saveSuccess;
                if (isBatch) {
                    saveSuccess = fileBasedProcessingService.processBatchExtractionResults(results, extractParams);
                } else {
                    // 单个文本处理结果保存
                    saveSuccess = fileBasedProcessingService.processAndSaveExtractionResult(
                            results.get(0), extractParams,
                            Map.of("processing_mode", "async",
                                    "kg_mode", kgMode,
                                    "timestamp", System.currentTimeMillis()));
                }

                log.info("✅ {}文本提取完成 - 成功: {}, 失败: {}, 保存: {}",
                        isBatch ? "批量" : "异步", successCount, errorCount, saveSuccess ? "成功" : "失败");

                return String.format("""
                        {
                            "success": true,
                            "processing_mode": "%s",
                            "total": %d,
                            "success_count": %d,
                            "error_count": %d,
                            "file_saved": %s,
                            "extract_params": "%s",
                            "kg_mode": "%s",
                            "timestamp": %d
                        }
                        """, isBatch ? "batch" : "async", textInputs.size(), successCount, errorCount, 
                        saveSuccess, extractParams, kgMode, System.currentTimeMillis());

            } catch (Exception e) {
                log.error("❌ {}文本提取失败: {}", isBatch ? "批量" : "异步", e.getMessage(), e);
                return createErrorResponse(String.format("%s处理失败: %s", isBatch ? "批量" : "异步", e.getMessage()));
            }
        });
    }



    /**
     * 企业级实体关系属性消歧义处理 - v5.0核心功能
     * 处理流程：TextExtractionService -> SmartAIProvider -> AIModelCaller
     * 集成实体融合和实体连接
     */
    @Async("textExtractionExecutor")
    public CompletableFuture<String> processEntityRelationDisambiguation(String textInput, String extractParams, 
                                                                         String disambiguationMode, String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("🔍 开始实体关系消歧义处理 - TaskId: {}, 类型: {}, 模式: {}, 文本长度: {}",
                        taskId, extractParams, disambiguationMode, textInput.length());

                // 主处理流程：TextExtractionService -> SmartAIProvider -> AIModelCaller
                // 在SmartAIProvider中集成实体消歧义、关系验证、知识融合
                String aiResult = smartAIProvider.process(textInput, extractParams);
                
                // 进一步的消歧义后处理（如实体连接）
                String disambiguatedResult = performAdvancedDisambiguation(aiResult, disambiguationMode, textInput);
                
                // 保存消歧义处理结果
                Map<String, Object> metadata = Map.of(
                        "processing_mode", "disambiguation",
                        "disambiguation_mode", disambiguationMode,
                        "task_id", taskId,
                        "timestamp", System.currentTimeMillis()
                );
                
                boolean saveSuccess = fileBasedProcessingService.processAndSaveExtractionResult(
                        disambiguatedResult, extractParams + "_disambiguated", metadata);

                log.info("✅ 实体关系消歧义处理完成 - TaskId: {}, 保存: {}", taskId, saveSuccess ? "成功" : "失败");

                return String.format("""
                        {
                            "success": true,
                            "task_id": "%s",
                            "processing_mode": "disambiguation",
                            "disambiguation_mode": "%s",
                            "extract_params": "%s",
                            "file_saved": %s,
                            "timestamp": %d
                        }
                        """, taskId, disambiguationMode, extractParams, saveSuccess, System.currentTimeMillis());

            } catch (Exception e) {
                log.error("❌ 实体关系消歧义处理失败 - TaskId: {}, 错误: {}", taskId, e.getMessage(), e);
                return createErrorResponse(String.format("消歧义处理失败: %s", e.getMessage()));
            }
        });
    }

    /**
     * 批量实体关系消歧义处理
     */
    @Async("textExtractionExecutor")  
    public CompletableFuture<String> processBatchEntityRelationDisambiguation(java.util.List<String> textInputs, 
                                                                              String extractParams, 
                                                                              String disambiguationMode, 
                                                                              String batchId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("📦 开始批量实体关系消歧义处理 - BatchId: {}, 类型: {}, 模式: {}, 数量: {}",
                        batchId, extractParams, disambiguationMode, textInputs.size());

                java.util.List<String> results = new java.util.ArrayList<>();
                int successCount = 0;
                int errorCount = 0;

                for (int i = 0; i < textInputs.size(); i++) {
                    try {
                        String text = textInputs.get(i);
                        
                        // 主处理流程：TextExtractionService -> SmartAIProvider -> AIModelCaller
                        String aiResult = smartAIProvider.process(text, extractParams);
                        
                        // 消歧义后处理
                        String disambiguatedResult = performAdvancedDisambiguation(aiResult, disambiguationMode, text);
                        
                        results.add(disambiguatedResult);
                        successCount++;

                        log.debug("批量消歧义处理第{}项成功 - BatchId: {}", i + 1, batchId);

                    } catch (Exception e) {
                        log.error("批量消歧义处理第{}项失败 - BatchId: {}: {}", i + 1, batchId, e.getMessage());
                        
                        String errorResult = String.format("{\"error\":\"%s\",\"index\":%d,\"batch_id\":\"%s\"}", 
                                e.getMessage(), i, batchId);
                        results.add(errorResult);
                        errorCount++;
                    }
                }

                // 保存批量消歧义结果
                boolean saveSuccess = fileBasedProcessingService.processBatchExtractionResults(
                        results, extractParams + "_batch_disambiguated");

                log.info("✅ 批量实体关系消歧义处理完成 - BatchId: {}, 成功: {}, 失败: {}, 保存: {}",
                        batchId, successCount, errorCount, saveSuccess ? "成功" : "失败");

                return String.format("""
                        {
                            "success": true,
                            "batch_id": "%s",
                            "processing_mode": "batch_disambiguation",
                            "disambiguation_mode": "%s",
                            "total": %d,
                            "success_count": %d,
                            "error_count": %d,
                            "extract_params": "%s",
                            "file_saved": %s,
                            "timestamp": %d
                        }
                        """, batchId, disambiguationMode, textInputs.size(), successCount, errorCount, 
                        extractParams, saveSuccess, System.currentTimeMillis());

            } catch (Exception e) {
                log.error("❌ 批量实体关系消歧义处理失败 - BatchId: {}, 错误: {}", batchId, e.getMessage(), e);
                return createErrorResponse(String.format("批量消歧义处理失败: %s", e.getMessage()));
            }
        });
    }

    /**
     * 高级消歧义后处理 - 包含实体融合和实体连接
     */
    private String performAdvancedDisambiguation(String aiResult, String disambiguationMode, String originalText) {
        try {
            JsonNode aiResultNode = objectMapper.readTree(aiResult);
            ObjectNode enhancedResult = (ObjectNode) aiResultNode.deepCopy();
            
            // 根据消歧义模式进行不同的处理
            switch (disambiguationMode.toLowerCase()) {
                case "full":
                    // 完整消歧义：实体 + 关系 + 属性
                    performEntityLinking(enhancedResult, originalText);
                    performEntityFusion(enhancedResult);
                    performAttributeDisambiguation(enhancedResult);
                    break;
                case "entity_only":
                    // 仅实体消歧义
                    performEntityLinking(enhancedResult, originalText);
                    performEntityFusion(enhancedResult);
                    break;
                case "relation_only":
                    // 仅关系消歧义（已在SmartAIProvider中处理）
                    break;
                default:
                    log.warn("⚠️ 未知的消歧义模式: {}, 使用默认处理", disambiguationMode);
                    break;
            }
            
            // 添加消歧义处理元数据
            enhancedResult.put("disambiguation_applied", true);
            enhancedResult.put("disambiguation_mode", disambiguationMode);
            enhancedResult.put("disambiguation_timestamp", System.currentTimeMillis());

            return objectMapper.writeValueAsString(enhancedResult);

        } catch (Exception e) {
            log.warn("⚠️ 高级消歧义后处理失败，返回原始结果: {}", e.getMessage());
            return aiResult; // 降级处理
        }
    }

    /**
     * 实体连接处理
     */
    private void performEntityLinking(ObjectNode result, String context) {
        // 实体连接到外部知识库的逻辑
        // 这里可以集成百度百科、维基百科等外部数据源
        log.debug("🔗 执行实体连接处理");
        
        JsonNode entities = result.get("entities");
        if (entities != null && entities.isArray()) {
            for (JsonNode entity : entities) {
                // 为每个实体添加可能的外部链接
                if (entity instanceof ObjectNode) {
                    ObjectNode entityObj = (ObjectNode) entity;
                    entityObj.put("linked", false); // 标记为未连接，实际实现时会尝试连接
                    entityObj.put("link_confidence", 0.0);
                }
            }
        }
    }

    /**
     * 实体融合处理
     */
    private void performEntityFusion(ObjectNode result) {
        // 同一实体的不同提及进行融合
        log.debug("🔀 执行实体融合处理");
        
        JsonNode entities = result.get("entities");
        if (entities != null && entities.isArray()) {
            // 实体融合逻辑：合并相似的实体
            ArrayNode fusedEntities = objectMapper.createArrayNode();
            
            // 简化的融合逻辑：基于名称相似度
            Map<String, ObjectNode> entityMap = new HashMap<>();
            for (JsonNode entity : entities) {
                if (entity.has("name")) {
                    String name = entity.get("name").asText().toLowerCase();
                    if (entityMap.containsKey(name)) {
                        // 合并实体属性
                        ObjectNode existing = entityMap.get(name);
                        entityDisambiguationService.mergeEntityAttributes(existing, (ObjectNode) entity);
                    } else {
                        entityMap.put(name, (ObjectNode) entity.deepCopy());
                    }
                }
            }
            
            // 添加融合后的实体
            entityMap.values().forEach(fusedEntities::add);
            result.set("entities", fusedEntities);
            result.put("entity_fusion_applied", true);
        }
    }

    /**
     * 属性消歧义处理
     */
    private void performAttributeDisambiguation(ObjectNode result) {
        // 属性值的消歧义和标准化
        log.debug("🎯 执行属性消歧义处理");
        
        JsonNode entities = result.get("entities");
        if (entities != null && entities.isArray()) {
            for (JsonNode entity : entities) {
                if (entity instanceof ObjectNode) {
                    ObjectNode entityObj = (ObjectNode) entity;
                    
                    // 标准化日期格式
                    if (entityObj.has("birth_date")) {
                        String date = entityObj.get("birth_date").asText();
                        String normalizedDate = normalizeDateFormat(date);
                        entityObj.put("birth_date", normalizedDate);
                    }
                    
                    // 标准化职业描述
                    if (entityObj.has("profession")) {
                        String profession = entityObj.get("profession").asText();
                        String normalizedProfession = entityValidationService.normalizeProfession(profession);
                        entityObj.put("profession", normalizedProfession);
                    }
                }
            }
        }
        
        result.put("attribute_disambiguation_applied", true);
    }

    /**
     * 标准化日期格式 - 委托给EntityValidationService
     */
    private String normalizeDateFormat(String date) {
        return entityValidationService.normalizeDateFormat(date);
    }

    /**
     * 验证提取类型是否有效
     */
    public boolean isValidExtractionType(String extractParams) {
        if (extractParams == null || extractParams.trim().isEmpty()) {
            return false;
        }

        return java.util.Arrays.asList(templateManager.getSupportedTypes())
                .contains(extractParams.toLowerCase());
    }

    /**
     * 创建错误响应
     */
    private String createErrorResponse(String errorMessage) {
        return String.format("""
                {
                    "success": false,
                    "error": "%s",
                    "timestamp": %d,
                    "service_version": "v4.0"
                }
                """, errorMessage, System.currentTimeMillis());
    }
}
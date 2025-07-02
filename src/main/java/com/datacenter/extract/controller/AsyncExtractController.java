package com.datacenter.extract.controller;

import com.alibaba.fastjson2.JSONObject;
import com.datacenter.extract.service.TextExtractionService;
import com.datacenter.extract.service.FileBasedProcessingService;
import com.datacenter.extract.service.TemplateManager;
import com.datacenter.extract.service.ExtractMcpService;
import com.datacenter.extract.util.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

/**
 * 异步提取控制器 - v4.0重构版本
 * 
 * 🚀 v4.0重构特性：
 * 1. 基于模板的提取配置
 * 2. 文件系统输出代替数据库
 * 3. 优化的API响应
 * 4. 增强的错误处理
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class AsyncExtractController {

    private static final Logger log = LoggerFactory.getLogger(AsyncExtractController.class);

    private final TextExtractionService textExtractionService;
    private final FileBasedProcessingService fileBasedProcessingService;
    private final TemplateManager templateManager;
    private final ExtractMcpService extractMcpService;

    @Autowired
    public AsyncExtractController(TextExtractionService textExtractionService,
            FileBasedProcessingService fileBasedProcessingService,
            TemplateManager templateManager,
            ExtractMcpService extractMcpService) {
        this.textExtractionService = textExtractionService;
        this.fileBasedProcessingService = fileBasedProcessingService;
        this.templateManager = templateManager;
        this.extractMcpService = extractMcpService;
        log.info("🚀 AsyncExtractController v4.0初始化完成 - 基于文件系统的智能提取API");
    }

    /**
     * 异步文本提取接口 - v4.0重构版本
     */
    @PostMapping("/extract")
    public JSONObject extractAsync(@RequestBody JSONObject request) {
        try {
            // 提取参数
            String textInput = request.getString("textInput");
            String extractParams = request.getString("extractParams");
            String kgMode = request.getString("kgMode");

            // 参数验证
            if (textInput == null || textInput.trim().isEmpty()) {
                return ResponseBuilder.error("textInput不能为空").build();
            }

            // 默认提取类型
            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples"; // v5.0: 使用统一参数名
            }

            // 验证提取类型是否支持
            if (!textExtractionService.isValidExtractionType(extractParams)) {
                return ResponseBuilder.error("不支持的提取类型: " + extractParams)
                        .data("supported_types", templateManager.getSupportedTypes())
                        .build();
            }

            // 默认KG模式 - v5.0优化：默认使用fusion模式（P0优先级）
            if (kgMode == null || kgMode.trim().isEmpty()) {
                kgMode = "fusion";
            }

            // 验证kgMode参数 - v5.0优化：只支持fusion和batch策略
            if (!Arrays.asList("fusion", "batch").contains(kgMode)) {
                log.warn("⚠️ 无效的kgMode参数: {}, 使用默认值: fusion", kgMode);
                kgMode = "fusion";
            }

            log.info("📝 收到提取请求 - 类型: {}, KG模式: {}, 文本长度: {}",
                    extractParams, kgMode, textInput.length());

            // 提交异步任务
            textExtractionService.processTextAsync(textInput, extractParams, kgMode);

            // 立即返回成功响应
            return ResponseBuilder.success("文本提取任务已提交，正在智能处理中...")
                    .data("extract_params", extractParams)
                    .data("kg_mode", kgMode)
                    .data("text_length", textInput.length())
                    .data("output_location", "out/out_" + extractParams + "_*.json")
                    .build();

        } catch (Exception e) {
            log.error("❌ 处理提取请求失败", e);
            return ResponseBuilder.error("处理请求失败: " + e.getMessage()).build();
        }
    }

    /**
     * MCP同步文本提取接口 - v5.0新增
     * 独立的ExtractMcpService路线，直接返回提取结果
     */
    @PostMapping("/mcp/extract")
    public JSONObject extractMcp(@RequestBody JSONObject request) {
        try {
            String textInput = request.getString("textInput");
            String extractParams = request.getString("extractParams");

            // 参数验证
            if (textInput == null || textInput.trim().isEmpty()) {
                return ResponseBuilder.error("textInput不能为空").build();
            }

            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples";
            }

            log.info("🔧 收到MCP同步提取请求 - 类型: {}, 文本长度: {}", extractParams, textInput.length());

            // 直接调用ExtractMcpService处理
            String result = extractMcpService.extractTextData(textInput, extractParams);
            
            // 解析结果并构建响应
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode resultNode = mapper.readTree(result);
                
                if (resultNode.get("success").asBoolean()) {
                    return ResponseBuilder.success("MCP同步提取成功")
                            .data("result", resultNode.get("result"))
                            .data("extract_params", resultNode.get("extract_params"))
                            .data("processing_time_ms", resultNode.get("processing_time_ms"))
                            .data("service", "ExtractMcpService")
                            .build();
                } else {
                    return ResponseBuilder.error(resultNode.get("error").asText()).build();
                }
            } catch (Exception e) {
                // 如果解析失败，直接返回原始结果
                return ResponseBuilder.success("MCP同步提取完成")
                        .data("raw_result", result)
                        .data("service", "ExtractMcpService")
                        .build();
            }

        } catch (Exception e) {
            log.error("❌ MCP提取请求失败", e);
            return ResponseBuilder.error("MCP提取失败: " + e.getMessage()).build();
        }
    }

    /**
     * 批量文本提取接口 - v4.0新增
     */
    @PostMapping("/extract/batch")
    public JSONObject extractBatch(@RequestBody JSONObject request) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> textInputs = (java.util.List<String>) request.get("textInputs");
            String extractParams = request.getString("extractParams");

            if (textInputs == null || textInputs.isEmpty()) {
                return ResponseBuilder.error("textInputs不能为空").build();
            }

            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples";
            }

            if (!textExtractionService.isValidExtractionType(extractParams)) {
                return ResponseBuilder.error("不支持的提取类型: " + extractParams).build();
            }

            log.info("📦 收到批量提取请求 - 类型: {}, 数量: {}", extractParams, textInputs.size());

            // 提交批量处理任务
            textExtractionService.processBatchTexts(textInputs, extractParams);

            return ResponseBuilder.success("批量提取任务已提交")
                    .data("extract_params", extractParams)
                    .data("batch_size", textInputs.size())
                    .data("output_location", "out/batch/batch_" + extractParams + "_*.json")
                    .build();

        } catch (Exception e) {
            log.error("❌ 处理批量提取请求失败", e);
            return ResponseBuilder.error("批量处理失败: " + e.getMessage()).build();
        }
    }

    /**
     * 获取支持的提取类型 - v5.0精简版（仅核心功能）
     */
    @GetMapping("/extract/types")
    public JSONObject getSupportedTypes() {
        try {
            String[] supportedTypes = templateManager.getSupportedTypes();

            return ResponseBuilder.success("获取支持的提取类型成功")
                    .data("supported_types", supportedTypes)
                    .data("total_types", supportedTypes.length)
                    .data("timestamp", System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("❌ 获取支持的提取类型失败", e);
            return ResponseBuilder.error("获取类型信息失败: " + e.getMessage()).build();
        }
    }

    /**
     * 获取模板信息 - v5.0优化版
     */
    @GetMapping("/extract/template/{extractParams}")
    public JSONObject getTemplateInfo(@PathVariable String extractParams) {
        try {
            Map<String, Object> templateInfo = fileBasedProcessingService.getTemplateInfo(extractParams);

            if (templateInfo.containsKey("error")) {
                return ResponseBuilder.error((String) templateInfo.get("error")).build();
            }

            return ResponseBuilder.success("获取模板信息成功")
                    .data("template", templateInfo)
                    .build();

        } catch (Exception e) {
            log.error("❌ 获取模板信息失败: {}", extractParams, e);
            return ResponseBuilder.error("获取模板信息失败: " + e.getMessage()).build();
        }
    }

    /**
     * 服务统计信息接口 - v5.0精简版（仅核心状态）
     */
    @GetMapping("/status")
    public JSONObject getServiceStatus() {
        try {
            Map<String, Object> basicStats = fileBasedProcessingService.getProcessingStats();

            return ResponseBuilder.success("服务运行正常")
                    .data("version", "v5.0")
                    .data("status", "active")
                    .data("supported_types", templateManager.getSupportedTypes())
                    .data("timestamp", System.currentTimeMillis())
                    .data("core_features", Arrays.asList(
                            "实体消歧义处理",
                            "关系验证与融合",
                            "知识图谱构建",
                            "批量消歧义处理",
                            "企业级配置化"))
                    .data("basic_stats", basicStats)
                    .build();

        } catch (Exception e) {
            log.error("❌ 获取服务状态失败", e);
            return ResponseBuilder.error("获取服务状态失败: " + e.getMessage()).build();
        }
    }

    /**
     * 清理旧文件接口 - v5.0直接调用文件服务
     */
    @PostMapping("/cleanup")
    public JSONObject cleanupOldFiles(@RequestBody JSONObject request) {
        try {
            int daysToKeep = request.getIntValue("daysToKeep", 7); // 默认保留7天

            fileBasedProcessingService.cleanupOldFiles(daysToKeep);

            return ResponseBuilder.success("文件清理任务已提交")
                    .data("days_to_keep", daysToKeep)
                    .build();

        } catch (Exception e) {
            log.error("❌ 清理文件失败", e);
            return ResponseBuilder.error("清理文件失败: " + e.getMessage()).build();
        }
    }

    /**
     * 企业级实体关系属性消歧义处理 - v5.0核心功能
     * 支持实体消歧义、关系验证、属性融合和实体连接
     */
    @PostMapping("/extract/disambiguate")
    public JSONObject processEntityRelationDisambiguation(@RequestBody JSONObject request) {
        String taskId = java.util.UUID.randomUUID().toString();
        
        try {
            // 提取参数
            String textInput = request.getString("textInput");
            String extractParams = request.getString("extractParams");
            String disambiguationMode = request.getString("disambiguationMode");
            if (disambiguationMode == null || disambiguationMode.trim().isEmpty()) {
                disambiguationMode = "full"; // full/entity_only/relation_only
            }
            
            // 参数验证
            if (textInput == null || textInput.trim().isEmpty()) {
                return ResponseBuilder.error("textInput不能为空").build();
            }

            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples"; // 默认三元组提取
            }

            // 验证提取类型
            if (!textExtractionService.isValidExtractionType(extractParams)) {
                return ResponseBuilder.error("不支持的提取类型: " + extractParams)
                        .data("supported_types", templateManager.getSupportedTypes())
                        .build();
            }

            log.info("🔍 收到消歧义处理请求 - TaskId: {}, 类型: {}, 模式: {}, 文本长度: {}", 
                    taskId, extractParams, disambiguationMode, textInput.length());

            // 提交消歧义处理任务
            textExtractionService.processEntityRelationDisambiguation(textInput, extractParams, disambiguationMode, taskId);

            return ResponseBuilder.success("消歧义处理任务已提交")
                    .data("taskId", taskId)
                    .data("extractParams", extractParams)
                    .data("disambiguationMode", disambiguationMode)
                    .data("textLength", textInput.length())
                    .data("estimatedTime", estimateProcessingTime(textInput.length()))
                    .build();

        } catch (Exception e) {
            log.error("❌ 消歧义处理请求失败 - TaskId: {}, 错误: {}", taskId, e.getMessage(), e);
            return ResponseBuilder.error("消歧义处理请求失败: " + e.getMessage())
                    .data("taskId", taskId)
                    .build();
        }
    }

    /**
     * 批量实体关系消歧义处理
     */
    @PostMapping("/extract/disambiguate/batch")
    public JSONObject processBatchEntityRelationDisambiguation(@RequestBody JSONObject request) {
        String batchId = java.util.UUID.randomUUID().toString();
        
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> textInputs = (java.util.List<String>) request.get("textInputs");
            String extractParams = request.getString("extractParams");
            String disambiguationMode = request.getString("disambiguationMode");
            if (disambiguationMode == null || disambiguationMode.trim().isEmpty()) {
                disambiguationMode = "full";
            }

            if (textInputs == null || textInputs.isEmpty()) {
                return ResponseBuilder.error("textInputs不能为空").build();
            }

            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples";
            }

            if (!textExtractionService.isValidExtractionType(extractParams)) {
                return ResponseBuilder.error("不支持的提取类型: " + extractParams).build();
            }

            log.info("📦 收到批量消歧义处理请求 - BatchId: {}, 类型: {}, 模式: {}, 数量: {}", 
                    batchId, extractParams, disambiguationMode, textInputs.size());

            // 提交批量消歧义处理任务
            textExtractionService.processBatchEntityRelationDisambiguation(textInputs, extractParams, disambiguationMode, batchId);

            return ResponseBuilder.success("批量消歧义处理任务已提交")
                    .data("batchId", batchId)
                    .data("extractParams", extractParams)
                    .data("disambiguationMode", disambiguationMode)
                    .data("totalCount", textInputs.size())
                    .data("estimatedTotalTime", estimateBatchProcessingTime(textInputs))
                    .build();

        } catch (Exception e) {
            log.error("❌ 批量消歧义处理请求失败 - BatchId: {}, 错误: {}", batchId, e.getMessage(), e);
            return ResponseBuilder.error("批量消歧义处理请求失败: " + e.getMessage())
                    .data("batchId", batchId)
                    .build();
        }
    }

    /**
     * 估算处理时间
     */
    private String estimateProcessingTime(int textLength) {
        if (textLength < 500) {
            return "5-10秒";
        } else if (textLength < 2000) {
            return "10-30秒";
        } else {
            return "30-60秒";
        }
    }

    /**
     * 估算批量处理时间
     */
    private String estimateBatchProcessingTime(java.util.List<String> textInputs) {
        int totalLength = textInputs.stream().mapToInt(String::length).sum();
        int avgLength = totalLength / textInputs.size();
        int baseTime = textInputs.size() * 10; // 每个文本基础10秒
        
        if (avgLength > 1000) {
            baseTime = baseTime * 2; // 长文本加倍
        }
        
        return String.format("%d-%d分钟", baseTime/60, (baseTime*2)/60);
    }

    /**
     * 健康检查接口 - v5.0版本
     */
    @GetMapping("/health")
    public JSONObject healthCheck() {
        try {
            return ResponseBuilder.success("服务健康")
                    .data("status", "healthy")
                    .data("version", "v5.0")
                    .data("timestamp", System.currentTimeMillis())
                    .data("supported_types", templateManager.getSupportedTypes())
                    .data("features", Arrays.asList(
                            "企业级知识图谱处理",
                            "实体关系消歧义",
                            "知识融合与连接",
                            "配置化架构",
                            "批量处理优化"))
                    .build();

        } catch (Exception e) {
            log.error("❌ 健康检查失败", e);
            return ResponseBuilder.error("健康检查失败: " + e.getMessage()).build();
        }
    }
}
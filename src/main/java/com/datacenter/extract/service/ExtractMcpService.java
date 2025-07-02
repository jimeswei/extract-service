package com.datacenter.extract.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;

/**
 * 提取MCP服务 - 独立的同步文本提取服务
 * 
 * 🎯 设计目标：
 * 1. 与异步方法完全并行的独立路线
 * 2. 直接返回提取结果，无需等待
 * 3. 路径：ExtractMcpService -> SmartAIProvider -> AIModelCaller
 * 4. 支持多种提取类型：celebrity、work、event、triples、celebritycelebrity等
 * 
 * 🔗 处理流程：
 * ExtractMcpService -> SmartAIProvider -> AIModelCaller
 * 
 * @author 基于升级业务需求优化
 * @since v5.0企业级版本
 */
@Service
public class ExtractMcpService {

    private static final Logger log = LoggerFactory.getLogger(ExtractMcpService.class);

    private final SmartAIProvider smartAIProvider;
    private final TemplateManager templateManager;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExtractMcpService(SmartAIProvider smartAIProvider, TemplateManager templateManager) {
        this.smartAIProvider = smartAIProvider;
        this.templateManager = templateManager;
        this.objectMapper = new ObjectMapper();
        log.info("🚀 ExtractMcpService初始化完成 - 独立同步文本提取服务");
    }

    /**
     * MCP工具方法：基于模板的智能文本提取
     * 
     * 🔄 与异步方法并行的独立路线，直接返回提取结果
     * 
     * @param textInput 文本内容或文本数组(JSON格式)
     * @param extractParams 提取参数: celebrity,work,event,triples,celebritycelebrity 等
     * @return 直接返回提取结果，格式化的JSON字符串
     */
    @Tool(name = "extract_text_data", description = "基于模板的智能文本提取工具，支持多种提取类型和输出格式")
    public String extractTextData(
            @ToolParam(description = "文本内容或文本数组(JSON格式)") String textInput,
            @ToolParam(description = "提取参数: celebrity,work,event,triples,celebritycelebrity 等") String extractParams) {

        try {
            log.info("📝 开始同步文本提取 - 类型: {}, 文本长度: {}",
                    extractParams != null ? extractParams : "默认",
                    textInput != null ? textInput.length() : 0);

            // 验证输入
            if (textInput == null || textInput.trim().isEmpty()) {
                return createErrorResponse("文本内容不能为空");
            }

            // 验证提取类型
            if (extractParams == null || extractParams.trim().isEmpty()) {
                extractParams = "triples"; // 默认使用三元组提取
                log.info("⚠️ 未指定提取类型，使用默认类型: {}", extractParams);
            }

            // 验证模板是否存在
            if (templateManager.getTemplate(extractParams) == null) {
                log.warn("⚠️ 未找到提取类型{}的模板，使用默认模板", extractParams);
                extractParams = "triples";
            }

            // 处理文本数组输入
            String[] texts = parseTextInput(textInput);
            
            if (texts.length == 1) {
                // 单个文本处理
                return processSingleText(texts[0], extractParams);
            } else {
                // 多个文本处理
                return processMultipleTexts(texts, extractParams);
            }

        } catch (Exception e) {
            log.error("❌ 同步文本提取失败: {}", e.getMessage());
            return createErrorResponse("文本提取失败: " + e.getMessage());
        }
    }

    /**
     * 解析文本输入，支持单个文本和JSON数组
     */
    private String[] parseTextInput(String textInput) {
        try {
            // 尝试解析为JSON数组
            if (textInput.trim().startsWith("[")) {
                JsonNode jsonArray = objectMapper.readTree(textInput);
                if (jsonArray.isArray()) {
                    String[] texts = new String[jsonArray.size()];
                    for (int i = 0; i < jsonArray.size(); i++) {
                        texts[i] = jsonArray.get(i).asText();
                    }
                    log.debug("🔄 解析到{}个文本", texts.length);
                    return texts;
                }
            }
        } catch (Exception e) {
            log.debug("📝 非JSON数组格式，视为单个文本处理");
        }
        
        // 单个文本
        return new String[]{textInput};
    }

    /**
     * 处理单个文本
     */
    private String processSingleText(String text, String extractParams) {
        try {
            long startTime = System.currentTimeMillis();
            
            // 核心处理：ExtractMcpService -> SmartAIProvider -> AIModelCaller
            String result = smartAIProvider.process(text, extractParams);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("✅ 单个文本提取成功 - 类型: {}, 耗时: {}ms", extractParams, processingTime);

            // 验证结果格式
            if (isValidExtractionResult(result)) {
                return formatSuccessResponse(result, extractParams, processingTime, "single");
            } else {
                log.warn("⚠️ 提取结果格式异常，返回原始结果");
                return result;
            }

        } catch (Exception e) {
            log.error("❌ 单个文本处理失败: {}", e.getMessage());
            throw new RuntimeException("单个文本处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理多个文本
     */
    private String processMultipleTexts(String[] texts, String extractParams) {
        try {
            long startTime = System.currentTimeMillis();
            StringBuilder allResults = new StringBuilder();
            allResults.append("{\n  \"success\": true,\n  \"results\": [\n");

            int successCount = 0;
            int errorCount = 0;

            for (int i = 0; i < texts.length; i++) {
                try {
                    String text = texts[i];
                    if (text == null || text.trim().isEmpty()) {
                        log.warn("⚠️ 第{}个文本为空，跳过", i + 1);
                        continue;
                    }

                    // 核心处理：ExtractMcpService -> SmartAIProvider -> AIModelCaller
                    String result = smartAIProvider.process(text, extractParams);
                    
                    if (i > 0 && successCount > 0) {
                        allResults.append(",\n");
                    }
                    
                    allResults.append("    {\n");
                    allResults.append("      \"index\": ").append(i).append(",\n");
                    allResults.append("      \"success\": true,\n");
                    allResults.append("      \"result\": ").append(result).append("\n");
                    allResults.append("    }");
                    
                    successCount++;
                    log.debug("✅ 第{}个文本处理成功", i + 1);

                } catch (Exception e) {
                    log.error("❌ 第{}个文本处理失败: {}", i + 1, e.getMessage());
                    
                    if (i > 0 && (successCount > 0 || errorCount > 0)) {
                        allResults.append(",\n");
                    }
                    
                    allResults.append("    {\n");
                    allResults.append("      \"index\": ").append(i).append(",\n");
                    allResults.append("      \"success\": false,\n");
                    allResults.append("      \"error\": \"").append(e.getMessage().replace("\"", "\\\"")).append("\"\n");
                    allResults.append("    }");
                    
                    errorCount++;
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            
            allResults.append("\n  ],\n");
            allResults.append("  \"extract_params\": \"").append(extractParams).append("\",\n");
            allResults.append("  \"total_count\": ").append(texts.length).append(",\n");
            allResults.append("  \"success_count\": ").append(successCount).append(",\n");
            allResults.append("  \"error_count\": ").append(errorCount).append(",\n");
            allResults.append("  \"processing_time_ms\": ").append(processingTime).append(",\n");
            allResults.append("  \"processing_mode\": \"multiple\",\n");
            allResults.append("  \"timestamp\": ").append(System.currentTimeMillis()).append("\n");
            allResults.append("}");

            log.info("✅ 多个文本提取完成 - 总数: {}, 成功: {}, 失败: {}, 耗时: {}ms",
                    texts.length, successCount, errorCount, processingTime);

            return allResults.toString();

        } catch (Exception e) {
            log.error("❌ 多个文本处理失败: {}", e.getMessage());
            throw new RuntimeException("多个文本处理失败: " + e.getMessage());
        }
    }

    /**
     * 验证提取结果是否有效
     */
    private boolean isValidExtractionResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含错误标识
        if (result.contains("\"success\":false") || result.contains("\"error\"")) {
            return false;
        }

        // 检查是否包含有效的数据结构
        return result.contains("triples") || 
               result.contains("entities") || 
               result.contains("relations") ||
               result.contains("celebrities") ||
               result.contains("works") ||
               result.contains("events");
    }

    /**
     * 格式化成功响应
     */
    private String formatSuccessResponse(String result, String extractParams, long processingTime, String mode) {
        try {
            return String.format("""
                    {
                        "success": true,
                        "extract_params": "%s",
                        "processing_time_ms": %d,
                        "processing_mode": "%s",
                        "result": %s,
                        "service": "ExtractMcpService",
                        "timestamp": %d,
                        "supported_types": %s
                    }
                    """, 
                    extractParams, 
                    processingTime, 
                    mode,
                    result,
                    System.currentTimeMillis(),
                    Arrays.toString(templateManager.getSupportedTypes()));
        } catch (Exception e) {
            log.warn("⚠️ 格式化响应失败，返回原始结果: {}", e.getMessage());
            return result;
        }
    }

    /**
     * 创建错误响应
     */
    private String createErrorResponse(String errorMessage) {
        return String.format("""
                {
                    "success": false,
                    "error": "%s",
                    "service": "ExtractMcpService",
                    "timestamp": %d,
                    "supported_types": %s
                }
                """, 
                errorMessage, 
                System.currentTimeMillis(),
                Arrays.toString(templateManager.getSupportedTypes()));
    }

    /**
     * 获取支持的提取类型
     */
    public String[] getSupportedTypes() {
        return templateManager.getSupportedTypes();
    }

    /**
     * 验证提取类型是否有效
     */
    public boolean isValidExtractionType(String extractParams) {
        if (extractParams == null || extractParams.trim().isEmpty()) {
            return false;
        }
        return Arrays.asList(templateManager.getSupportedTypes())
                .contains(extractParams.toLowerCase());
    }
} 
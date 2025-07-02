package com.datacenter.extract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 基于文件的处理服务 - 替代数据库操作
 * 
 * v4.0重构特性：
 * 1. 完全移除数据库依赖
 * 2. 基于模板的提取字段管理
 * 3. 结构化文件输出
 * 4. 支持多种提取类型
 */
@Service
public class FileBasedProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FileBasedProcessingService.class);

    private final TemplateManager templateManager;
    private final OutputFileService outputFileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public FileBasedProcessingService(TemplateManager templateManager, OutputFileService outputFileService) {
        this.templateManager = templateManager;
        this.outputFileService = outputFileService;
        log.info("🗂️ FileBasedProcessingService初始化完成，支持类型: {}",
                String.join(", ", templateManager.getSupportedTypes()));
    }

    /**
     * 处理提取结果并保存到文件
     */
    public boolean processAndSaveExtractionResult(String extractionResult, String extractType,
            Map<String, Object> processingMetadata) {
        try {
            log.info("开始处理提取结果 - 类型: {}, 数据长度: {}",
                    extractType, extractionResult != null ? extractionResult.length() : 0);

            // 解析AI提取结果
            Map<String, Object> parsedData = parseExtractionResult(extractionResult);

            if (parsedData.isEmpty()) {
                log.warn("解析结果为空，跳过文件保存");
                return false;
            }

            // 验证并规范化数据
            Map<String, Object> normalizedData = normalizeExtractionData(parsedData, extractType);

            // 添加处理元数据
            if (processingMetadata != null) {
                normalizedData.putAll(processingMetadata);
            }

            // 保存到文件
            outputFileService.saveExtractionResult(extractType,
                    objectMapper.writeValueAsString(normalizedData),
                    Map.of("processed_at", System.currentTimeMillis(),
                            "extract_type", extractType,
                            "status", "success"));

            // 保存原始结果用于调试
            outputFileService.saveRawResult(extractType, extractionResult,
                    objectMapper.writeValueAsString(normalizedData));

            log.info("✅ 成功处理并保存提取结果到文件系统");
            return true;

        } catch (Exception e) {
            log.error("❌ 处理提取结果失败: {}", e.getMessage(), e);

            // 保存错误信息
            try {
                outputFileService.saveExtractionResult(extractType + "_error",
                        extractionResult,
                        Map.of("error", e.getMessage(),
                                "processed_at", System.currentTimeMillis(),
                                "status", "failed"));
            } catch (Exception saveError) {
                log.error("保存错误信息失败: {}", saveError.getMessage());
            }

            return false;
        }
    }

    /**
     * 批量处理多个提取结果
     */
    public boolean processBatchExtractionResults(List<String> extractionResults, String extractType) {
        try {
            log.info("开始批量处理 - 类型: {}, 数量: {}", extractType, extractionResults.size());

            Map<String, Object> batchResults = new HashMap<>();
            List<Map<String, Object>> successResults = new ArrayList<>();
            List<Map<String, Object>> errorResults = new ArrayList<>();

            for (int i = 0; i < extractionResults.size(); i++) {
                String result = extractionResults.get(i);
                try {
                    Map<String, Object> parsedData = parseExtractionResult(result);
                    if (!parsedData.isEmpty()) {
                        Map<String, Object> normalizedData = normalizeExtractionData(parsedData, extractType);
                        normalizedData.put("batch_index", i);
                        successResults.add(normalizedData);
                    }
                } catch (Exception e) {
                    log.error("批量处理第{}项失败: {}", i, e.getMessage());
                    errorResults.add(Map.of("batch_index", i, "error", e.getMessage(), "raw_data", result));
                }
            }

            batchResults.put("success_count", successResults.size());
            batchResults.put("error_count", errorResults.size());
            batchResults.put("success_results", successResults);
            batchResults.put("error_results", errorResults);

            // 保存批量结果
            outputFileService.saveBatchResults(extractType, batchResults);

            log.info("✅ 批量处理完成 - 成功: {}, 失败: {}", successResults.size(), errorResults.size());
            return errorResults.isEmpty();

        } catch (Exception e) {
            log.error("❌ 批量处理失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 根据提取类型获取模板信息
     */
    public Map<String, Object> getTemplateInfo(String extractType) {
        try {
            JsonNode template = templateManager.getTemplate(extractType);
            if (template == null) {
                return Map.of("error", "模板不存在");
            }

            Map<String, Object> templateInfo = new HashMap<>();
            templateInfo.put("entity_type", template.get("entity_type").asText());
            templateInfo.put("extraction_fields", template.get("extraction_fields"));
            templateInfo.put("output_structure", template.get("output_structure"));

            if (template.has("relation_types")) {
                templateInfo.put("relation_types", template.get("relation_types"));
            }

            if (template.has("work_types")) {
                templateInfo.put("work_types", template.get("work_types"));
            }

            if (template.has("event_types")) {
                templateInfo.put("event_types", template.get("event_types"));
            }

            return templateInfo;

        } catch (Exception e) {
            log.error("获取模板信息失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 获取处理统计信息
     */
    public Map<String, Object> getProcessingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("supported_types", templateManager.getSupportedTypes());
        stats.put("output_directory_info", outputFileService.getOutputDirectoryInfo());
        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }

    /**
     * 清理旧文件
     */
    public void cleanupOldFiles(int daysToKeep) {
        log.info("开始清理{}天前的旧文件", daysToKeep);
        outputFileService.cleanupOldFiles(daysToKeep);
    }

    /**
     * 解析提取结果
     */
    private Map<String, Object> parseExtractionResult(String result) {
        try {
            log.debug("解析提取结果，数据长度: {}", result != null ? result.length() : 0);

            if (result == null || result.trim().isEmpty()) {
                log.warn("提取结果为空");
                return Map.of();
            }

            // 提取JSON部分
            String jsonPart = extractJsonFromResult(result);
            if (jsonPart.isEmpty()) {
                log.warn("无法从结果中提取JSON部分");
                return Map.of();
            }

            log.debug("提取的JSON部分: {}", jsonPart);

            // 解析JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonPart, Map.class);

            log.debug("成功解析提取结果，包含字段: {}", data.keySet());
            return data;

        } catch (Exception e) {
            log.error("解析提取结果失败: {}, 原始数据: {}", e.getMessage(), result);
            return Map.of();
        }
    }

    /**
     * 从结果中提取JSON部分
     */
    private String extractJsonFromResult(String result) {
        if (result.trim().startsWith("{")) {
            return result.trim();
        }

        // 查找JSON开始和结束位置
        int start = result.indexOf("{");
        int end = result.lastIndexOf("}");

        if (start >= 0 && end > start) {
            return result.substring(start, end + 1);
        }

        return "";
    }

    /**
     * 根据模板规范化提取数据
     */
    private Map<String, Object> normalizeExtractionData(Map<String, Object> data, String extractType) {
        try {
            JsonNode outputStructure = templateManager.getOutputStructure(extractType);
            if (outputStructure == null) {
                log.debug("未找到输出结构模板，返回原始数据");
                return data;
            }

            // 根据模板结构验证和规范化数据
            Map<String, Object> normalizedData = new HashMap<>(data);
            normalizedData.put("extract_type", extractType);
            normalizedData.put("normalized", true);
            normalizedData.put("template_version", "v4.0");

            log.debug("数据规范化完成，字段数: {}", normalizedData.size());
            return normalizedData;

        } catch (Exception e) {
            log.error("数据规范化失败: {}", e.getMessage());
            return data;
        }
    }
}
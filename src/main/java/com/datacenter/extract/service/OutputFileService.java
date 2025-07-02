package com.datacenter.extract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 输出文件服务 - 负责将提取结果写入文件
 * 
 * 功能特性：
 * 1. 自动创建输出目录
 * 2. 按类型和时间组织文件
 * 3. JSON格式标准化输出
 * 4. 文件备份和管理
 */
@Service
public class OutputFileService {

    private static final Logger log = LoggerFactory.getLogger(OutputFileService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String outputBaseDir = "out";

    /**
     * 保存提取结果到文件
     */
    public void saveExtractionResult(String extractType, String content, Map<String, Object> metadata) {
        try {
            // 确保输出目录存在
            ensureOutputDirectory();

            // 生成文件名
            String fileName = generateFileName(extractType);
            Path filePath = Paths.get(outputBaseDir, fileName);

            // 构建输出对象
            ObjectNode outputObject = objectMapper.createObjectNode();
            outputObject.put("extract_type", extractType);
            outputObject.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            outputObject.put("content", content);

            if (metadata != null && !metadata.isEmpty()) {
                JsonNode metadataNode = objectMapper.valueToTree(metadata);
                outputObject.set("metadata", metadataNode);
            }

            // 写入文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), outputObject);

            log.info("成功保存提取结果到文件: {}", filePath);

        } catch (Exception e) {
            log.error("保存提取结果失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存原始AI响应结果
     */
    public void saveRawResult(String extractType, String aiResult, String processedResult) {
        try {
            ensureOutputDirectory();

            String fileName = generateRawFileName(extractType);
            Path filePath = Paths.get(outputBaseDir, "raw", fileName);

            // 确保raw目录存在
            Files.createDirectories(filePath.getParent());

            ObjectNode outputObject = objectMapper.createObjectNode();
            outputObject.put("extract_type", extractType);
            outputObject.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            outputObject.put("ai_result", aiResult);
            outputObject.put("processed_result", processedResult);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), outputObject);

            log.debug("保存原始AI结果到文件: {}", filePath);

        } catch (Exception e) {
            log.error("保存原始AI结果失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存处理过程中的中间结果
     */
    public void saveIntermediateResult(String extractType, String stage, Object data) {
        try {
            ensureOutputDirectory();

            String fileName = generateIntermediateFileName(extractType, stage);
            Path filePath = Paths.get(outputBaseDir, "intermediate", fileName);

            // 确保intermediate目录存在
            Files.createDirectories(filePath.getParent());

            ObjectNode outputObject = objectMapper.createObjectNode();
            outputObject.put("extract_type", extractType);
            outputObject.put("stage", stage);
            outputObject.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            outputObject.set("data", objectMapper.valueToTree(data));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), outputObject);

            log.debug("保存中间结果到文件: {} (阶段: {})", filePath, stage);

        } catch (Exception e) {
            log.error("保存中间结果失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 批量保存提取结果
     */
    public void saveBatchResults(String extractType, Map<String, Object> results) {
        try {
            ensureOutputDirectory();

            String fileName = generateBatchFileName(extractType);
            Path filePath = Paths.get(outputBaseDir, "batch", fileName);

            // 确保batch目录存在
            Files.createDirectories(filePath.getParent());

            ObjectNode outputObject = objectMapper.createObjectNode();
            outputObject.put("extract_type", extractType);
            outputObject.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            outputObject.put("total_count", results.size());
            outputObject.set("results", objectMapper.valueToTree(results));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), outputObject);

            log.info("成功保存批量提取结果到文件: {} (共{}条记录)", filePath, results.size());

        } catch (Exception e) {
            log.error("保存批量提取结果失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取输出目录信息
     */
    public Map<String, Object> getOutputDirectoryInfo() {
        try {
            Path outputPath = Paths.get(outputBaseDir);

            if (!Files.exists(outputPath)) {
                return Map.of("exists", false, "path", outputPath.toAbsolutePath().toString());
            }

            long fileCount = Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .count();

            return Map.of(
                    "exists", true,
                    "path", outputPath.toAbsolutePath().toString(),
                    "file_count", fileCount);

        } catch (IOException e) {
            log.error("获取输出目录信息失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 清理旧的输出文件
     */
    public void cleanupOldFiles(int daysToKeep) {
        try {
            Path outputPath = Paths.get(outputBaseDir);
            if (!Files.exists(outputPath)) {
                return;
            }

            long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000);

            Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("删除旧文件: {}", path);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", path);
                        }
                    });

            log.info("清理完成，删除{}天前的文件", daysToKeep);

        } catch (IOException e) {
            log.error("清理旧文件失败: {}", e.getMessage());
        }
    }

    /**
     * 确保输出目录存在
     */
    private void ensureOutputDirectory() throws IOException {
        Path outputPath = Paths.get(outputBaseDir);
        Files.createDirectories(outputPath);
    }

    /**
     * 生成标准输出文件名
     */
    private String generateFileName(String extractType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("out_%s_%s.json", extractType, timestamp);
    }

    /**
     * 生成原始结果文件名
     */
    private String generateRawFileName(String extractType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("raw_%s_%s.json", extractType, timestamp);
    }

    /**
     * 生成中间结果文件名
     */
    private String generateIntermediateFileName(String extractType, String stage) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("intermediate_%s_%s_%s.json", extractType, stage, timestamp);
    }

    /**
     * 生成批量结果文件名
     */
    private String generateBatchFileName(String extractType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("batch_%s_%s.json", extractType, timestamp);
    }
}
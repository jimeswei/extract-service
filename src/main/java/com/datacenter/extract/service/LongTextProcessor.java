package com.datacenter.extract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 长文本分片处理服务 - v4.0优化版本
 * 专门处理超长文本的智能提取，支持智能分段和并行处理
 * 
 * v4.0更新：
 * - 移除数据库依赖
 * - 集成模板管理系统
 * - 集成文件输出服务
 * - 优化分片算法
 */
@Service
public class LongTextProcessor {

    private static final Logger log = LoggerFactory.getLogger(LongTextProcessor.class);

    private final AIModelCaller aiModelCaller;
    private final TemplateManager templateManager;
    private final OutputFileService outputFileService;
    private final ObjectMapper objectMapper;
    private final ExecutorService batchExecutor;

    // 配置参数
    private static final int MAX_CHUNK_SIZE = 2000; // 单个分片最大字符数
    private static final int MIN_CHUNK_SIZE = 500; // 单个分片最小字符数
    private static final int OVERLAP_SIZE = 200; // 分片重叠字符数，避免丢失边界信息
    private static final int MAX_PARALLEL_CHUNKS = 3; // 最大并行处理分片数

    @Autowired
    public LongTextProcessor(AIModelCaller aiModelCaller,
            TemplateManager templateManager,
            OutputFileService outputFileService) {
        this.aiModelCaller = aiModelCaller;
        this.templateManager = templateManager;
        this.outputFileService = outputFileService;
        this.objectMapper = new ObjectMapper();
        this.batchExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_CHUNKS);
        log.info("LongTextProcessor v4.0 initialized with template management and file output");
    }

    /**
     * 处理长文本 - 主入口
     */
    public String processLongText(String text, String extractType) {
        int textLength = text.length();
        log.info("🔍 开始处理长文本，长度: {} 字符，提取类型: {}", textLength, extractType);

        try {
            // 验证提取类型
            if (!isValidExtractType(extractType)) {
                String error = "不支持的提取类型: " + extractType;
                log.error(error);
                return createErrorResponse(error);
            }

            // 短文本直接处理
            if (textLength <= MAX_CHUNK_SIZE) {
                log.info("📝 文本较短，直接处理");
                String result = aiModelCaller.callAI(text, extractType);

                // 保存结果到文件
                outputFileService.saveRawResult(extractType, result, result);

                return result;
            }

            // 长文本分片处理
            return processInChunks(text, extractType);

        } catch (Exception e) {
            log.error("💥 长文本处理失败: {}", e.getMessage(), e);
            return createErrorResponse("长文本处理失败: " + e.getMessage());
        }
    }

    /**
     * 分片处理长文本
     */
    private String processInChunks(String text, String extractType) {
        try {
            // 1. 智能分片
            List<TextChunk> chunks = smartSplitText(text);
            log.info("📊 文本分片完成，共 {} 个分片", chunks.size());

            // 2. 并行处理分片
            List<CompletableFuture<ChunkResult>> futures = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                int chunkIndex = i;

                CompletableFuture<ChunkResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("⚡ 处理分片 {} ({} 字符)", chunkIndex + 1, chunk.content.length());
                        String result = aiModelCaller.callAI(chunk.content, extractType);

                        // 保存分片结果
                        outputFileService.saveIntermediateResult(extractType, "chunk_" + chunkIndex, result);

                        return new ChunkResult(chunkIndex, chunk, result, true);
                    } catch (Exception e) {
                        log.error("❌ 分片 {} 处理失败: {}", chunkIndex + 1, e.getMessage());
                        return new ChunkResult(chunkIndex, chunk, null, false);
                    }
                }, batchExecutor);

                futures.add(future);
            }

            // 3. 等待所有分片完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 4. 收集和合并结果
            List<ChunkResult> results = new ArrayList<>();
            for (CompletableFuture<ChunkResult> future : futures) {
                results.add(future.get());
            }

            // 5. 合并三元组结果
            String mergedResult = mergeChunkResults(results, extractType);

            // 6. 保存最终合并结果
            outputFileService.saveRawResult(extractType, mergedResult, mergedResult);

            return mergedResult;

        } catch (Exception e) {
            log.error("💥 分片处理失败: {}", e.getMessage(), e);
            return createErrorResponse("分片处理失败: " + e.getMessage());
        }
    }

    /**
     * 智能文本分片 - 按语义边界分割
     */
    private List<TextChunk> smartSplitText(String text) {
        List<TextChunk> chunks = new ArrayList<>();

        // 按段落分割
        String[] paragraphs = text.split("\n\n|\n");

        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty())
                continue;

            // 检查加入当前段落后是否超过限制
            if (currentChunk.length() + paragraph.length() > MAX_CHUNK_SIZE && currentChunk.length() > MIN_CHUNK_SIZE) {
                // 保存当前分片
                chunks.add(new TextChunk(chunkIndex++, currentChunk.toString()));

                // 开始新分片，保留重叠内容
                String overlap = getOverlapContent(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
            }

            currentChunk.append(paragraph).append("\n");
        }

        // 添加最后一个分片
        if (currentChunk.length() > 0) {
            chunks.add(new TextChunk(chunkIndex, currentChunk.toString()));
        }

        // 如果按段落分割后分片太少，按句子进一步分割
        if (chunks.size() == 1 && text.length() > MAX_CHUNK_SIZE) {
            return splitBySentences(text);
        }

        return chunks;
    }

    /**
     * 按句子分割文本
     */
    private List<TextChunk> splitBySentences(String text) {
        List<TextChunk> chunks = new ArrayList<>();

        // 中文句子分割模式
        Pattern sentencePattern = Pattern.compile("[。！？；]+");
        String[] sentences = sentencePattern.split(text);

        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty())
                continue;

            if (currentChunk.length() + sentence.length() > MAX_CHUNK_SIZE && currentChunk.length() > MIN_CHUNK_SIZE) {
                chunks.add(new TextChunk(chunkIndex++, currentChunk.toString()));

                String overlap = getOverlapContent(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
            }

            currentChunk.append(sentence).append("。");
        }

        if (currentChunk.length() > 0) {
            chunks.add(new TextChunk(chunkIndex, currentChunk.toString()));
        }

        return chunks;
    }

    /**
     * 获取重叠内容 - 避免分片边界信息丢失
     */
    private String getOverlapContent(String text) {
        if (text.length() <= OVERLAP_SIZE) {
            return text;
        }

        // 从末尾取重叠内容，尽量按句子边界截取
        String overlap = text.substring(text.length() - OVERLAP_SIZE);

        // 寻找第一个句号后的位置作为起始点
        int firstPeriod = overlap.indexOf("。");
        if (firstPeriod > 0 && firstPeriod < overlap.length() - 1) {
            overlap = overlap.substring(firstPeriod + 1);
        }

        return overlap;
    }

    /**
     * 合并分片结果 - v4.0优化版本
     */
    private String mergeChunkResults(List<ChunkResult> results, String extractType) {
        try {
            ObjectNode mergedResult = objectMapper.createObjectNode();
            ArrayNode allTriples = objectMapper.createArrayNode();

            int successfulChunks = 0;
            int totalChunks = results.size();

            // 处理每个分片的结果
            for (ChunkResult result : results) {
                if (result.success && result.result != null) {
                    try {
                        JsonNode chunkJson = objectMapper.readTree(result.result);

                        // 提取三元组
                        if (chunkJson.has("triples") && chunkJson.get("triples").isArray()) {
                            ArrayNode chunkTriples = (ArrayNode) chunkJson.get("triples");

                            // 验证并添加每个三元组
                            for (JsonNode triple : chunkTriples) {
                                if (isValidTriple(triple) && !isDuplicateTriple(allTriples, triple)) {
                                    allTriples.add(triple);
                                }
                            }
                        }

                        successfulChunks++;

                    } catch (Exception e) {
                        log.warn("解析分片结果失败: {}", e.getMessage());
                    }
                } else {
                    log.warn("分片 {} 处理失败，跳过", result.chunkIndex);
                }
            }

            // 构建最终结果
            mergedResult.put("extract_type", extractType);
            mergedResult.set("triples", allTriples);
            mergedResult.put("total_chunks", totalChunks);
            mergedResult.put("successful_chunks", successfulChunks);
            mergedResult.put("total_triples", allTriples.size());
            mergedResult.put("processing_method", "long_text_chunked");
            mergedResult.put("processed_at", System.currentTimeMillis());

            // 添加处理统计
            ObjectNode stats = objectMapper.createObjectNode();
            stats.put("success_rate", (double) successfulChunks / totalChunks);
            stats.put("avg_triples_per_chunk",
                    successfulChunks > 0 ? (double) allTriples.size() / successfulChunks : 0);
            mergedResult.set("processing_stats", stats);

            log.info("✅ 长文本合并完成: {} 分片 -> {} 三元组", successfulChunks, allTriples.size());

            return objectMapper.writeValueAsString(mergedResult);

        } catch (Exception e) {
            log.error("合并分片结果失败: {}", e.getMessage(), e);
            return createErrorResponse("合并分片结果失败: " + e.getMessage());
        }
    }

    /**
     * 验证三元组有效性
     */
    private boolean isValidTriple(JsonNode triple) {
        return triple.has("subject") && triple.has("predicate") && triple.has("object") &&
                triple.get("subject").asText().trim().length() > 0 &&
                triple.get("predicate").asText().trim().length() > 0 &&
                triple.get("object").asText().trim().length() > 0;
    }

    /**
     * 检查重复三元组
     */
    private boolean isDuplicateTriple(ArrayNode existingTriples, JsonNode newTriple) {
        String newSubject = newTriple.get("subject").asText();
        String newPredicate = newTriple.get("predicate").asText();
        String newObject = newTriple.get("object").asText();

        for (JsonNode existing : existingTriples) {
            if (existing.get("subject").asText().equals(newSubject) &&
                    existing.get("predicate").asText().equals(newPredicate) &&
                    existing.get("object").asText().equals(newObject)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建错误响应
     */
    private String createErrorResponse(String errorMessage) {
        try {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("success", false);
            errorResponse.put("error", errorMessage);
            errorResponse.put("timestamp", System.currentTimeMillis());
            errorResponse.put("service", "LongTextProcessor");
            return objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + errorMessage + "\"}";
        }
    }

    /**
     * 文本分片数据结构
     */
    private static class TextChunk {
        final int index;
        final String content;
        final int startPos;
        final int endPos;

        TextChunk(int index, String content) {
            this.index = index;
            this.content = content;
            this.startPos = 0; // 简化实现
            this.endPos = content.length();
        }
    }

    /**
     * 分片处理结果
     */
    private static class ChunkResult {
        final int chunkIndex;
        final TextChunk chunk;
        final String result;
        final boolean success;

        ChunkResult(int chunkIndex, TextChunk chunk, String result, boolean success) {
            this.chunkIndex = chunkIndex;
            this.chunk = chunk;
            this.result = result;
            this.success = success;
        }
    }

    /**
     * 获取处理统计信息
     */
    public String getProcessingStats() {
        try {
            ObjectNode stats = objectMapper.createObjectNode();
            stats.put("max_chunk_size", MAX_CHUNK_SIZE);
            stats.put("min_chunk_size", MIN_CHUNK_SIZE);
            stats.put("overlap_size", OVERLAP_SIZE);
            stats.put("max_parallel_chunks", MAX_PARALLEL_CHUNKS);
            stats.put("service_version", "v4.0");
            stats.put("features", "template_based,file_output,smart_chunking");
            return objectMapper.writeValueAsString(stats);
        } catch (Exception e) {
            return "{\"error\":\"Failed to get stats\"}";
        }
    }

    /**
     * 验证提取类型是否有效
     */
    private boolean isValidExtractType(String extractType) {
        if (extractType == null || extractType.trim().isEmpty()) {
            return false;
        }

        String[] supportedTypes = templateManager.getSupportedTypes();
        for (String supportedType : supportedTypes) {
            if (supportedType.equalsIgnoreCase(extractType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 资源清理
     */
    public void shutdown() {
        if (batchExecutor != null && !batchExecutor.isShutdown()) {
            batchExecutor.shutdown();
            log.info("LongTextProcessor executor shutdown completed");
        }
    }
}
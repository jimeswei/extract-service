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
 * 长文本分批次处理服务
 * 专门处理超长文本的知识图谱提取，支持智能分段和并行处理
 */
@Service
public class LongTextProcessor {

    private static final Logger log = LoggerFactory.getLogger(LongTextProcessor.class);

    private final AIModelCaller aiModelCaller;
    private final ObjectMapper objectMapper;
    private final ExecutorService batchExecutor;

    // 配置参数
    private static final int MAX_CHUNK_SIZE = 2000; // 单个分片最大字符数
    private static final int MIN_CHUNK_SIZE = 500; // 单个分片最小字符数
    private static final int OVERLAP_SIZE = 200; // 分片重叠字符数，避免丢失边界信息
    private static final int MAX_PARALLEL_CHUNKS = 3; // 最大并行处理分片数

    @Autowired
    public LongTextProcessor(AIModelCaller aiModelCaller) {
        this.aiModelCaller = aiModelCaller;
        this.objectMapper = new ObjectMapper();
        this.batchExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_CHUNKS);
    }

    /**
     * 处理长文本 - 主入口
     */
    public String processLongText(String text, String extractType) {
        int textLength = text.length();
        log.info("🔍 开始处理长文本，长度: {} 字符", textLength);

        try {
            // 短文本直接处理
            if (textLength <= MAX_CHUNK_SIZE) {
                log.info("📝 文本较短，直接处理");
                return aiModelCaller.callAI(text, extractType);
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
            return mergeChunkResults(results, extractType);

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

        String overlap = text.substring(text.length() - OVERLAP_SIZE);

        // 找到第一个完整句子的开始
        int sentenceStart = overlap.indexOf('。');
        if (sentenceStart > 0 && sentenceStart < OVERLAP_SIZE - 50) {
            overlap = overlap.substring(sentenceStart + 1);
        }

        return overlap.trim();
    }

    /**
     * 合并分片结果
     */
    private String mergeChunkResults(List<ChunkResult> results, String extractType) {
        try {
            ObjectNode mergedResult = objectMapper.createObjectNode();
            ArrayNode allTriples = objectMapper.createArrayNode();

            int successCount = 0;
            int totalChunks = results.size();

            for (ChunkResult result : results) {
                if (result.success && result.result != null) {
                    successCount++;

                    try {
                        JsonNode resultJson = objectMapper.readTree(result.result);
                        JsonNode triples = resultJson.get("triples");

                        if (triples != null && triples.isArray()) {
                            // 去重并添加三元组
                            for (JsonNode triple : triples) {
                                if (isValidTriple(triple) && !isDuplicateTriple(allTriples, triple)) {
                                    // 添加分片信息
                                    ObjectNode enhancedTriple = triple.deepCopy();
                                    enhancedTriple.put("chunk_index", result.chunkIndex);
                                    enhancedTriple.put("chunk_start", result.chunk.startPos);
                                    allTriples.add(enhancedTriple);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("⚠️  解析分片 {} 结果失败: {}", result.chunkIndex, e.getMessage());
                    }
                }
            }

            // 构建最终结果
            mergedResult.set("triples", allTriples);
            mergedResult.put("total_chunks", totalChunks);
            mergedResult.put("success_chunks", successCount);
            mergedResult.put("success_rate", String.format("%.2f%%", (double) successCount / totalChunks * 100));
            mergedResult.put("total_triples", allTriples.size());
            mergedResult.put("processing_method", "batch_processing");
            mergedResult.put("timestamp", System.currentTimeMillis());

            log.info("✅ 分片合并完成，成功率: {}/{} ({:.1f}%)，提取三元组: {}",
                    successCount, totalChunks, (double) successCount / totalChunks * 100, allTriples.size());

            return mergedResult.toString();

        } catch (Exception e) {
            log.error("💥 合并结果失败: {}", e.getMessage(), e);
            return createErrorResponse("合并结果失败: " + e.getMessage());
        }
    }

    /**
     * 验证三元组有效性
     */
    private boolean isValidTriple(JsonNode triple) {
        return triple.has("subject") && triple.has("predicate") && triple.has("object") &&
                !triple.get("subject").asText().trim().isEmpty() &&
                !triple.get("predicate").asText().trim().isEmpty() &&
                !triple.get("object").asText().trim().isEmpty();
    }

    /**
     * 检查三元组是否重复
     */
    private boolean isDuplicateTriple(ArrayNode existingTriples, JsonNode newTriple) {
        String newSubject = newTriple.get("subject").asText().trim();
        String newPredicate = newTriple.get("predicate").asText().trim();
        String newObject = newTriple.get("object").asText().trim();

        for (JsonNode existing : existingTriples) {
            String existingSubject = existing.get("subject").asText().trim();
            String existingPredicate = existing.get("predicate").asText().trim();
            String existingObject = existing.get("object").asText().trim();

            if (newSubject.equals(existingSubject) &&
                    newPredicate.equals(existingPredicate) &&
                    newObject.equals(existingObject)) {
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
            ObjectNode errorResult = objectMapper.createObjectNode();
            errorResult.put("error", errorMessage);
            errorResult.put("success", false);
            errorResult.put("timestamp", System.currentTimeMillis());
            return errorResult.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + errorMessage + "\",\"success\":false}";
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
            this.startPos = 0; // 简化版本，实际可以记录在原文中的位置
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
            stats.put("active_threads",
                    MAX_PARALLEL_CHUNKS - ((java.util.concurrent.ThreadPoolExecutor) batchExecutor).getActiveCount());
            return stats.toString();
        } catch (Exception e) {
            return "{\"error\":\"获取统计信息失败\"}";
        }
    }
}
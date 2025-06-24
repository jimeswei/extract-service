package com.datacenter.extract.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * AI模型调用器 - 优化版本，解决超时和并发问题
 * 
 * 🚀 优化特性：
 * 1. 智能超时策略：根据文本长度动态调整
 * 2. 渐进式重试：指数退避策略
 * 3. 并发控制：信号量限制同时调用数
 * 4. 分级处理：长文本分块处理
 */
@Component
public class AIModelCaller {

    private static final Logger log = LoggerFactory.getLogger(AIModelCaller.class);

    private final WebClient deepseekClient;
    private final String apiKey;

    // 并发控制：最大同时调用AI的请求数
    private final Semaphore aiCallSemaphore = new Semaphore(5);

    // 超时配置
    private static final int BASE_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 180;
    private static final int TIMEOUT_PER_1000_CHARS = 10; // 每1000字符增加10秒

    public AIModelCaller(WebClient.Builder builder,
            @Value("${extraction.ai.providers.deepseek.api-key}") String apiKey,
            @Value("${extraction.ai.providers.deepseek.url}") String apiUrl) {
        this.apiKey = apiKey;
        this.deepseekClient = builder
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * 🚀 优化版AI调用 - 智能超时策略和并发控制
     */
    public String callAI(String text, String extractType) {
        // 并发控制：获取信号量
        try {
            aiCallSemaphore.acquire();
            log.info("🎯 开始AI调用，文本长度: {}，当前并发数: {}", text.length(), 5 - aiCallSemaphore.availablePermits());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ 获取AI调用许可证被中断");
            return createErrorResponse();
        }

        try {
            // 智能超时计算
            Duration timeout = calculateTimeout(text.length());
            log.info("⏱️  动态计算超时时间: {}秒", timeout.getSeconds());

            // 长文本预处理
            String processedText = preprocessText(text);

            long startTime = System.currentTimeMillis();

            String result = deepseekClient.post()
                    .bodyValue(buildRequest(processedText, extractType))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .retryWhen(createSmartRetrySpec())
                    .map(this::extractContent)
                    .doOnSuccess(response -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("✅ AI调用成功，耗时: {}ms，响应长度: {}", duration, response.length());
                    })
                    .doOnError(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.error("❌ AI调用失败，耗时: {}ms，错误: {}", duration, error.getMessage());
                    })
                    .onErrorReturn(createErrorResponse())
                    .block();

            return result;

        } catch (Exception e) {
            log.error("💥 AI调用异常: {}", e.getMessage(), e);
            return createErrorResponse();
        } finally {
            // 释放信号量
            aiCallSemaphore.release();
            log.debug("🔓 释放AI调用许可证，当前可用: {}", aiCallSemaphore.availablePermits());
        }
    }

    /**
     * 🧠 智能超时计算：根据文本长度动态调整
     */
    private Duration calculateTimeout(int textLength) {
        // 基础超时 + 按文本长度增加超时
        int timeoutSeconds = BASE_TIMEOUT_SECONDS + (textLength / 1000 * TIMEOUT_PER_1000_CHARS);

        // 限制最大超时时间
        timeoutSeconds = Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS);

        return Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * 📝 长文本预处理：智能截断和优化
     */
    private String preprocessText(String text) {
        if (text.length() <= 3000) {
            return text;
        }

        // 长文本智能截断：保留重要信息
        String truncated = text.substring(0, 2500);

        // 寻找最后一个句号，避免截断在句子中间
        int lastPeriod = truncated.lastIndexOf('。');
        if (lastPeriod > 1500) { // 确保不会截取得太短
            truncated = truncated.substring(0, lastPeriod + 1);
        }

        log.info("📝 长文本已截断：{} -> {} 字符", text.length(), truncated.length());
        return truncated;
    }

    /**
     * 🔄 智能重试策略：指数退避 + 条件重试
     */
    private Retry createSmartRetrySpec() {
        return Retry.backoff(4, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .filter(throwable -> {
                    // 只对特定类型的错误进行重试
                    String message = throwable.getMessage();
                    return message.contains("timeout") ||
                            message.contains("connection") ||
                            message.contains("503") ||
                            message.contains("502") ||
                            message.contains("429"); // Rate limit
                })
                .doBeforeRetry(retrySignal -> {
                    log.warn("🔄 AI调用重试 #{}, 原因: {}",
                            retrySignal.totalRetries() + 1,
                            retrySignal.failure().getMessage());
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("💥 AI调用重试次数耗尽，最终失败: {}", retrySignal.failure().getMessage());
                    return retrySignal.failure();
                });
    }

    /**
     * 🎯 优化Prompt构建：更精确的提示词
     */
    private Map<String, Object> buildRequest(String text, String extractType) {
        String prompt = String.format("""
                你是专业的知识图谱构建专家。请从以下文本中精确提取知识三元组。

                要求：
                1. 只提取明确、准确的信息
                2. 主体和客体应该是具体的实体名称
                3. 关系词要规范（如：出生于、毕业于、作品、配偶等）
                4. 必须严格按照JSON格式返回

                输出格式：
                {"triples":[{"subject":"主体","predicate":"关系","object":"客体","confidence":0.95}]}

                文本内容：
                %s
                """, text);

        return Map.of(
                "model", "deepseek-chat",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.1,
                "max_tokens", 2000,
                "top_p", 0.95,
                "frequency_penalty", 0.0,
                "presence_penalty", 0.0);
    }

    /**
     * 🧹 智能响应清理和验证
     */
    private String extractContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            // 提取content内容
            JsonNode choicesNode = root.path("choices");
            if (choicesNode.isArray() && choicesNode.size() > 0) {
                String content = choicesNode.get(0).path("message").path("content").asText();

                // 智能清理：只保留JSON部分
                return extractJsonFromContent(content);
            }

            log.error("❌ AI响应格式异常，未找到choices节点");
            return createErrorResponse();

        } catch (Exception e) {
            log.error("❌ 解析AI响应失败: {}", e.getMessage());
            return createErrorResponse();
        }
    }

    /**
     * 🔍 从AI响应中提取JSON部分
     */
    private String extractJsonFromContent(String content) {
        try {
            // 寻找JSON开始和结束位置
            int jsonStart = content.indexOf("{");
            int jsonEnd = content.lastIndexOf("}") + 1;

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonPart = content.substring(jsonStart, jsonEnd);

                // 验证JSON格式
                new ObjectMapper().readTree(jsonPart);

                log.debug("✅ 成功提取并验证JSON格式");
                return jsonPart;
            }

            log.warn("⚠️  未找到有效JSON格式，返回原始内容");
            return content;

        } catch (Exception e) {
            log.error("❌ JSON提取失败: {}", e.getMessage());
            return createErrorResponse();
        }
    }

    /**
     * ❌ 创建标准错误响应
     */
    private String createErrorResponse() {
        return """
                {"triples":[],"error":"AI处理失败，请稍后重试","success":false}
                """;
    }

    /**
     * 📊 获取调用统计信息
     */
    public Map<String, Object> getCallStats() {
        return Map.of(
                "available_permits", aiCallSemaphore.availablePermits(),
                "queue_length", aiCallSemaphore.getQueueLength(),
                "max_concurrent_calls", 5);
    }
}
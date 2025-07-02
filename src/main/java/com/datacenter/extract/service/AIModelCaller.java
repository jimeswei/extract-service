package com.datacenter.extract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * AI模型调用器 - 优化版本
 * 
 * v4.0重构亮点：
 * 1. 基于模板的动态Prompt生成
 * 2. 移除数据库依赖，专注AI调用
 * 3. 增强的错误处理和重试机制
 * 4. 性能监控和并发控制
 */
@Component
public class AIModelCaller {

    private static final Logger log = LoggerFactory.getLogger(AIModelCaller.class);

    private final WebClient deepseekClient;
    private final TemplateManager templateManager;
    private final CacheService cacheService;

    private final String apiKey;
    private final boolean isDevelopmentMode; // 新增开发模式标志

    // 并发控制：最多同时5个AI调用
    private final Semaphore aiCallSemaphore = new Semaphore(5);

    // 超时配置
    private static final int BASE_TIMEOUT_SECONDS = 60; // 增加基础超时到60秒
    private static final int MAX_TIMEOUT_SECONDS = 300; // 增加最大超时到5分钟
    private static final int TIMEOUT_PER_1000_CHARS = 15; // 每1000字符增加15秒

    @Autowired
    public AIModelCaller(WebClient.Builder builder,
            @Value("${extraction.ai.providers.deepseek.api-key}") String apiKey,
            @Value("${extraction.ai.providers.deepseek.url}") String apiUrl,
            TemplateManager templateManager,
            CacheService cacheService) {
        this.apiKey = apiKey;
        this.templateManager = templateManager;
        this.cacheService = cacheService;

        // 检测是否为开发模式（API Key为空或默认值）
        this.isDevelopmentMode = apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("sk-d91a");

        if (isDevelopmentMode) {
            log.warn("⚠️  检测到开发模式，将使用模拟AI响应，不进行真实API调用");
            log.warn("⚠️  如需使用真实AI服务，请配置有效的DEEPSEEK_API_KEY环境变量");
            this.deepseekClient = null; // 开发模式下不创建WebClient
        } else {
            log.info("🚀 生产模式，使用真实AI服务");
            this.deepseekClient = builder
                    .baseUrl(apiUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }

        log.info("🚀 AIModelCaller初始化完成，模式: {}，支持模板类型: {}",
                isDevelopmentMode ? "开发模式(模拟)" : "生产模式",
                String.join(", ", templateManager.getSupportedTypes()));
    }

    /**
     * 🧠 智能AI调用 - v4.0模板化版本 + 缓存优化
     */
    public String callAI(String text, String extractType) {
        // 生成缓存键
        String textHash = String.valueOf(text.hashCode());

        // 尝试从缓存获取结果
        String cachedResult = cacheService.getAIResult(textHash, extractType);
        if (cachedResult != null) {
            log.info("🎯 缓存命中，直接返回结果 - 类型: {}", extractType);
            return cachedResult;
        }

        // 获取并发许可证
        try {
            if (!aiCallSemaphore.tryAcquire(10, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("⏰ AI调用队列已满，请求被拒绝");
                return createErrorResponse("系统繁忙，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createErrorResponse("请求被中断");
        }

        try {
            log.info("🎯 开始AI调用 - 类型: {}, 文本长度: {}, 可用许可证: {}",
                    extractType, text.length(), aiCallSemaphore.availablePermits());

            final String finalTextHash = textHash; // 为lambda表达式创建final变量

            // 开发模式：返回模拟响应
            if (isDevelopmentMode) {
                log.info("🎭 开发模式：返回模拟AI响应");
                String mockResponse = createFallbackResponse(extractType);
                // 缓存模拟响应
                cacheService.putAIResult(finalTextHash, extractType, mockResponse);
                return mockResponse;
            }

            // 动态计算超时时间
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
                        // 缓存成功的结果
                        cacheService.putAIResult(finalTextHash, extractType, response);
                        log.debug("💾 AI结果已缓存");
                    })
                    .doOnError(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.error("❌ AI调用失败，耗时: {}ms，错误: {}", duration, error.getMessage());
                    })
                    .onErrorResume(error -> {
                        // 检查是否是超时或连接错误，使用降级响应
                        String errorMsg = error.getMessage();
                        if (errorMsg.contains("timeout") || errorMsg.contains("connection") ||
                                errorMsg.contains("Did not observe any item")) {
                            log.warn("🔄 AI调用失败，启用降级响应: {}", errorMsg);
                            return reactor.core.publisher.Mono.just(createFallbackResponse(extractType));
                        }
                        return reactor.core.publisher.Mono.just(createErrorResponse("AI处理失败，请稍后重试"));
                    })
                    .block();

            return result;

        } catch (Exception e) {
            log.error("💥 AI调用异常: {}", e.getMessage(), e);
            return createErrorResponse("AI调用异常: " + e.getMessage());
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

        // 对于很短的文本，确保至少有60秒超时
        timeoutSeconds = Math.max(timeoutSeconds, 60);

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
        return Retry.backoff(3, Duration.ofSeconds(3)) // 减少重试次数，增加重试间隔
                .maxBackoff(Duration.ofSeconds(60))
                .filter(throwable -> {
                    // 只对特定类型的错误进行重试
                    String message = throwable.getMessage();
                    // 不重试401认证错误
                    if (message.contains("401")) {
                        log.error("🔑 API认证失败，请检查API Key配置");
                        return false;
                    }
                    // 对于超时错误，只重试一次
                    if (message.contains("Did not observe any item") || message.contains("timeout")) {
                        log.warn("⏰ 检测到超时错误，将进行有限重试");
                        return true;
                    }
                    return message.contains("connection") ||
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
                    String errorMessage = retrySignal.failure().getMessage();
                    log.error("💥 AI调用重试次数耗尽，最终失败: {}", errorMessage);

                    // 根据错误类型返回不同的错误信息
                    if (errorMessage.contains("401")) {
                        return new RuntimeException("API认证失败，请检查API Key配置");
                    } else if (errorMessage.contains("429")) {
                        return new RuntimeException("API调用频率超限，请稍后重试");
                    } else if (errorMessage.contains("Did not observe any item") || errorMessage.contains("timeout")) {
                        return new RuntimeException("AI服务响应超时，请检查网络连接或稍后重试");
                    } else {
                        return retrySignal.failure();
                    }
                });
    }

    /**
     * 🎯 基于模板的请求构建 - v4.0核心优化
     */
    private Map<String, Object> buildRequest(String text, String extractType) {
        // 从模板管理器获取Prompt
        String promptTemplate = templateManager.getPromptTemplate(extractType);
        String prompt = String.format(promptTemplate, text);

        log.debug("🎨 使用模板类型: {}, Prompt长度: {}", extractType, prompt.length());

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
            return createErrorResponse("AI响应格式异常");

        } catch (Exception e) {
            log.error("❌ 解析AI响应失败: {}", e.getMessage());
            return createErrorResponse("解析AI响应失败");
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
            return createErrorResponse("JSON格式无效");
        }
    }

    /**
     * ❌ 创建标准错误响应
     */
    private String createErrorResponse(String errorMessage) {
        return String.format("{\"success\":false,\"error\":\"%s\",\"timestamp\":\"%s\"}",
                errorMessage.replace("\"", "'"),
                java.time.Instant.now().toString());
    }

    /**
     * 🔄 创建降级响应 - 当AI服务完全不可用时的基本响应
     */
    private String createFallbackResponse(String extractType) {
        log.warn("🔄 启用降级响应模式，类型: {}", extractType);

        switch (extractType.toLowerCase()) {
            case "celebrity":
                return "{\"success\":true,\"entities\":[],\"message\":\"AI服务暂时不可用，返回空结果\",\"fallback\":true}";
            case "work":
                return "{\"success\":true,\"entities\":[],\"message\":\"AI服务暂时不可用，返回空结果\",\"fallback\":true}";
            case "event":
                return "{\"success\":true,\"entities\":[],\"message\":\"AI服务暂时不可用，返回空结果\",\"fallback\":true}";
            case "triples":
                return "{\"success\":true,\"triples\":[],\"message\":\"AI服务暂时不可用，返回空结果\",\"fallback\":true}";
            case "celebritycelebrity":
                return "{\"success\":true,\"relations\":[],\"message\":\"AI服务暂时不可用，返回空结果\",\"fallback\":true}";
            default:
                return "{\"success\":true,\"entities\":[],\"message\":\"AI服务暂时不可用，返回空结果\",\"fallback\":true}";
        }
    }

    /**
     * 📊 获取调用统计信息
     */
    public Map<String, Object> getCallStats() {
        return Map.of(
                "available_permits", aiCallSemaphore.availablePermits(),
                "queue_length", aiCallSemaphore.getQueueLength(),
                "max_concurrent_calls", 5,
                "supported_templates", templateManager.getSupportedTypes());
    }
}
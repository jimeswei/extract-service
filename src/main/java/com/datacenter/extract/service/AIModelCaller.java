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

/**
 * AI模型调用器 - 按照系统架构设计文档第4.1.1章实现
 * 
 * 巧妙设计：一个方法解决所有AI调用
 * 智能Prompt构建和响应内容提取
 * 自动重试和错误恢复
 */
@Component
public class AIModelCaller {

    private static final Logger log = LoggerFactory.getLogger(AIModelCaller.class);

    private final WebClient deepseekClient;
    private final String apiKey;

    public AIModelCaller(WebClient.Builder builder,
            @Value("${extraction.ai.providers.deepseek.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.deepseekClient = builder
                .baseUrl("https://api.deepseek.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * 巧妙设计：一个方法解决所有AI调用
     * WebClient响应式链条处理逻辑
     * 智能Prompt构建和响应内容提取
     * 自动重试和错误恢复
     */
    public String callAI(String text, String extractType) {
        try {
            return deepseekClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(buildRequest(text, extractType))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)))
                    .map(this::extractContent)
                    .onErrorReturn(createErrorResponse())
                    .block();
        } catch (Exception e) {
            log.error("AI调用失败: {}", e.getMessage());
            return createErrorResponse();
        }
    }

    /**
     * 巧妙设计：智能Prompt构建
     * 按照架构文档要求优化Prompt格式
     */
    private Map<String, Object> buildRequest(String text, String extractType) {
        String prompt = String.format("""
                从文本中提取知识三元组，JSON格式返回：
                {"triples":[{"subject":"主体","predicate":"关系","object":"客体","confidence":0.95}]}

                文本：%s
                """, text.length() > 2000 ? text.substring(0, 2000) + "..." : text);

        return Map.of(
                "model", "deepseek-chat",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.1,
                "max_tokens", 1500);
    }

    /**
     * 巧妙设计：响应内容智能提取
     * 自动清理：只保留JSON部分
     */
    private String extractContent(String response) {
        try {
            JsonNode root = new ObjectMapper().readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            // 智能清理：只保留JSON部分
            int start = content.indexOf("{");
            int end = content.lastIndexOf("}") + 1;
            return start >= 0 && end > start ? content.substring(start, end) : content;

        } catch (Exception e) {
            return createErrorResponse();
        }
    }

    /**
     * 创建标准错误响应
     */
    private String createErrorResponse() {
        return """
                {"triples":[],"error":"AI处理失败","success":false}
                """;
    }
}
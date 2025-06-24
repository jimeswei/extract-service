package com.datacenter.extract.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能AI提供者 - 按照系统架构设计文档第4.1.2章实现
 * 
 * 巧妙设计：缓存+AI调用+兜底的三层保障机制
 * 正则表达式实体提取作为规则兜底
 * Caffeine缓存自动管理
 */
@Component
public class SmartAIProvider {

    private static final Logger log = LoggerFactory.getLogger(SmartAIProvider.class);

    private final AIModelCaller aiCaller;
    private final Cache<String, String> cache;
    private final ObjectMapper objectMapper;

    @Autowired
    public SmartAIProvider(AIModelCaller aiCaller) {
        this.aiCaller = aiCaller;
        this.cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 巧妙设计：缓存+AI调用+兜底的三层保障
     * 第一选择：AI调用
     * 智能判断：如果AI失败，使用规则兜底
     */
    public String process(String text, String extractType) {
        String cacheKey = generateKey(text, extractType);

        return cache.get(cacheKey, key -> {
            // 第一选择：AI调用
            String aiResult = aiCaller.callAI(text, extractType);

            // 智能判断：如果AI失败，使用规则兜底
            if (isValidResponse(aiResult)) {
                log.info("AI提取成功，文本长度: {}", text.length());
                return aiResult;
            } else {
                log.warn("AI提取失败，使用规则兜底处理，类型: {}", extractType);
                return createFallbackResponse(text, extractType);
            }
        });
    }

    /**
     * 巧妙设计：简单规则兜底
     * 使用简单规则提取实体作为兜底
     */
    private String createFallbackResponse(String text, String extractType) {
        // 根据不同的提取类型返回不同格式
        if ("social_extraction".equals(extractType)) {
            return createSocialFallbackResponse(text);
        } else {
            return createTriplesFallbackResponse(text);
        }
    }

    private String createSocialFallbackResponse(String originalText) {
        // 提取人名、作品、事件等实体
        List<String> persons = extractPersons(originalText);
        List<String> works = extractWorks(originalText);
        List<String> events = extractEvents(originalText);

        try {
            return objectMapper.writeValueAsString(Map.of(
                    "entities", Map.of(
                            "persons", persons.stream().map(name -> Map.of(
                                    "name", name,
                                    "nationality", "未知",
                                    "gender", "未知",
                                    "profession", "未知")).collect(Collectors.toList()),
                            "works", works.stream().map(title -> Map.of(
                                    "title", title,
                                    "work_type", "未知",
                                    "release_date", "未知")).collect(Collectors.toList()),
                            "events", events.stream().map(event -> Map.of(
                                    "event_name", event,
                                    "event_type", "未知",
                                    "time", "未知")).collect(Collectors.toList())),
                    "relations", createSimpleRelations(persons, works, events)));
        } catch (Exception e) {
            log.error("社交关系兜底处理异常", e);
            return """
                    {
                      "entities": {"persons": [], "works": [], "events": []},
                      "relations": []
                    }
                    """;
        }
    }

    private String createTriplesFallbackResponse(String text) {
        // 使用简单规则提取实体作为兜底
        List<String> entities = extractSimpleEntities(text);

        List<Map<String, Object>> triples = new ArrayList<>();
        for (int i = 0; i < entities.size() - 1; i++) {
            triples.add(Map.of(
                    "subject", entities.get(i),
                    "predicate", "相关",
                    "object", entities.get(i + 1),
                    "confidence", 0.6));
        }

        try {
            return objectMapper.writeValueAsString(Map.of(
                    "triples", triples,
                    "success", true,
                    "fallback", true,
                    "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("兜底处理异常", e);
            return """
                    {"triples":[],"error":"兜底处理失败","success":false}
                    """;
        }
    }

    private List<String> extractPersons(String text) {
        List<String> persons = new ArrayList<>();

        // 人名模式1：直接职业描述
        Pattern personPattern1 = Pattern
                .compile("(?:导演|演员|歌手|运动员|作家|明星|主演|配偶|原名)([^\u4e00-\u9fa5]*)([\u4e00-\u9fa5]{2,4})");
        persons.addAll(personPattern1.matcher(text)
                .results()
                .map(m -> m.group(2))
                .distinct()
                .collect(Collectors.toList()));

        // 人名模式2：后置描述
        Pattern personPattern2 = Pattern.compile("([\u4e00-\u9fa5]{2,4})(?=是|为|担任|导演|演员|歌手|运动员|，|。|出生|毕业)");
        persons.addAll(personPattern2.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .collect(Collectors.toList()));

        // 去重并限制数量
        return persons.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private List<String> extractWorks(String text) {
        // 作品模式匹配
        Pattern workPattern = Pattern.compile("《([^》]+)》");
        return workPattern.matcher(text)
                .results()
                .map(m -> m.group(1))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<String> extractEvents(String text) {
        // 事件模式匹配
        Pattern eventPattern = Pattern.compile("(第\\d+届[^，。]+(?:电影节|颁奖典礼|奥运会|比赛))");
        return eventPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<Map<String, String>> createSimpleRelations(List<String> persons, List<String> works,
            List<String> events) {
        List<Map<String, String>> relations = new ArrayList<>();

        // 人员-作品关系
        for (String person : persons) {
            for (String work : works) {
                relations.add(Map.of(
                        "source", person,
                        "target", work,
                        "type", "参与"));
            }
        }

        // 人员-事件关系
        for (String person : persons) {
            for (String event : events) {
                relations.add(Map.of(
                        "source", person,
                        "target", event,
                        "type", "参与"));
            }
        }

        return relations;
    }

    /**
     * 巧妙设计：正则简单实体提取
     * 识别组织、人名、地点等实体
     */
    private List<String> extractSimpleEntities(String text) {
        List<String> entities = new ArrayList<>();

        // 组织机构
        Pattern orgPattern = Pattern.compile("[\u4e00-\u9fa5]{2,8}(?:公司|企业|机构|大学|学院|医院|银行|集团)");
        entities.addAll(orgPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .limit(3)
                .collect(Collectors.toList()));

        // 人名（简单规则）
        Pattern personPattern = Pattern.compile("[\u4e00-\u9fa5]{2,4}(?=是|为|担任|创建|创立)");
        entities.addAll(personPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .limit(3)
                .collect(Collectors.toList()));

        // 地点
        Pattern locationPattern = Pattern.compile("[\u4e00-\u9fa5]{2,6}(?:市|省|区|县|国)");
        entities.addAll(locationPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .limit(2)
                .collect(Collectors.toList()));

        return entities.stream().distinct().limit(5).collect(Collectors.toList());
    }

    /**
     * 验证AI响应是否有效
     */
    private boolean isValidResponse(String response) {
        // 检查是否包含有效的数据结构和成功标识，且没有错误
        boolean hasValidStructure = response.contains("triples") ||
                response.contains("entities") ||
                response.contains("relations");
        boolean hasSuccess = response.contains("success");
        boolean hasNoError = !response.contains("error");

        return hasValidStructure && hasSuccess && hasNoError;
    }

    /**
     * 生成缓存键
     */
    private String generateKey(String text, String extractType) {
        return Math.abs((text + extractType).hashCode()) + "";
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        var stats = cache.stats();
        return Map.of(
                "cache_size", cache.estimatedSize(),
                "hit_rate", stats.hitRate(),
                "miss_rate", stats.missRate(),
                "eviction_count", stats.evictionCount());
    }
}
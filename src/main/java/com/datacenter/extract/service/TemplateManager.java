package com.datacenter.extract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板管理服务 - 负责加载和管理提取模板
 * 
 * 功能特性：
 * 1. 统一模板加载机制
 * 2. 模板缓存管理
 * 3. 动态模板切换
 * 4. 模板验证
 */
@Service
public class TemplateManager {

    private static final Logger log = LoggerFactory.getLogger(TemplateManager.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JsonNode> templateCache = new ConcurrentHashMap<>();
    private final CacheService cacheService;

    @Autowired
    public TemplateManager(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("🚀 TemplateManager v4.0 初始化完成 - 集成Redis缓存");
    }

    /**
     * 根据提取类型获取对应的模板
     */
    public JsonNode getTemplate(String extractType) {
        if (extractType == null || extractType.trim().isEmpty()) {
            extractType = "triples"; // 默认使用三元组模板
        }

        String templateKey = extractType.toLowerCase();

        // 从缓存中获取
        JsonNode template = templateCache.get(templateKey);
        if (template != null) {
            log.debug("从缓存获取模板: {}", templateKey);
            return template;
        }

        // 加载模板
        template = loadTemplate(templateKey);
        if (template != null) {
            templateCache.put(templateKey, template);
            log.info("成功加载并缓存模板: {}", templateKey);
        }

        return template;
    }

    /**
     * 获取模板的Prompt文本
     */
    public String getPromptTemplate(String extractType) {
        JsonNode template = getTemplate(extractType);
        if (template != null && template.has("prompt_template")) {
            return template.get("prompt_template").asText();
        }

        // 返回默认的三元组模板
        log.warn("未找到{}类型的prompt模板，使用默认模板", extractType);
        return getDefaultPromptTemplate();
    }

    /**
     * 获取模板的输出结构
     */
    public JsonNode getOutputStructure(String extractType) {
        JsonNode template = getTemplate(extractType);
        if (template != null && template.has("output_structure")) {
            return template.get("output_structure");
        }
        return null;
    }

    /**
     * 获取提取字段列表
     */
    public JsonNode getExtractionFields(String extractType) {
        JsonNode template = getTemplate(extractType);
        if (template != null && template.has("extraction_fields")) {
            return template.get("extraction_fields");
        }
        return null;
    }

    /**
     * 获取支持的实体类型列表
     */
    public String[] getSupportedTypes() {
        return new String[] { "celebrity", "celebritycelebrity", "work", "event", "triples" };
    }

    /**
     * 清理模板缓存
     */
    public void clearCache() {
        templateCache.clear();
        log.info("模板缓存已清理");
    }

    /**
     * 重新加载指定模板
     */
    public void reloadTemplate(String extractType) {
        templateCache.remove(extractType.toLowerCase());
        getTemplate(extractType);
        log.info("重新加载模板: {}", extractType);
    }

    /**
     * 加载模板文件
     */
    private JsonNode loadTemplate(String templateKey) {
        try {
            String templatePath = String.format("templates/input_%s.json", templateKey);
            ClassPathResource resource = new ClassPathResource(templatePath);

            if (!resource.exists()) {
                log.error("模板文件不存在: {}", templatePath);
                return null;
            }

            JsonNode template = objectMapper.readTree(resource.getInputStream());
            log.info("成功加载模板文件: {}", templatePath);

            // 验证模板结构
            if (validateTemplate(template)) {
                return template;
            } else {
                log.error("模板文件格式无效: {}", templatePath);
                return null;
            }

        } catch (IOException e) {
            log.error("加载模板文件失败: {}, 错误: {}", templateKey, e.getMessage());
            return null;
        }
    }

    /**
     * 验证模板结构
     */
    private boolean validateTemplate(JsonNode template) {
        return template.has("entity_type") &&
                template.has("prompt_template") &&
                template.has("output_structure");
    }

    /**
     * 获取默认的Prompt模板
     */
    private String getDefaultPromptTemplate() {
        return """
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
                """;
    }
}
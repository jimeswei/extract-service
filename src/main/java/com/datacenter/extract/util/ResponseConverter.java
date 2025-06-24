package com.datacenter.extract.util;

import com.datacenter.extract.dto.ExtractResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 响应转换工具类
 * 将字符串响应转换为标准JSON对象
 */
public class ResponseConverter {

    private static final Logger log = LoggerFactory.getLogger(ResponseConverter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 转换社交关系提取结果为标准JSON响应
     */
    public static ExtractResponse convertSocialResponse(String jsonResponse) {
        try {
            JsonNode jsonNode = mapper.readTree(jsonResponse);

            // 检查是否成功
            boolean success = jsonNode.has("success") ? jsonNode.get("success").asBoolean() : false;

            if (!success) {
                String error = jsonNode.has("error") ? jsonNode.get("error").asText() : "未知错误";
                return new ExtractResponse(false, null, error);
            }

            // 解析data字段
            if (jsonNode.has("data")) {
                JsonNode dataNode = jsonNode.get("data");
                ExtractResponse.ExtractData extractData = parseExtractData(dataNode);

                // 构建元数据
                ExtractResponse.ExtractMetadata metadata = new ExtractResponse.ExtractMetadata();
                if (jsonNode.has("processing_time")) {
                    metadata.setProcessingTime(jsonNode.get("processing_time").asText());
                }
                if (jsonNode.has("confidence")) {
                    metadata.setConfidence(jsonNode.get("confidence").asDouble());
                }

                return new ExtractResponse(true, extractData, metadata);
            }

            // 如果没有data字段，返回空数据结构
            ExtractResponse.ExtractData emptyData = new ExtractResponse.ExtractData();
            emptyData
                    .setEntities(new ExtractResponse.Entities(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
            emptyData.setRelations(new ArrayList<>());

            return new ExtractResponse(true, emptyData, null);

        } catch (Exception e) {
            log.error("响应转换失败: {}", e.getMessage());
            return new ExtractResponse(false, null, "响应解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析提取数据
     */
    private static ExtractResponse.ExtractData parseExtractData(JsonNode dataNode) {
        ExtractResponse.ExtractData extractData = new ExtractResponse.ExtractData();

        // 解析实体
        if (dataNode.has("entities")) {
            JsonNode entitiesNode = dataNode.get("entities");
            ExtractResponse.Entities entities = parseEntities(entitiesNode);
            extractData.setEntities(entities);
        }

        // 解析关系
        if (dataNode.has("relations")) {
            JsonNode relationsNode = dataNode.get("relations");
            List<ExtractResponse.Relation> relations = parseRelations(relationsNode);
            extractData.setRelations(relations);
        }

        return extractData;
    }

    /**
     * 解析实体信息
     */
    private static ExtractResponse.Entities parseEntities(JsonNode entitiesNode) {
        List<ExtractResponse.Person> persons = new ArrayList<>();
        List<ExtractResponse.Work> works = new ArrayList<>();
        List<ExtractResponse.Event> events = new ArrayList<>();

        // 解析人员
        if (entitiesNode.has("persons")) {
            JsonNode personsNode = entitiesNode.get("persons");
            if (personsNode.isArray()) {
                for (JsonNode personNode : personsNode) {
                    ExtractResponse.Person person = new ExtractResponse.Person();
                    person.setName(getTextValue(personNode, "name"));
                    person.setNationality(getTextValue(personNode, "nationality"));
                    person.setGender(getTextValue(personNode, "gender"));
                    person.setProfession(getTextValue(personNode, "profession"));
                    persons.add(person);
                }
            }
        }

        // 解析作品
        if (entitiesNode.has("works")) {
            JsonNode worksNode = entitiesNode.get("works");
            if (worksNode.isArray()) {
                for (JsonNode workNode : worksNode) {
                    ExtractResponse.Work work = new ExtractResponse.Work();
                    work.setTitle(getTextValue(workNode, "title"));
                    work.setWorkType(getTextValue(workNode, "work_type"));
                    work.setReleaseDate(getTextValue(workNode, "release_date"));
                    works.add(work);
                }
            }
        }

        // 解析事件
        if (entitiesNode.has("events")) {
            JsonNode eventsNode = entitiesNode.get("events");
            if (eventsNode.isArray()) {
                for (JsonNode eventNode : eventsNode) {
                    ExtractResponse.Event event = new ExtractResponse.Event();
                    event.setEventName(getTextValue(eventNode, "event_name"));
                    event.setEventType(getTextValue(eventNode, "event_type"));
                    event.setTime(getTextValue(eventNode, "time"));
                    events.add(event);
                }
            }
        }

        return new ExtractResponse.Entities(persons, works, events);
    }

    /**
     * 解析关系信息
     */
    private static List<ExtractResponse.Relation> parseRelations(JsonNode relationsNode) {
        List<ExtractResponse.Relation> relations = new ArrayList<>();

        if (relationsNode.isArray()) {
            for (JsonNode relationNode : relationsNode) {
                ExtractResponse.Relation relation = new ExtractResponse.Relation();
                relation.setSource(getTextValue(relationNode, "source"));
                relation.setTarget(getTextValue(relationNode, "target"));
                relation.setType(getTextValue(relationNode, "type"));
                relations.add(relation);
            }
        }

        return relations;
    }

    /**
     * 安全获取文本值
     */
    private static String getTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : null;
    }

    /**
     * 创建错误响应
     */
    public static ExtractResponse createErrorResponse(String message) {
        return new ExtractResponse(false, null, message);
    }
}
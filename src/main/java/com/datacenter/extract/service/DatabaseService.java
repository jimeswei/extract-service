package com.datacenter.extract.service;

import com.datacenter.extract.entity.Celebrity;
import com.datacenter.extract.entity.Work;
import com.datacenter.extract.entity.Event;
import com.datacenter.extract.entity.EventType;
import com.datacenter.extract.repository.CelebrityRepository;
import com.datacenter.extract.repository.WorkRepository;
import com.datacenter.extract.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * 数据库服务 - 负责将提取数据存储到MySQL
 * 简洁实现，按照架构设计文档"直接入库"要求
 */
@Service
@Transactional
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    @Autowired
    private CelebrityRepository celebrityRepository;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private EventRepository eventRepository;

    /**
     * 保存提取的社交关系数据到数据库
     */
    public void saveSocialData(String extractionResult) {
        try {
            log.info("开始保存提取数据到数据库，数据长度: {}", extractionResult != null ? extractionResult.length() : 0);

            // 解析AI提取结果
            Map<String, Object> data = parseExtractionResult(extractionResult);

            if (data.isEmpty()) {
                log.warn("解析结果为空，跳过数据库保存");
                return;
            }

            // 优先处理triples格式（AI实际返回格式）
            if (data.containsKey("triples")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> triples = (List<Map<String, Object>>) data.get("triples");
                processTriplesData(triples);
            }
            // 兼容entities格式
            else if (data.containsKey("entities")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entities = (Map<String, Object>) data.get("entities");
                savePersons(entities);
                saveWorks(entities);
                saveEvents(entities);
            }

            // 保存关系信息
            if (data.containsKey("relations")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relations = (List<Map<String, Object>>) data.get("relations");
                saveRelations(relations);
            }

            log.info("🎉 成功保存提取数据到MySQL数据库 (localhost:3306/extract-graph)");

        } catch (Exception e) {
            log.error("保存数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理triples格式数据 - 这是AI实际返回的格式
     */
    private void processTriplesData(List<Map<String, Object>> triples) {
        if (triples == null || triples.isEmpty()) {
            log.info("triples为空，跳过处理");
            return;
        }

        log.info("处理 {} 个三元组", triples.size());

        // 从triples中提取实体
        for (Map<String, Object> triple : triples) {
            String subject = (String) triple.get("subject");
            String predicate = (String) triple.get("predicate");
            String object = (String) triple.get("object");

            if (subject != null && !subject.trim().isEmpty()) {
                // 根据谓词判断主语是人名还是作品名
                if (isPerson(subject, predicate)) {
                    saveSinglePerson(subject, predicate, object);
                } else if (isWork(subject, predicate)) {
                    saveSingleWork(subject, predicate, object);
                }
            }

            // 处理宾语也可能是实体的情况
            if (object != null && !object.trim().isEmpty()) {
                if (isPerson(object, predicate)) {
                    saveSinglePerson(object, predicate, subject);
                }
            }

            // 记录关系
            log.info("🔗 关系记录: {} --[{}]--> {}", subject, predicate, object);
        }
    }

    /**
     * 判断是否为人员实体
     */
    private boolean isPerson(String entity, String predicate) {
        // 常见的人员相关谓词
        return predicate.contains("出生") || predicate.contains("结婚") || predicate.contains("职业") ||
                predicate.contains("导演") || predicate.contains("主演") || predicate.contains("歌手") ||
                predicate.contains("演员") || predicate.contains("制片") || predicate.contains("编剧") ||
                entity.matches(".*[杰明华伦龙云飞雪莉].*"); // 简单的人名模式匹配
    }

    /**
     * 判断是否为作品实体
     */
    private boolean isWork(String entity, String predicate) {
        // 作品通常用书名号包围，或包含特定词汇
        return entity.startsWith("《") && entity.endsWith("》") ||
                predicate.contains("作品") || predicate.contains("电影") || predicate.contains("歌曲") ||
                predicate.contains("专辑") || predicate.contains("小说");
    }

    /**
     * 保存单个人员信息
     */
    private void saveSinglePerson(String name, String predicate, String value) {
        try {
            // 检查是否已存在
            if (celebrityRepository.existsByName(name)) {
                log.info("人员 {} 已存在，跳过插入", name);
                return;
            }

            // 创建Celebrity实体
            Celebrity celebrity = new Celebrity();
            celebrity.setCelebrityId(generateId());
            celebrity.setName(name);

            // 根据谓词设置相应字段
            if (predicate.contains("职业") || predicate.contains("歌手") || predicate.contains("演员")) {
                celebrity.setProfession(value);
            } else if (predicate.contains("出生")) {
                celebrity.setBirthdate(value);
            } else if (predicate.contains("结婚") || predicate.contains("配偶")) {
                celebrity.setSpouse(value);
            }

            // 保存到数据库
            celebrityRepository.save(celebrity);
            log.info("✅ 成功保存人员: {} (通过三元组提取)", name);

        } catch (Exception e) {
            log.error("保存人员 {} 失败: {}", name, e.getMessage());
        }
    }

    /**
     * 保存单个作品信息
     */
    private void saveSingleWork(String title, String predicate, String value) {
        try {
            // 检查是否已存在
            if (workRepository.existsByTitle(title)) {
                log.info("作品 {} 已存在，跳过插入", title);
                return;
            }

            // 创建Work实体
            Work work = new Work();
            work.setWorkId(generateId());
            work.setTitle(title);

            // 根据谓词设置相应字段
            if (predicate.contains("类型")) {
                work.setWorkType(value);
            } else if (predicate.contains("发布") || predicate.contains("上映")) {
                work.setReleaseDate(value);
            }

            // 保存到数据库
            workRepository.save(work);
            log.info("✅ 成功保存作品: {} (通过三元组提取)", title);

        } catch (Exception e) {
            log.error("保存作品 {} 失败: {}", title, e.getMessage());
        }
    }

    /**
     * 保存人员信息到celebrity表 - 真实MySQL入库
     */
    private void savePersons(Map<String, Object> entities) {
        if (!entities.containsKey("persons"))
            return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> persons = (List<Map<String, Object>>) entities.get("persons");

        for (Map<String, Object> person : persons) {
            String name = (String) person.get("name");

            // 检查是否已存在，避免重复插入
            if (celebrityRepository.existsByName(name)) {
                log.info("人员 {} 已存在，跳过插入", name);
                continue;
            }

            // 创建Celebrity实体
            Celebrity celebrity = new Celebrity();
            celebrity.setCelebrityId(generateId());
            celebrity.setName(name);
            celebrity.setNationality((String) person.get("nationality"));
            celebrity.setBirthdate((String) person.get("birthdate"));
            celebrity.setGender((String) person.get("gender"));
            celebrity.setProfession((String) person.get("profession"));
            celebrity.setSpouse((String) person.get("spouse"));
            celebrity.setCompany((String) person.get("company"));
            celebrity.setPosition((String) person.get("position"));
            celebrity.setEducation((String) person.get("education"));

            // 保存到数据库
            celebrityRepository.save(celebrity);
            log.info("✅ 成功保存人员: {}", name);
        }
    }

    /**
     * 保存作品信息到work表 - 真实MySQL入库
     */
    private void saveWorks(Map<String, Object> entities) {
        if (!entities.containsKey("works"))
            return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> works = (List<Map<String, Object>>) entities.get("works");

        for (Map<String, Object> workData : works) {
            String title = (String) workData.get("title");

            // 检查是否已存在
            if (workRepository.existsByTitle(title)) {
                log.info("作品 {} 已存在，跳过插入", title);
                continue;
            }

            // 创建Work实体
            Work work = new Work();
            work.setWorkId(generateId());
            work.setTitle(title);
            work.setWorkType((String) workData.get("work_type"));
            work.setReleaseDate((String) workData.get("release_date"));
            work.setRole((String) workData.get("role"));
            work.setPlatform((String) workData.get("platform"));
            work.setAwards((String) workData.get("awards"));
            work.setDescription((String) workData.get("description"));

            // 保存到数据库
            workRepository.save(work);
            log.info("✅ 成功保存作品: {}", title);
        }
    }

    /**
     * 保存事件信息到event表 - 真实MySQL入库
     */
    private void saveEvents(Map<String, Object> entities) {
        if (!entities.containsKey("events"))
            return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) entities.get("events");

        for (Map<String, Object> eventData : events) {
            String eventName = (String) eventData.get("event_name");

            // 检查是否已存在
            if (eventRepository.existsByEventName(eventName)) {
                log.info("事件 {} 已存在，跳过插入", eventName);
                continue;
            }

            // 创建Event实体
            Event event = new Event();
            event.setEventId(generateId());
            event.setEventName(eventName);
            event.setTime((String) eventData.get("time"));

            // 处理事件类型
            String eventTypeStr = (String) eventData.get("event_type");
            if (eventTypeStr != null) {
                try {
                    EventType eventType = EventType.valueOf(eventTypeStr);
                    event.setEventType(eventType);
                } catch (IllegalArgumentException e) {
                    event.setEventType(EventType.其他);
                }
            }

            // 保存到数据库
            eventRepository.save(event);
            log.info("✅ 成功保存事件: {}", eventName);
        }
    }

    /**
     * 保存关系信息到关系表 - 简化实现
     * 注：关系表需要复杂的映射逻辑，暂时记录日志
     */
    private void saveRelations(List<Map<String, Object>> relations) {
        for (Map<String, Object> relation : relations) {
            String source = (String) relation.get("source");
            String target = (String) relation.get("target");
            String type = (String) relation.get("type");

            // TODO: 实现关系表的复杂映射逻辑
            // 需要确定source和target是人员、作品还是事件，然后写入对应关系表
            log.info("🔗 关系记录: {} --[{}]--> {}", source, type, target);
        }
    }

    /**
     * 解析提取结果JSON
     */
    private Map<String, Object> parseExtractionResult(String result) {
        try {
            log.info("原始AI返回数据: {}", result);

            // 简单的JSON解析实现
            if (result == null || result.trim().isEmpty()) {
                log.warn("AI返回数据为空");
                return Map.of();
            }

            // 提取JSON部分
            String jsonPart = extractJsonFromResult(result);
            if (jsonPart.isEmpty()) {
                log.warn("无法从AI返回数据中提取JSON部分");
                return Map.of();
            }

            log.info("提取的JSON部分: {}", jsonPart);

            // 使用Jackson ObjectMapper解析
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(jsonPart, Map.class);

            log.info("成功解析提取结果，包含 {} 个主要字段: {}", data.size(), data.keySet());
            return data;

        } catch (Exception e) {
            log.error("解析提取结果失败: {}, 原始数据: {}", e.getMessage(), result);
            // 不再使用模拟数据，返回空Map让调用方知道解析失败
            return Map.of();
        }
    }

    /**
     * 从AI结果中提取JSON部分
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
     * 生成唯一ID
     */
    private String generateId() {
        return "ID_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
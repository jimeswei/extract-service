package com.datacenter.extract.service;

import com.datacenter.extract.entity.Celebrity;
import com.datacenter.extract.entity.Work;
import com.datacenter.extract.entity.Event;
import com.datacenter.extract.entity.EventType;
import com.datacenter.extract.entity.CelebrityCelebrity;
import com.datacenter.extract.entity.CelebrityWork;
import com.datacenter.extract.entity.CelebrityEvent;
import com.datacenter.extract.entity.EventWork;
import com.datacenter.extract.repository.CelebrityRepository;
import com.datacenter.extract.repository.WorkRepository;
import com.datacenter.extract.repository.EventRepository;
import com.datacenter.extract.repository.CelebrityCelebrityRepository;
import com.datacenter.extract.repository.CelebrityWorkRepository;
import com.datacenter.extract.repository.CelebrityEventRepository;
import com.datacenter.extract.repository.EventWorkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

/**
 * 数据库服务 - 负责将提取数据存储到MySQL
 * 完整实现，支持所有7张表的数据保存
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

    @Autowired
    private CelebrityCelebrityRepository celebrityCelebrityRepository;

    @Autowired
    private CelebrityWorkRepository celebrityWorkRepository;

    @Autowired
    private CelebrityEventRepository celebrityEventRepository;

    @Autowired
    private EventWorkRepository eventWorkRepository;

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
                processTriplesDataWithRelations(triples);
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
                saveRelationsReal(relations);
            }

            log.info("🎉 成功保存提取数据到MySQL数据库 (localhost:3306/extract-graph)");

        } catch (Exception e) {
            log.error("保存数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理triples格式数据 - 包含关系保存逻辑
     */
    private void processTriplesDataWithRelations(List<Map<String, Object>> triples) {
        if (triples == null || triples.isEmpty()) {
            log.info("triples为空，跳过处理");
            return;
        }

        log.info("处理 {} 个三元组，包括实体和关系保存", triples.size());

        // 从triples中提取实体
        for (Map<String, Object> triple : triples) {
            String subject = (String) triple.get("subject");
            String predicate = (String) triple.get("predicate");
            String object = (String) triple.get("object");

            if (subject != null && !subject.trim().isEmpty()) {
                // 保存主语实体
                if (isPerson(subject, predicate)) {
                    saveSinglePerson(subject, predicate, object);
                } else if (isWork(subject, predicate)) {
                    saveSingleWork(subject, predicate, object);
                } else if (isEvent(subject, predicate)) {
                    saveSingleEvent(subject, predicate, object);
                }
            }

            // 保存宾语实体
            if (object != null && !object.trim().isEmpty()) {
                if (isPerson(object, predicate)) {
                    saveSinglePerson(object, predicate, subject);
                } else if (isWork(object, predicate)) {
                    saveSingleWork(object, predicate, subject);
                } else if (isEvent(object, predicate)) {
                    saveSingleEvent(object, predicate, subject);
                }
            }

            // 保存关系
            saveTripleRelation(subject, predicate, object);

            // 记录关系
            log.info("🔗 关系记录: {} --[{}]--> {}", subject, predicate, object);
        }
    }

    /**
     * 保存三元组关系到对应的关系表
     */
    private void saveTripleRelation(String subject, String predicate, String object) {
        try {
            boolean subjectIsPerson = isPerson(subject, predicate);
            boolean objectIsPerson = isPerson(object, predicate);
            boolean subjectIsWork = isWork(subject, predicate);
            boolean objectIsWork = isWork(object, predicate);
            boolean subjectIsEvent = isEvent(subject, predicate);
            boolean objectIsEvent = isEvent(object, predicate);

            // 人-人关系
            if (subjectIsPerson && objectIsPerson) {
                CelebrityCelebrity relation = new CelebrityCelebrity(
                        getCelebrityIdByName(subject),
                        getCelebrityIdByName(object),
                        predicate);
                celebrityCelebrityRepository.save(relation);
                log.info("✅ 保存人人关系: {} -> {} [{}]", subject, object, predicate);
            }
            // 人-作品关系
            else if (subjectIsPerson && objectIsWork) {
                CelebrityWork relation = new CelebrityWork(
                        getCelebrityIdByName(subject),
                        getWorkIdByTitle(object),
                        predicate);
                celebrityWorkRepository.save(relation);
                log.info("✅ 保存人作品关系: {} -> {} [{}]", subject, object, predicate);
            }
            // 作品-人关系 (反向)
            else if (subjectIsWork && objectIsPerson) {
                CelebrityWork relation = new CelebrityWork(
                        getCelebrityIdByName(object),
                        getWorkIdByTitle(subject),
                        "参与_" + predicate);
                celebrityWorkRepository.save(relation);
                log.info("✅ 保存人作品关系(反向): {} -> {} [{}]", object, subject, "参与_" + predicate);
            }
            // 人-事件关系
            else if (subjectIsPerson && objectIsEvent) {
                CelebrityEvent relation = new CelebrityEvent(
                        getCelebrityIdByName(subject),
                        getEventIdByName(object),
                        predicate);
                celebrityEventRepository.save(relation);
                log.info("✅ 保存人事件关系: {} -> {} [{}]", subject, object, predicate);
            }
            // 事件-人关系 (反向)
            else if (subjectIsEvent && objectIsPerson) {
                CelebrityEvent relation = new CelebrityEvent(
                        getCelebrityIdByName(object),
                        getEventIdByName(subject),
                        "参与_" + predicate);
                celebrityEventRepository.save(relation);
                log.info("✅ 保存人事件关系(反向): {} -> {} [{}]", object, subject, "参与_" + predicate);
            }
            // 事件-作品关系
            else if (subjectIsEvent && objectIsWork) {
                EventWork relation = new EventWork(
                        getEventIdByName(subject),
                        getWorkIdByTitle(object),
                        predicate);
                eventWorkRepository.save(relation);
                log.info("✅ 保存事件作品关系: {} -> {} [{}]", subject, object, predicate);
            }
            // 作品-事件关系 (反向)
            else if (subjectIsWork && objectIsEvent) {
                EventWork relation = new EventWork(
                        getEventIdByName(object),
                        getWorkIdByTitle(subject),
                        "在_" + predicate);
                eventWorkRepository.save(relation);
                log.info("✅ 保存事件作品关系(反向): {} -> {} [{}]", object, subject, "在_" + predicate);
            }

        } catch (Exception e) {
            log.error("保存关系失败: {} -> {} [{}], 错误: {}", subject, object, predicate, e.getMessage());
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
                predicate.contains("合作") || predicate.contains("配偶") || predicate.contains("朋友") ||
                entity.matches(".*[杰明华伦龙云飞雪莉艺谋德华连杰朝伟润发学友富城黎明成龙].*"); // 扩展人名模式匹配
    }

    /**
     * 判断是否为作品实体
     */
    private boolean isWork(String entity, String predicate) {
        // 作品通常用书名号包围，或包含特定词汇
        return entity.startsWith("《") && entity.endsWith("》") ||
                predicate.contains("作品") || predicate.contains("电影") || predicate.contains("歌曲") ||
                predicate.contains("专辑") || predicate.contains("小说") || predicate.contains("主演") ||
                predicate.contains("演唱") || entity.contains("专辑");
    }

    /**
     * 判断是否为事件实体
     */
    private boolean isEvent(String entity, String predicate) {
        return entity.contains("颁奖典礼") || entity.contains("电影节") || entity.contains("开幕式") ||
                entity.contains("活动") || entity.contains("仪式") || entity.contains("节目") ||
                predicate.contains("参加") || predicate.contains("举行") || predicate.contains("展映");
    }

    // 辅助方法：根据名字获取celebrity_id
    private String getCelebrityIdByName(String name) {
        Optional<Celebrity> celebrity = celebrityRepository.findByName(name);
        return celebrity.isPresent() ? celebrity.get().getCelebrityId() : generateId();
    }

    // 辅助方法：根据标题获取work_id
    private String getWorkIdByTitle(String title) {
        Optional<Work> work = workRepository.findByTitle(title);
        return work.isPresent() ? work.get().getWorkId() : generateId();
    }

    // 辅助方法：根据名称获取event_id
    private String getEventIdByName(String eventName) {
        Optional<Event> event = eventRepository.findByEventName(eventName);
        return event.isPresent() ? event.get().getEventId() : generateId();
    }

    /**
     * 保存单个事件信息
     */
    private void saveSingleEvent(String eventName, String predicate, String value) {
        try {
            // 检查是否已存在
            if (eventRepository.existsByEventName(eventName)) {
                log.info("事件 {} 已存在，跳过插入", eventName);
                return;
            }

            // 创建Event实体
            Event event = new Event();
            event.setEventId(generateId());
            event.setEventName(eventName);

            // 根据谓词设置相应字段
            if (predicate.contains("时间") || predicate.contains("举行")) {
                event.setTime(value);
            }

            // 设置事件类型
            if (eventName.contains("颁奖典礼")) {
                event.setEventType(EventType.颁奖典礼);
            } else if (eventName.contains("电影节")) {
                event.setEventType(EventType.其他);
            } else {
                event.setEventType(EventType.其他);
            }

            // 保存到数据库
            eventRepository.save(event);
            log.info("✅ 成功保存事件: {} (通过三元组提取)", eventName);

        } catch (Exception e) {
            log.error("保存事件 {} 失败: {}", eventName, e.getMessage());
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
     * 保存关系信息到关系表 - 完整实现
     */
    private void saveRelationsReal(List<Map<String, Object>> relations) {
        for (Map<String, Object> relation : relations) {
            String source = (String) relation.get("source");
            String target = (String) relation.get("target");
            String type = (String) relation.get("type");

            // 实现关系表的映射逻辑
            saveTripleRelation(source, type, target);
            log.info("🔗 关系记录: {} --[{}]--> {}", source, type, target);
        }
    }
}
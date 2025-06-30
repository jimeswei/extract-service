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
import java.math.BigDecimal;

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

    @Autowired
    private KnowledgeGraphEngine knowledgeGraphEngine;

    @Autowired
    private EntityDisambiguator entityDisambiguator;

    @Autowired
    private KnowledgeFusion knowledgeFusion;

    /**
     * 保存提取的社交关系数据到数据库 - v3.0增强版
     * 
     * @param extractionResult AI提取结果
     * @param kgMode           知识图谱处理模式 (standard/enhanced/fusion)
     */
    public void saveSocialDataEnhanced(String extractionResult, String kgMode) {
        try {
            log.info("开始保存提取数据到数据库，模式: {}, 数据长度: {}",
                    kgMode, extractionResult != null ? extractionResult.length() : 0);

            // 解析AI提取结果
            Map<String, Object> data = parseExtractionResult(extractionResult);

            if (data.isEmpty()) {
                log.warn("解析结果为空，跳过数据库保存");
                return;
            }

            // v3.0: 根据模式进行知识图谱处理
            if ("enhanced".equals(kgMode) || "fusion".equals(kgMode)) {
                log.info("应用知识图谱增强处理，模式: {}", kgMode);

                // 实体消歧义
                if (data.containsKey("triples")) {
                    data = entityDisambiguator.disambiguate(data);
                }

                // 知识融合（仅fusion模式）
                if ("fusion".equals(kgMode)) {
                    data = knowledgeFusion.fuseKnowledge(data);
                }
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

            log.info("🎉 成功保存提取数据到MySQL数据库 (模式: {})", kgMode);

        } catch (Exception e) {
            log.error("保存数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存提取的社交关系数据到数据库 - 向后兼容版本
     */
    public void saveSocialData(String extractionResult) {
        saveSocialDataEnhanced(extractionResult, "standard");
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
     * 保存单个事件信息 - 支持知识融合
     */
    private void saveSingleEvent(String eventName, String predicate, String value) {
        try {
            // 查找已存在的实体
            Optional<Event> existingOpt = eventRepository.findByEventName(eventName);

            if (existingOpt.isPresent()) {
                // 存在则进行知识融合
                Event existing = existingOpt.get();
                log.info("发现已存在事件: {}，进行知识融合", eventName);

                // 根据谓词更新或补充属性
                boolean updated = false;

                if ((predicate.contains("时间") || predicate.contains("举行")) && existing.getTime() == null) {
                    existing.setTime(value);
                    updated = true;
                }

                // 如果事件类型未设置，尝试设置
                if (existing.getEventType() == null) {
                    if (eventName.contains("颁奖典礼")) {
                        existing.setEventType(EventType.颁奖典礼);
                        updated = true;
                    } else if (eventName.contains("电影节")) {
                        existing.setEventType(EventType.其他);
                        updated = true;
                    }
                }

                // 更新置信度（如果有）
                if (existing.getConfidenceScore() != null) {
                    existing.setConfidenceScore(
                            BigDecimal.valueOf((existing.getConfidenceScore().doubleValue() + 0.9) / 2));
                } else {
                    existing.setConfidenceScore(BigDecimal.valueOf(0.9));
                }

                if (updated) {
                    existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);
                    eventRepository.save(existing);
                    log.info("✅ 成功融合更新事件信息: {} (版本: {})", eventName, existing.getVersion());
                } else {
                    log.info("事件 {} 信息已完整，无需更新", eventName);
                }

            } else {
                // 不存在则创建新实体
                Event event = new Event();
                event.setEventId(generateId());
                event.setEventName(eventName);
                event.setConfidenceScore(BigDecimal.valueOf(0.8));
                event.setVersion(1);

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

                eventRepository.save(event);
                log.info("✅ 成功创建新事件: {} (通过三元组提取)", eventName);
            }

        } catch (Exception e) {
            log.error("保存事件 {} 失败: {}", eventName, e.getMessage());
        }
    }

    /**
     * 保存人员信息到celebrity表 - 支持知识融合
     */
    private void savePersons(Map<String, Object> entities) {
        if (!entities.containsKey("persons"))
            return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> persons = (List<Map<String, Object>>) entities.get("persons");

        for (Map<String, Object> person : persons) {
            String name = (String) person.get("name");

            // 查找已存在的实体
            Optional<Celebrity> existingOpt = celebrityRepository.findByName(name);

            if (existingOpt.isPresent()) {
                // 存在则进行知识融合
                Celebrity existing = existingOpt.get();
                log.info("发现已存在人员: {}，进行知识融合", name);

                // 使用知识融合服务进行属性合并
                Celebrity newData = new Celebrity();
                newData.setName(name);
                newData.setNationality((String) person.get("nationality"));
                newData.setBirthdate((String) person.get("birthdate"));
                newData.setGender((String) person.get("gender"));
                newData.setProfession((String) person.get("profession"));
                newData.setSpouse((String) person.get("spouse"));
                newData.setCompany((String) person.get("company"));
                newData.setPosition((String) person.get("position"));
                newData.setEducation((String) person.get("education"));
                newData.setConfidenceScore(BigDecimal.valueOf(0.85));

                // 调用知识融合服务
                Celebrity fused = knowledgeFusion.fuseCelebrityAttributes(existing, newData);
                celebrityRepository.save(fused);
                log.info("✅ 成功融合更新人员: {} (版本: {})", name, fused.getVersion());

            } else {
                // 不存在则创建新实体
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
                celebrity.setConfidenceScore(BigDecimal.valueOf(0.8));
                celebrity.setVersion(1);

                celebrityRepository.save(celebrity);
                log.info("✅ 成功创建新人员: {}", name);
            }
        }
    }

    /**
     * 保存作品信息到work表 - 支持知识融合
     */
    private void saveWorks(Map<String, Object> entities) {
        if (!entities.containsKey("works"))
            return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> works = (List<Map<String, Object>>) entities.get("works");

        for (Map<String, Object> workData : works) {
            String title = (String) workData.get("title");

            // 查找已存在的实体
            Optional<Work> existingOpt = workRepository.findByTitle(title);

            if (existingOpt.isPresent()) {
                // 存在则进行知识融合
                Work existing = existingOpt.get();
                log.info("发现已存在作品: {}，进行知识融合", title);

                // 使用知识融合服务进行属性合并
                Work newData = new Work();
                newData.setTitle(title);
                newData.setWorkType((String) workData.get("work_type"));
                newData.setReleaseDate((String) workData.get("release_date"));
                newData.setRole((String) workData.get("role"));
                newData.setPlatform((String) workData.get("platform"));
                newData.setAwards((String) workData.get("awards"));
                newData.setDescription((String) workData.get("description"));
                newData.setConfidenceScore(BigDecimal.valueOf(0.85));

                // 调用知识融合服务
                Work fused = knowledgeFusion.fuseWorkAttributes(existing, newData);
                workRepository.save(fused);
                log.info("✅ 成功融合更新作品: {} (版本: {})", title, fused.getVersion());

            } else {
                // 不存在则创建新实体
                Work work = new Work();
                work.setWorkId(generateId());
                work.setTitle(title);
                work.setWorkType((String) workData.get("work_type"));
                work.setReleaseDate((String) workData.get("release_date"));
                work.setRole((String) workData.get("role"));
                work.setPlatform((String) workData.get("platform"));
                work.setAwards((String) workData.get("awards"));
                work.setDescription((String) workData.get("description"));
                work.setConfidenceScore(BigDecimal.valueOf(0.8));
                work.setVersion(1);

                workRepository.save(work);
                log.info("✅ 成功创建新作品: {}", title);
            }
        }
    }

    /**
     * 保存事件信息到event表 - 支持知识融合
     */
    private void saveEvents(Map<String, Object> entities) {
        if (!entities.containsKey("events"))
            return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) entities.get("events");

        for (Map<String, Object> eventData : events) {
            String eventName = (String) eventData.get("event_name");

            // 查找已存在的实体
            Optional<Event> existingOpt = eventRepository.findByEventName(eventName);

            if (existingOpt.isPresent()) {
                // 存在则进行知识融合
                Event existing = existingOpt.get();
                log.info("发现已存在事件: {}，进行知识融合", eventName);

                // 根据新数据更新或补充属性
                boolean updated = false;

                String newTime = (String) eventData.get("time");
                if (newTime != null && existing.getTime() == null) {
                    existing.setTime(newTime);
                    updated = true;
                }

                // 处理事件类型
                String eventTypeStr = (String) eventData.get("event_type");
                if (eventTypeStr != null && existing.getEventType() == null) {
                    try {
                        EventType eventType = EventType.valueOf(eventTypeStr);
                        existing.setEventType(eventType);
                        updated = true;
                    } catch (IllegalArgumentException e) {
                        existing.setEventType(EventType.其他);
                        updated = true;
                    }
                }

                // 更新置信度
                if (existing.getConfidenceScore() != null) {
                    existing.setConfidenceScore(
                            BigDecimal.valueOf((existing.getConfidenceScore().doubleValue() + 0.85) / 2));
                } else {
                    existing.setConfidenceScore(BigDecimal.valueOf(0.85));
                }

                if (updated) {
                    existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);
                    eventRepository.save(existing);
                    log.info("✅ 成功融合更新事件: {} (版本: {})", eventName, existing.getVersion());
                } else {
                    log.info("事件 {} 信息已完整，无需更新", eventName);
                }

            } else {
                // 不存在则创建新实体
                Event event = new Event();
                event.setEventId(generateId());
                event.setEventName(eventName);
                event.setTime((String) eventData.get("time"));
                event.setConfidenceScore(BigDecimal.valueOf(0.8));
                event.setVersion(1);

                // 处理事件类型
                String eventTypeStr = (String) eventData.get("event_type");
                if (eventTypeStr != null) {
                    try {
                        EventType eventType = EventType.valueOf(eventTypeStr);
                        event.setEventType(eventType);
                    } catch (IllegalArgumentException e) {
                        event.setEventType(EventType.其他);
                    }
                } else {
                    event.setEventType(EventType.其他);
                }

                eventRepository.save(event);
                log.info("✅ 成功创建新事件: {}", eventName);
            }
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
     * 保存单个人员信息 - 支持知识融合
     */
    private void saveSinglePerson(String name, String predicate, String value) {
        try {
            // 查找已存在的实体
            Optional<Celebrity> existingOpt = celebrityRepository.findByName(name);

            if (existingOpt.isPresent()) {
                // 存在则进行知识融合
                Celebrity existing = existingOpt.get();
                log.info("发现已存在实体: {}，进行知识融合", name);

                // 根据谓词更新或补充属性
                boolean updated = false;

                if (predicate.contains("出生") && existing.getBirthdate() == null) {
                    existing.setBirthdate(value);
                    updated = true;
                } else if (predicate.contains("职业") && existing.getProfession() == null) {
                    existing.setProfession(value);
                    updated = true;
                } else if (predicate.contains("国籍") && existing.getNationality() == null) {
                    existing.setNationality(value);
                    updated = true;
                } else if (predicate.contains("配偶") && existing.getSpouse() == null) {
                    existing.setSpouse(value);
                    updated = true;
                } else if (predicate.contains("公司") && existing.getCompany() == null) {
                    existing.setCompany(value);
                    updated = true;
                } else if (predicate.contains("学历") && existing.getEducation() == null) {
                    existing.setEducation(value);
                    updated = true;
                }

                // 更新置信度（如果有）
                if (existing.getConfidenceScore() != null) {
                    existing.setConfidenceScore(
                            BigDecimal.valueOf((existing.getConfidenceScore().doubleValue() + 0.9) / 2));
                } else {
                    existing.setConfidenceScore(BigDecimal.valueOf(0.9));
                }

                if (updated) {
                    existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);
                    celebrityRepository.save(existing);
                    log.info("✅ 成功融合更新人员信息: {} (版本: {})", name, existing.getVersion());
                } else {
                    log.info("人员 {} 信息已完整，无需更新", name);
                }

            } else {
                // 不存在则创建新实体
                Celebrity celebrity = new Celebrity();
                celebrity.setCelebrityId(generateId());
                celebrity.setName(name);
                celebrity.setConfidenceScore(BigDecimal.valueOf(0.8));
                celebrity.setVersion(1);

                // 根据谓词设置相应字段
                if (predicate.contains("出生")) {
                    celebrity.setBirthdate(value);
                } else if (predicate.contains("职业")) {
                    celebrity.setProfession(value);
                } else if (predicate.contains("国籍")) {
                    celebrity.setNationality(value);
                } else if (predicate.contains("配偶")) {
                    celebrity.setSpouse(value);
                } else if (predicate.contains("公司")) {
                    celebrity.setCompany(value);
                } else if (predicate.contains("学历")) {
                    celebrity.setEducation(value);
                }

                celebrityRepository.save(celebrity);
                log.info("✅ 成功创建新人员: {} (通过三元组提取)", name);
            }

        } catch (Exception e) {
            log.error("保存人员 {} 失败: {}", name, e.getMessage());
        }
    }

    /**
     * 保存单个作品信息 - 支持知识融合
     */
    private void saveSingleWork(String title, String predicate, String value) {
        try {
            // 查找已存在的实体
            Optional<Work> existingOpt = workRepository.findByTitle(title);

            if (existingOpt.isPresent()) {
                // 存在则进行知识融合
                Work existing = existingOpt.get();
                log.info("发现已存在作品: {}，进行知识融合", title);

                // 根据谓词更新或补充属性
                boolean updated = false;

                if (predicate.contains("类型") && existing.getWorkType() == null) {
                    existing.setWorkType(value);
                    updated = true;
                } else if (predicate.contains("发布") && existing.getReleaseDate() == null) {
                    existing.setReleaseDate(value);
                    updated = true;
                } else if (predicate.contains("平台") && existing.getPlatform() == null) {
                    existing.setPlatform(value);
                    updated = true;
                } else if (predicate.contains("奖项") && existing.getAwards() == null) {
                    existing.setAwards(value);
                    updated = true;
                } else if (predicate.contains("描述") && existing.getDescription() == null) {
                    existing.setDescription(value);
                    updated = true;
                }

                // 更新置信度（如果有）
                if (existing.getConfidenceScore() != null) {
                    existing.setConfidenceScore(
                            BigDecimal.valueOf((existing.getConfidenceScore().doubleValue() + 0.9) / 2));
                } else {
                    existing.setConfidenceScore(BigDecimal.valueOf(0.9));
                }

                if (updated) {
                    existing.setVersion(existing.getVersion() != null ? existing.getVersion() + 1 : 2);
                    workRepository.save(existing);
                    log.info("✅ 成功融合更新作品信息: {} (版本: {})", title, existing.getVersion());
                } else {
                    log.info("作品 {} 信息已完整，无需更新", title);
                }

            } else {
                // 不存在则创建新实体
                Work work = new Work();
                work.setWorkId(generateId());
                work.setTitle(title);
                work.setConfidenceScore(BigDecimal.valueOf(0.8));
                work.setVersion(1);

                // 根据谓词设置相应字段
                if (predicate.contains("类型")) {
                    work.setWorkType(value);
                } else if (predicate.contains("发布")) {
                    work.setReleaseDate(value);
                } else if (predicate.contains("平台")) {
                    work.setPlatform(value);
                } else if (predicate.contains("奖项")) {
                    work.setAwards(value);
                } else if (predicate.contains("描述")) {
                    work.setDescription(value);
                }

                workRepository.save(work);
                log.info("✅ 成功创建新作品: {} (通过三元组提取)", title);
            }

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
package com.datacenter.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 文本提取响应DTO
 * 标准JSON格式的响应数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractResponse {

    private Boolean success;
    private String message;
    private String error;
    private ExtractData data;
    private ExtractMetadata metadata;
    private Long timestamp;

    // 默认构造函数
    public ExtractResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    // 成功响应构造函数
    public ExtractResponse(Boolean success, ExtractData data, ExtractMetadata metadata) {
        this();
        this.success = success;
        this.data = data;
        this.metadata = metadata;
    }

    // 错误响应构造函数
    public ExtractResponse(Boolean success, String message, String error) {
        this();
        this.success = success;
        this.message = message;
        this.error = error;
    }

    // Getter和Setter方法
    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public ExtractData getData() {
        return data;
    }

    public void setData(ExtractData data) {
        this.data = data;
    }

    public ExtractMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ExtractMetadata metadata) {
        this.metadata = metadata;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 提取数据结构
     */
    public static class ExtractData {
        private Entities entities;
        private List<Relation> relations;

        public ExtractData() {
        }

        public ExtractData(Entities entities, List<Relation> relations) {
            this.entities = entities;
            this.relations = relations;
        }

        public Entities getEntities() {
            return entities;
        }

        public void setEntities(Entities entities) {
            this.entities = entities;
        }

        public List<Relation> getRelations() {
            return relations;
        }

        public void setRelations(List<Relation> relations) {
            this.relations = relations;
        }
    }

    /**
     * 实体集合
     */
    public static class Entities {
        private List<Person> persons;
        private List<Work> works;
        private List<Event> events;

        public Entities() {
        }

        public Entities(List<Person> persons, List<Work> works, List<Event> events) {
            this.persons = persons;
            this.works = works;
            this.events = events;
        }

        public List<Person> getPersons() {
            return persons;
        }

        public void setPersons(List<Person> persons) {
            this.persons = persons;
        }

        public List<Work> getWorks() {
            return works;
        }

        public void setWorks(List<Work> works) {
            this.works = works;
        }

        public List<Event> getEvents() {
            return events;
        }

        public void setEvents(List<Event> events) {
            this.events = events;
        }
    }

    /**
     * 人员信息
     */
    public static class Person {
        private String name;
        private String nationality;
        private String gender;
        private String profession;

        public Person() {
        }

        public Person(String name, String nationality, String gender, String profession) {
            this.name = name;
            this.nationality = nationality;
            this.gender = gender;
            this.profession = profession;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNationality() {
            return nationality;
        }

        public void setNationality(String nationality) {
            this.nationality = nationality;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getProfession() {
            return profession;
        }

        public void setProfession(String profession) {
            this.profession = profession;
        }
    }

    /**
     * 作品信息
     */
    public static class Work {
        private String title;

        @JsonProperty("work_type")
        private String workType;

        @JsonProperty("release_date")
        private String releaseDate;

        public Work() {
        }

        public Work(String title, String workType, String releaseDate) {
            this.title = title;
            this.workType = workType;
            this.releaseDate = releaseDate;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getWorkType() {
            return workType;
        }

        public void setWorkType(String workType) {
            this.workType = workType;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public void setReleaseDate(String releaseDate) {
            this.releaseDate = releaseDate;
        }
    }

    /**
     * 事件信息
     */
    public static class Event {
        @JsonProperty("event_name")
        private String eventName;

        @JsonProperty("event_type")
        private String eventType;

        private String time;

        public Event() {
        }

        public Event(String eventName, String eventType, String time) {
            this.eventName = eventName;
            this.eventType = eventType;
            this.time = time;
        }

        public String getEventName() {
            return eventName;
        }

        public void setEventName(String eventName) {
            this.eventName = eventName;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }
    }

    /**
     * 关系信息
     */
    public static class Relation {
        private String source;
        private String target;
        private String type;

        public Relation() {
        }

        public Relation(String source, String target, String type) {
            this.source = source;
            this.target = target;
            this.type = type;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * 元数据信息
     */
    public static class ExtractMetadata {
        @JsonProperty("processing_time")
        private String processingTime;

        private Double confidence;

        public ExtractMetadata() {
        }

        public ExtractMetadata(String processingTime, Double confidence) {
            this.processingTime = processingTime;
            this.confidence = confidence;
        }

        public String getProcessingTime() {
            return processingTime;
        }

        public void setProcessingTime(String processingTime) {
            this.processingTime = processingTime;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }
}
package com.datacenter.extract.model;

import java.util.List;
import java.util.ArrayList;

/**
 * 提取数据模型 - 极简设计
 */
public class ExtractionData {

    private List<Person> persons = new ArrayList<>();
    private List<Relation> relations = new ArrayList<>();
    private boolean success = true;
    private String message = "提取成功";

    // 人员信息
    public static class Person {
        public String name;
        public String gender;
        public String profession;

        public Person() {
        }

        public Person(String name, String gender, String profession) {
            this.name = name;
            this.gender = gender;
            this.profession = profession;
        }
    }

    // 关系信息
    public static class Relation {
        public String source;
        public String target;
        public String type;

        public Relation() {
        }

        public Relation(String source, String target, String type) {
            this.source = source;
            this.target = target;
            this.type = type;
        }
    }

    // Getter/Setter
    public List<Person> getPersons() {
        return persons;
    }

    public void setPersons(List<Person> persons) {
        this.persons = persons;
    }

    public List<Relation> getRelations() {
        return relations;
    }

    public void setRelations(List<Relation> relations) {
        this.relations = relations;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
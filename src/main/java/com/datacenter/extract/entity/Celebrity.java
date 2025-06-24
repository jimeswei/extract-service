package com.datacenter.extract.entity;

import jakarta.persistence.*;

/**
 * 名人实体 - 映射celebrity表
 * 按照base_data_graph.sql表结构设计
 */
@Entity
@Table(name = "celebrity")
public class Celebrity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "celebrity_id", nullable = false, length = 100)
    private String celebrityId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "nationality", length = 50)
    private String nationality;

    @Column(name = "birthdate", length = 50)
    private String birthdate;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "profession")
    private String profession;

    @Column(name = "spouse", length = 50)
    private String spouse;

    @Column(name = "relatives", columnDefinition = "TEXT")
    private String relatives;

    @Column(name = "company", columnDefinition = "TEXT")
    private String company;

    @Column(name = "position", columnDefinition = "TEXT")
    private String position;

    @Column(name = "education", columnDefinition = "TEXT")
    private String education;

    @Column(name = "resume", columnDefinition = "TEXT")
    private String resume;

    @Column(name = "baike", columnDefinition = "MEDIUMTEXT")
    private String baike;

    @Column(name = "`group`") // group是MySQL关键字，需要转义
    private String group;

    // 构造函数
    public Celebrity() {
    }

    // Getter和Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCelebrityId() {
        return celebrityId;
    }

    public void setCelebrityId(String celebrityId) {
        this.celebrityId = celebrityId;
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

    public String getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(String birthdate) {
        this.birthdate = birthdate;
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

    public String getSpouse() {
        return spouse;
    }

    public void setSpouse(String spouse) {
        this.spouse = spouse;
    }

    public String getRelatives() {
        return relatives;
    }

    public void setRelatives(String relatives) {
        this.relatives = relatives;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getResume() {
        return resume;
    }

    public void setResume(String resume) {
        this.resume = resume;
    }

    public String getBaike() {
        return baike;
    }

    public void setBaike(String baike) {
        this.baike = baike;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }
}
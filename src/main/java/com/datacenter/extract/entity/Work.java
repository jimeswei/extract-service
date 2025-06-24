package com.datacenter.extract.entity;

import jakarta.persistence.*;

/**
 * 作品实体 - 映射work表
 */
@Entity
@Table(name = "work")
public class Work {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false, length = 200)
    private String workId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "work_type", length = 200)
    private String workType;

    @Column(name = "release_date", length = 100)
    private String releaseDate;

    @Column(name = "role", columnDefinition = "TEXT")
    private String role;

    @Column(name = "platform", length = 100)
    private String platform;

    @Column(name = "awards", columnDefinition = "TEXT")
    private String awards;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "baike", columnDefinition = "MEDIUMTEXT")
    private String baike;

    @Column(name = "`group`")
    private String group;

    // 构造函数
    public Work() {
    }

    // Getter和Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWorkId() {
        return workId;
    }

    public void setWorkId(String workId) {
        this.workId = workId;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAwards() {
        return awards;
    }

    public void setAwards(String awards) {
        this.awards = awards;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
package com.datacenter.extract.entity;

import jakarta.persistence.*;

/**
 * 名人与名人关系实体 - 映射celebrity_celebrity表
 */
@Entity
@Table(name = "celebrity_celebrity")
public class CelebrityCelebrity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "`from`", nullable = false, length = 100)
    private String from;

    @Column(name = "`to`", nullable = false, length = 100)
    private String to;

    @Column(name = "e_type", length = 100)
    private String eType;

    // 构造函数
    public CelebrityCelebrity() {
    }

    public CelebrityCelebrity(String from, String to, String eType) {
        this.from = from;
        this.to = to;
        this.eType = eType;
    }

    // Getter和Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getEType() {
        return eType;
    }

    public void setEType(String eType) {
        this.eType = eType;
    }
}
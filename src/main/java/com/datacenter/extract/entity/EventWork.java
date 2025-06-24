package com.datacenter.extract.entity;

import jakarta.persistence.*;

/**
 * 事件与作品关系实体 - 映射event_work表
 */
@Entity
@Table(name = "event_work")
public class EventWork {

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
    public EventWork() {
    }

    public EventWork(String from, String to, String eType) {
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
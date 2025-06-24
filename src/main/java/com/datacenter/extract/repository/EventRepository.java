package com.datacenter.extract.repository;

import com.datacenter.extract.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Event数据访问层
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * 根据事件名称查找事件
     */
    Optional<Event> findByEventName(String eventName);

    /**
     * 检查事件是否存在
     */
    boolean existsByEventName(String eventName);
}
package com.datacenter.extract.repository;

import com.datacenter.extract.entity.EventWork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 事件与作品关系Repository
 */
@Repository
public interface EventWorkRepository extends JpaRepository<EventWork, Long> {

}
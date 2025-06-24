package com.datacenter.extract.repository;

import com.datacenter.extract.entity.CelebrityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 名人与事件关系Repository
 */
@Repository
public interface CelebrityEventRepository extends JpaRepository<CelebrityEvent, Long> {

}
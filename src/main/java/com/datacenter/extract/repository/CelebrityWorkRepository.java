package com.datacenter.extract.repository;

import com.datacenter.extract.entity.CelebrityWork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 名人与作品关系Repository
 */
@Repository
public interface CelebrityWorkRepository extends JpaRepository<CelebrityWork, Long> {

}
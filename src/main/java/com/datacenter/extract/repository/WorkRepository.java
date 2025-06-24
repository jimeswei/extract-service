package com.datacenter.extract.repository;

import com.datacenter.extract.entity.Work;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Work数据访问层
 */
@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

    /**
     * 根据标题查找作品
     */
    Optional<Work> findByTitle(String title);

    /**
     * 检查作品是否存在
     */
    boolean existsByTitle(String title);
}
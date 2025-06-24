package com.datacenter.extract.repository;

import com.datacenter.extract.entity.Celebrity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Celebrity数据访问层
 */
@Repository
public interface CelebrityRepository extends JpaRepository<Celebrity, Long> {

    /**
     * 根据名称查找名人
     */
    Optional<Celebrity> findByName(String name);

    /**
     * 检查名人是否存在
     */
    boolean existsByName(String name);
}
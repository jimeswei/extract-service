package com.datacenter.extract.repository;

import com.datacenter.extract.entity.CelebrityCelebrity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 名人与名人关系Repository
 */
@Repository
public interface CelebrityCelebrityRepository extends JpaRepository<CelebrityCelebrity, Long> {

}
package com.example.mallcs.mapper;

import com.example.mallcs.entity.MockApiConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MockApiConfigMapper {

    Optional<MockApiConfigEntity> findByOperation(@Param("operation") String operation);

    List<MockApiConfigEntity> findAll();

    boolean existsByOperation(@Param("operation") String operation);

    /** 存在则更新，不存在则插入（PostgreSQL upsert） */
    void upsert(MockApiConfigEntity entity);

    void deleteByOperation(@Param("operation") String operation);
}

package com.example.mallcs.mapper;

import com.example.mallcs.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<AppUser> findByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);

    void insert(AppUser user);
}

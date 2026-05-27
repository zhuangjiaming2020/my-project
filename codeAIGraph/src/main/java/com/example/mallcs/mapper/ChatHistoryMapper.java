package com.example.mallcs.mapper;

import com.example.mallcs.entity.ChatHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {

    void insert(ChatHistoryEntity entity);

    List<ChatHistoryEntity> findByUserIdAndSessionId(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId);

    List<ChatHistoryEntity> findLatestMessagePerSession(@Param("userId") String userId);
}

package com.example.mallcs.service;

import com.example.mallcs.entity.ChatHistoryEntity;
import com.example.mallcs.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 Advisor —— 在 Graph 执行前后自动记录消息到 PostgreSQL（MyBatis 实现）。
 *
 * <p>异步写库（{@code @Async}），不阻塞主响应流。
 */
@Component
public class ChatHistoryAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryAdvisor.class);

    private final ChatHistoryMapper chatHistoryMapper;

    public ChatHistoryAdvisor(ChatHistoryMapper chatHistoryMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
    }

    /** 记录用户消息（Graph 执行前调用） */
    @Async
    public void recordUserMessage(String userId, String sessionId, String message) {
        try {
            chatHistoryMapper.insert(ChatHistoryEntity.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .role("USER")
                    .content(message)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.debug("[ChatAdvisor] 记录用户消息: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception e) {
            log.error("[ChatAdvisor] 保存用户消息失败: {}", e.getMessage());
        }
    }

    /** 记录 AI 回复消息（Graph 执行完成后调用） */
    @Async
    public void recordAssistantMessage(String userId, String sessionId,
                                       String answer, String sceneType, String orderNo) {
        try {
            chatHistoryMapper.insert(ChatHistoryEntity.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .role("ASSISTANT")
                    .content(answer)
                    .sceneType(sceneType)
                    .orderNo(orderNo)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.debug("[ChatAdvisor] 记录AI回复: userId={}, sessionId={}, scene={}", userId, sessionId, sceneType);
        } catch (Exception e) {
            log.error("[ChatAdvisor] 保存AI回复失败: {}", e.getMessage());
        }
    }

    /** 查询某用户某会话的历史消息 */
    public List<ChatHistoryEntity> getHistory(String userId, String sessionId) {
        return chatHistoryMapper.findByUserIdAndSessionId(userId, sessionId);
    }

    /** 查询某用户每个会话的最后一条消息（会话列表摘要） */
    public List<ChatHistoryEntity> getSessionSummaries(String userId) {
        return chatHistoryMapper.findLatestMessagePerSession(userId);
    }
}

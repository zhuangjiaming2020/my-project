package com.example.mallcs.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 对话历史记录。每条消息（用户提问 / AI 回复）独立存储，
 * 通过 userId + sessionId 关联同一轮对话。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryEntity {

    private Long id;
    private String userId;
    private String sessionId;
    /** USER 或 ASSISTANT */
    private String role;
    private String content;
    /** AI 识别的场景类型，仅 ASSISTANT 消息有值 */
    private String sceneType;
    /** 关联订单号，仅 ASSISTANT 消息有值 */
    private String orderNo;
    private LocalDateTime createdAt;
}

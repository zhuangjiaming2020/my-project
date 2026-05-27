package com.example.mallcs.entity;

import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock 接口配置持久化实体。headers 字段以 JSON 字符串存储，
 * 通过 {@code JsonMapTypeHandler} 自动转换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockApiConfigEntity {

    private Long id;
    private String operation;
    @Builder.Default
    private boolean enabled = false;
    private String description;
    private String url;
    @Builder.Default
    private String method = "GET";
    /** 以 JSON 字符串存储，如 {"Authorization":"Bearer xxx"} */
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();
    private String requestBody;
    private LocalDateTime updatedAt;
}

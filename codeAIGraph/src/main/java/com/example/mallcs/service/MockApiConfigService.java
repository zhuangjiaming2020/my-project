package com.example.mallcs.service;

import com.example.mallcs.domain.ApiConfig;
import com.example.mallcs.entity.MockApiConfigEntity;
import com.example.mallcs.mapper.MockApiConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Mock 数据接口配置管理服务（MyBatis + PostgreSQL 持久化版本）。
 */
@Service
public class MockApiConfigService {

    public static final String OP_ORDER_STATUS   = "order_status";
    public static final String OP_LOGISTICS_INFO = "logistics_info";
    public static final String OP_SUBMIT_REVIEW  = "submit_review";

    public static final List<String> ALL_OPERATIONS = List.of(
            OP_ORDER_STATUS, OP_LOGISTICS_INFO, OP_SUBMIT_REVIEW
    );

    private final MockApiConfigMapper mapper;

    public MockApiConfigService(MockApiConfigMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<ApiConfig> getConfig(String operation) {
        return mapper.findByOperation(operation).map(this::toApiConfig);
    }

    public Collection<ApiConfig> getAllConfigs() {
        return mapper.findAll().stream()
                .sorted(Comparator.comparingInt(e -> ALL_OPERATIONS.indexOf(e.getOperation())))
                .map(this::toApiConfig)
                .toList();
    }

    @Transactional
    public ApiConfig saveConfig(String operation, ApiConfig config) {
        MockApiConfigEntity entity = MockApiConfigEntity.builder()
                .operation(operation)
                .enabled(config.isEnabled())
                .description(config.getDescription())
                .url(config.getUrl())
                .method(config.getMethod() != null ? config.getMethod() : "GET")
                .headers(config.getHeaders() != null ? config.getHeaders() : new LinkedHashMap<>())
                .requestBody(config.getRequestBody())
                .build();
        mapper.upsert(entity);
        return toApiConfig(entity);
    }

    @Transactional
    public void deleteConfig(String operation) {
        mapper.deleteByOperation(operation);
    }

    public boolean isEnabled(String operation) {
        return mapper.findByOperation(operation)
                .map(MockApiConfigEntity::isEnabled)
                .orElse(false);
    }

    // ── 转换工具 ─────────────────────────────────────────────────

    private ApiConfig toApiConfig(MockApiConfigEntity e) {
        return ApiConfig.builder()
                .operation(e.getOperation())
                .enabled(e.isEnabled())
                .description(e.getDescription())
                .url(e.getUrl())
                .method(e.getMethod())
                .headers(e.getHeaders())
                .requestBody(e.getRequestBody())
                .build();
    }
}

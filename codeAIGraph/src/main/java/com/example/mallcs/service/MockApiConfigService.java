package com.example.mallcs.service;

import com.example.mallcs.domain.ApiConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 数据接口配置管理服务（内存存储）。
 *
 * <p>存储三个操作的外部 API 配置信息。管理员通过后台 REST 接口读写这些配置，
 * {@link ConfigurableMallDataService} 在执行查询时读取配置决定是否调用外部 API。
 *
 * <p>生产环境可替换为数据库持久化实现。
 */
@Service
public class MockApiConfigService {

    public static final String OP_ORDER_STATUS   = "order_status";
    public static final String OP_LOGISTICS_INFO = "logistics_info";
    public static final String OP_SUBMIT_REVIEW  = "submit_review";

    public static final List<String> ALL_OPERATIONS = List.of(
            OP_ORDER_STATUS, OP_LOGISTICS_INFO, OP_SUBMIT_REVIEW
    );

    private final Map<String, ApiConfig> store = new ConcurrentHashMap<>();

    /** 初始化默认配置（disabled，仅作示例） */
    public MockApiConfigService() {
        store.put(OP_ORDER_STATUS, ApiConfig.builder()
                .operation(OP_ORDER_STATUS)
                .enabled(false)
                .description("查询订单状态接口")
                .url("http://your-mall-api.com/orders/{orderId}/status")
                .method("GET")
                .build());

        store.put(OP_LOGISTICS_INFO, ApiConfig.builder()
                .operation(OP_LOGISTICS_INFO)
                .enabled(false)
                .description("查询物流信息接口")
                .url("http://your-mall-api.com/orders/{orderId}/logistics")
                .method("GET")
                .build());

        store.put(OP_SUBMIT_REVIEW, ApiConfig.builder()
                .operation(OP_SUBMIT_REVIEW)
                .enabled(false)
                .description("提交订单评价接口")
                .url("http://your-mall-api.com/orders/review")
                .method("POST")
                .requestBody("{\"orderId\": \"{{orderId}}\", \"rating\": {{rating}}, \"content\": \"{{content}}\"}")
                .build());
    }

    public Optional<ApiConfig> getConfig(String operation) {
        return Optional.ofNullable(store.get(operation));
    }

    public Collection<ApiConfig> getAllConfigs() {
        List<ApiConfig> result = new ArrayList<>();
        for (String op : ALL_OPERATIONS) {
            ApiConfig cfg = store.get(op);
            if (cfg != null) result.add(cfg);
        }
        return result;
    }

    public ApiConfig saveConfig(String operation, ApiConfig config) {
        config.setOperation(operation);
        store.put(operation, config);
        return config;
    }

    public void deleteConfig(String operation) {
        store.remove(operation);
    }

    public boolean isEnabled(String operation) {
        return getConfig(operation).map(ApiConfig::isEnabled).orElse(false);
    }
}

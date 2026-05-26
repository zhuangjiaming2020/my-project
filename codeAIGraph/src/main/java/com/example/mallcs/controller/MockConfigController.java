package com.example.mallcs.controller;

import com.example.mallcs.domain.ApiConfig;
import com.example.mallcs.service.ConfigurableMallDataService;
import com.example.mallcs.service.MockApiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Mock 数据接口配置管理控制器。
 *
 * <p>管理员通过此接口配置真实商城系统的 API 地址，替换内置 Mock 数据。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET  /api/mock-config              - 获取所有配置</li>
 *   <li>GET  /api/mock-config/{operation}  - 获取指定操作配置</li>
 *   <li>POST /api/mock-config/{operation}  - 保存/更新配置</li>
 *   <li>DELETE /api/mock-config/{operation} - 重置为默认配置</li>
 *   <li>POST /api/mock-config/{operation}/test - 测试外部 API</li>
 * </ul>
 *
 * <h3>operation 取值</h3>
 * <ul>
 *   <li>order_status   - 查询订单状态</li>
 *   <li>logistics_info - 查询物流信息</li>
 *   <li>submit_review  - 提交订单评价</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/mock-config")
public class MockConfigController {

    private static final Logger log = LoggerFactory.getLogger(MockConfigController.class);

    private final MockApiConfigService configService;
    private final ConfigurableMallDataService dataService;

    public MockConfigController(MockApiConfigService configService,
                                ConfigurableMallDataService dataService) {
        this.configService = configService;
        this.dataService = dataService;
    }

    // ------------------------------------------------------------------
    // 获取所有配置
    // ------------------------------------------------------------------

    @GetMapping
    public Collection<ApiConfig> getAllConfigs() {
        return configService.getAllConfigs();
    }

    // ------------------------------------------------------------------
    // 获取单个配置
    // ------------------------------------------------------------------

    @GetMapping("/{operation}")
    public ResponseEntity<ApiConfig> getConfig(@PathVariable String operation) {
        return configService.getConfig(operation)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------
    // 保存/更新配置
    // ------------------------------------------------------------------

    @PostMapping("/{operation}")
    public ResponseEntity<?> saveConfig(@PathVariable String operation,
                                        @RequestBody ApiConfig config) {
        if (!MockApiConfigService.ALL_OPERATIONS.contains(operation)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "未知操作: " + operation +
                            "，有效值: " + MockApiConfigService.ALL_OPERATIONS));
        }
        ApiConfig saved = configService.saveConfig(operation, config);
        log.info("[MockConfig] 配置已保存: operation={}, enabled={}, url={}",
                operation, saved.isEnabled(), saved.getUrl());
        return ResponseEntity.ok(saved);
    }

    // ------------------------------------------------------------------
    // 重置配置（删除自定义配置，恢复默认占位配置）
    // ------------------------------------------------------------------

    @DeleteMapping("/{operation}")
    public ResponseEntity<Void> resetConfig(@PathVariable String operation) {
        configService.deleteConfig(operation);
        log.info("[MockConfig] 配置已重置: operation={}", operation);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // 测试外部 API
    // ------------------------------------------------------------------

    /**
     * 使用当前保存的配置发起一次真实 HTTP 请求，验证外部 API 是否可用。
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "orderId": "ORD001",
     *   "rating": 5,
     *   "content": "非常好"
     * }
     * </pre>
     */
    @PostMapping("/{operation}/test")
    public ResponseEntity<?> testConfig(@PathVariable String operation,
                                        @RequestBody TestRequest request) {
        ApiConfig config = configService.getConfig(operation).orElse(null);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "未找到配置: " + operation));
        }
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL 不能为空"));
        }

        log.info("[MockConfig] 开始测试: operation={}, url={}", operation, config.getUrl());

        Map<String, String> params = buildParams(operation, request);
        long start = System.currentTimeMillis();
        String responseBody = dataService.callExternalApi(config, params);
        long elapsed = System.currentTimeMillis() - start;

        if (responseBody != null) {
            log.info("[MockConfig] 测试成功: elapsed={}ms", elapsed);
            return ResponseEntity.ok(new TestResult(true, responseBody, null, elapsed));
        } else {
            log.warn("[MockConfig] 测试失败");
            return ResponseEntity.ok(new TestResult(false, null, "请求失败，请检查URL和网络连通性", elapsed));
        }
    }

    // ------------------------------------------------------------------
    // 获取操作元信息（前端下拉/说明用）
    // ------------------------------------------------------------------

    @GetMapping("/operations")
    public List<OperationMeta> getOperations() {
        return List.of(
                new OperationMeta(MockApiConfigService.OP_ORDER_STATUS, "查询订单状态",
                        "根据订单号查询订单当前状态信息",
                        List.of("orderId")),
                new OperationMeta(MockApiConfigService.OP_LOGISTICS_INFO, "查询物流信息",
                        "根据订单号查询快递物流轨迹",
                        List.of("orderId")),
                new OperationMeta(MockApiConfigService.OP_SUBMIT_REVIEW, "提交订单评价",
                        "提交已完成订单的评分和评价内容",
                        List.of("orderId", "rating", "content"))
        );
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private Map<String, String> buildParams(String operation, TestRequest request) {
        if (MockApiConfigService.OP_SUBMIT_REVIEW.equals(operation)) {
            return Map.of(
                    "orderId", nvl(request.orderId(), "ORD001"),
                    "rating",  String.valueOf(request.rating() != null ? request.rating() : 5),
                    "content", nvl(request.content(), "测试评价内容")
            );
        }
        return Map.of("orderId", nvl(request.orderId(), "ORD001"));
    }

    private String nvl(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    // ------------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------------

    public record TestRequest(String orderId, Integer rating, String content) {}

    public record TestResult(boolean success, String responseBody, String errorMessage, long elapsedMs) {}

    public record OperationMeta(String operation, String label, String description, List<String> params) {}
}

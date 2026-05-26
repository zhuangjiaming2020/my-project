package com.example.mallcs.service;

import com.example.mallcs.domain.ApiConfig;
import com.example.mallcs.mock.MockMallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 可配置数据服务 —— {@link MallDataService} 的主实现（@Primary）。
 *
 * <p>执行策略：
 * <ol>
 *   <li>检查 {@link MockApiConfigService} 中对应操作是否配置并启用</li>
 *   <li>若启用：调用管理员配置的外部 API，返回原始响应体</li>
 *   <li>若未启用或调用失败：降级到 {@link MockMallDataService} 内置 Mock 数据</li>
 * </ol>
 *
 * <p>上层 Graph 节点无需感知数据来源，只需注入 {@link MallDataService} 即可。
 */
@Service
@Primary
public class ConfigurableMallDataService implements MallDataService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableMallDataService.class);

    private final MockMallDataService mockService;
    private final MockApiConfigService configService;
    private final RestTemplate restTemplate;

    public ConfigurableMallDataService(MockMallDataService mockService,
                                       MockApiConfigService configService) {
        this.mockService = mockService;
        this.configService = configService;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String queryOrderStatus(String orderId) {
        if (configService.isEnabled(MockApiConfigService.OP_ORDER_STATUS)) {
            ApiConfig cfg = configService.getConfig(MockApiConfigService.OP_ORDER_STATUS).get();
            log.info("[Configurable] 使用外部API查询订单状态: {}", orderId);
            String result = callExternalApi(cfg, Map.of("orderId", orderId));
            if (result != null) return result;
            log.warn("[Configurable] 外部API失败，降级到Mock数据");
        }
        return mockService.queryOrderStatus(orderId);
    }

    @Override
    public String queryLogisticsInfo(String orderId) {
        if (configService.isEnabled(MockApiConfigService.OP_LOGISTICS_INFO)) {
            ApiConfig cfg = configService.getConfig(MockApiConfigService.OP_LOGISTICS_INFO).get();
            log.info("[Configurable] 使用外部API查询物流信息: {}", orderId);
            String result = callExternalApi(cfg, Map.of("orderId", orderId));
            if (result != null) return result;
            log.warn("[Configurable] 外部API失败，降级到Mock数据");
        }
        return mockService.queryLogisticsInfo(orderId);
    }

    @Override
    public String submitOrderReview(String orderId, int rating, String content) {
        if (configService.isEnabled(MockApiConfigService.OP_SUBMIT_REVIEW)) {
            ApiConfig cfg = configService.getConfig(MockApiConfigService.OP_SUBMIT_REVIEW).get();
            log.info("[Configurable] 使用外部API提交评价: orderId={}, rating={}", orderId, rating);
            String result = callExternalApi(cfg, Map.of(
                    "orderId", orderId,
                    "rating", String.valueOf(rating),
                    "content", content
            ));
            if (result != null) return result;
            log.warn("[Configurable] 外部API失败，降级到Mock数据");
        }
        return mockService.submitOrderReview(orderId, rating, content);
    }

    /**
     * 调用外部 API。
     *
     * @param config 接口配置
     * @param params 参数 Map（orderId / rating / content）
     * @return 响应体字符串，失败时返回 null（触发降级）
     */
    public String callExternalApi(ApiConfig config, Map<String, String> params) {
        try {
            // 替换 URL 中的路径参数：{orderId} → 实际值
            String url = config.getUrl();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                url = url.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            if ("POST".equalsIgnoreCase(config.getMethod())) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            if (config.getHeaders() != null) {
                config.getHeaders().forEach(headers::set);
            }

            // 构建请求体（替换 {{placeholder}} 占位符）
            String bodyStr = null;
            if ("POST".equalsIgnoreCase(config.getMethod()) && config.getRequestBody() != null) {
                bodyStr = config.getRequestBody();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    bodyStr = bodyStr.replace("{{" + entry.getKey() + "}}", entry.getValue());
                }
            }

            HttpEntity<String> entity = new HttpEntity<>(bodyStr, headers);
            HttpMethod method = HttpMethod.valueOf(config.getMethod().toUpperCase());

            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            log.info("[Configurable] 外部API响应状态: {}", response.getStatusCode());
            return response.getBody();

        } catch (Exception e) {
            log.error("[Configurable] 调用外部API异常: {}", e.getMessage());
            return null;
        }
    }
}

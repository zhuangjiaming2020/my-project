package com.example.mallcs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可配置 Mock 数据接口的配置实体。
 *
 * <p>管理员通过后台配置此对象，指定外部商城系统的真实 API 地址。
 * 配置生效后，Graph 节点将调用外部 API 而非内置 Mock 数据，
 * 从而实现运维客服适配不同商城系统。
 *
 * <h3>URL 参数占位符规则</h3>
 * <ul>
 *   <li>URL 中使用 {@code {orderId}}、{@code {rating}}、{@code {content}} 作为路径参数</li>
 *   <li>POST 请求体中使用 {@code {{orderId}}}、{@code {{rating}}}、{@code {{content}}} 作为模板变量</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiConfig {

    /** 操作标识：order_status / logistics_info / submit_review */
    private String operation;

    /** 是否启用此配置（false 则降级到内置 Mock 数据） */
    private boolean enabled;

    /** 接口名称描述 */
    private String description;

    /** 请求 URL，支持 {orderId} 等路径参数占位符 */
    private String url;

    /** HTTP 方法：GET 或 POST */
    @Builder.Default
    private String method = "GET";

    /** 自定义请求头，键值对 */
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * POST 请求体模板（JSON 字符串）。
     * 支持 {{orderId}}、{{rating}}、{{content}} 占位符，发送前自动替换。
     */
    private String requestBody;
}

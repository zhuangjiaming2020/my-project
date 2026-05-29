package com.example.mallcs.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * 可追踪的 RestTemplate 配置。
 *
 * <p>注册一个带有 B3 传播拦截器的 {@link RestTemplate} Bean，
 * 使服务间调用自动携带以下链路追踪请求头：
 * <ul>
 *   <li>{@code X-B3-TraceId}  —— 当前请求的 TraceId，下游服务可加入同一链路</li>
 *   <li>{@code X-B3-SpanId}   —— 当前 SpanId（下游作为父 SpanId 使用）</li>
 *   <li>{@code X-B3-Sampled}  —— 采样标志，透传给下游</li>
 * </ul>
 *
 * <p>{@link com.example.mallcs.service.ConfigurableMallDataService} 注入此 Bean，
 * 调用外部商城 API 时自动完成链路传播，无需手动添加请求头。
 */
@Configuration
public class TracingRestTemplateConfig {

    private final Tracer tracer;

    public TracingRestTemplateConfig(Tracer tracer) {
        this.tracer = tracer;
    }

    @Bean
    public RestTemplate tracingRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(b3PropagationInterceptor());
        return restTemplate;
    }

    /**
     * B3 传播拦截器：从当前 Span 读取 TraceId/SpanId 并注入到出站请求头。
     */
    private ClientHttpRequestInterceptor b3PropagationInterceptor() {
        return (request, body, execution) -> {
            String traceId = null;
            String spanId  = null;

            // 优先从 Micrometer Tracer 当前 Span 读取
            Span span = tracer.currentSpan();
            if (span != null) {
                TraceContext ctx = span.context();
                traceId = ctx.traceId();
                spanId  = ctx.spanId();
            }

            // 降级：从 MDC 读取（Micrometer Tracing 自动写入）
            if (traceId == null || traceId.isBlank()) {
                traceId = MDC.get("traceId");
                spanId  = MDC.get("spanId");
            }

            if (traceId != null && !traceId.isBlank()) {
                request.getHeaders().set("X-B3-TraceId", traceId);
                request.getHeaders().set("X-B3-Sampled", "1");
            }
            if (spanId != null && !spanId.isBlank()) {
                // 下游将本 SpanId 作为父 SpanId，形成完整调用链
                request.getHeaders().set("X-B3-ParentSpanId", spanId);
            }

            return execution.execute(request, body);
        };
    }
}

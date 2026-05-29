package com.example.mallcs.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路追踪响应过滤器。
 *
 * <p>在每一个 HTTP 响应头中自动注入以下字段：
 * <ul>
 *   <li>{@code X-B3-TraceId}  —— 完整 TraceId（B3 格式，与 Spring Cloud Sleuth 兼容）</li>
 *   <li>{@code X-Trace-Id}    —— 同上，别名，方便前端统一读取</li>
 *   <li>{@code X-B3-SpanId}   —— 当前 SpanId</li>
 *   <li>{@code X-B3-Sampled}  —— 采样标志（1 = 已采样）</li>
 * </ul>
 *
 * <p><b>过滤器执行顺序设计：</b>
 * <ol>
 *   <li>Spring Boot 的 {@code ServerHttpObservationFilter}（order = HIGHEST_PRECEDENCE+1）
 *       最先执行，负责创建 HTTP 请求 Span 并将 traceId/spanId 写入 MDC。</li>
 *   <li>本过滤器（order = HIGHEST_PRECEDENCE+10）紧随其后，此时 Span 已就绪，
 *       可安全读取并写入响应头。</li>
 *   <li>Spring Security 过滤器链（order = -100）在本过滤器之后执行。</li>
 * </ol>
 *
 * <p><b>SSE 兼容：</b>头必须在响应体写入之前设置。本过滤器在调用
 * {@code filterChain.doFilter()} <em>之前</em>写头，保证 SSE 流也能携带 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdResponseFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceIdResponseFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 在响应体写出前设置追踪头（对 SSE 流式响应尤为关键）
        injectTraceHeaders(response);
        filterChain.doFilter(request, response);
    }

    /**
     * 将当前 Span 的 traceId / spanId 注入响应头。
     *
     * <p>优先从 Micrometer {@link Tracer} 当前 Span 读取；
     * 若 Span 尚未就绪（极少数边界情况），降级到 MDC 读取。
     */
    private void injectTraceHeaders(HttpServletResponse response) {
        if (response.isCommitted()) {
            return;
        }

        String traceId = null;
        String spanId  = null;

        // 1. 优先路径：Micrometer Tracer 当前活跃 Span（最准确）
        Span span = tracer.currentSpan();
        if (span != null) {
            traceId = span.context().traceId();
            spanId  = span.context().spanId();
        }

        // 2. 降级路径：MDC（Micrometer Tracing 自动写入，适用于 Span 未直接暴露的场景）
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
            spanId  = MDC.get("spanId");
        }

        if (traceId != null && !traceId.isBlank()) {
            response.setHeader("X-B3-TraceId", traceId);
            response.setHeader("X-Trace-Id",   traceId);
            response.setHeader("X-B3-Sampled", "1");
        }
        if (spanId != null && !spanId.isBlank()) {
            response.setHeader("X-B3-SpanId", spanId);
        }
    }
}

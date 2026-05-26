package com.example.mallcs.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 商城客服对话接口。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>POST /api/chat         - 同步对话（阻塞，等待最终答案）</li>
 *   <li>POST /api/chat/stream  - 流式对话（SSE，逐节点输出进度）</li>
 *   <li>GET  /api/chat/demo    - 演示接口（无需请求体）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class CustomerServiceController {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceController.class);

    private final CompiledGraph mallCustomerServiceGraph;

    public CustomerServiceController(CompiledGraph mallCustomerServiceGraph) {
        this.mallCustomerServiceGraph = mallCustomerServiceGraph;
    }

    // ------------------------------------------------------------------
    // 请求/响应 DTO
    // ------------------------------------------------------------------

    public record ChatRequest(
            /** 用户消息内容 */
            String message,
            /**
             * 会话 ID（可选）。
             * 传入相同 sessionId 可在同一会话中保持上下文（多轮对话）。
             * 不传则每次生成新会话。
             */
            String sessionId
    ) {}

    public record ChatResponse(
            String sessionId,
            String answer,
            String sceneType,
            String orderNo,
            boolean success,
            String errorMessage
    ) {}

    // ------------------------------------------------------------------
    // 同步对话接口
    // ------------------------------------------------------------------

    /**
     * 同步对话 —— 等待 Graph 执行完毕后返回最终答案。
     *
     * <p>示例请求：
     * <pre>
     * POST /api/chat
     * {"message": "帮我查一下 ORD002 的快递到哪了", "sessionId": "user_001"}
     * </pre>
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId()
                : UUID.randomUUID().toString();

        log.info("[Controller] 收到对话请求: sessionId={}, message={}", sessionId, request.message());

        try {
            Map<String, Object> initialState = buildInitialState(request.message());
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // 执行 Graph 并收集最终状态
            AtomicReference<String> finalAnswer    = new AtomicReference<>("抱歉，我暂时无法处理您的请求，请稍后重试。");
            AtomicReference<String> sceneType      = new AtomicReference<>("unknown");
            AtomicReference<String> orderNo        = new AtomicReference<>("");

            mallCustomerServiceGraph.stream(initialState, config)
                    .doOnNext(output -> {
                        log.debug("[Controller] 节点完成: {}", output.node());
                        // 从每个节点的输出中更新状态快照
                        extractStateValues(output, finalAnswer, sceneType, orderNo);
                    })
                    .blockLast();

            log.info("[Controller] Graph 执行完毕: scene={}, answer.length={}",
                    sceneType.get(), finalAnswer.get().length());

            return new ChatResponse(sessionId, finalAnswer.get(), sceneType.get(),
                    orderNo.get(), true, null);

        } catch (Exception e) {
            log.error("[Controller] Graph 执行异常: {}", e.getMessage(), e);
            return new ChatResponse(sessionId, "系统繁忙，请稍后再试。如需帮助请拨打客服热线：400-123-4567",
                    "error", "", false, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 流式对话接口（SSE）
    // ------------------------------------------------------------------

    /**
     * 流式对话 —— 通过 SSE 实时推送每个 Graph 节点的执行进度。
     *
     * <p>客户端可订阅此接口获得实时反馈，适合展示"正在查询物流..."等中间状态。
     *
     * <p>示例请求：
     * <pre>
     * POST /api/chat/stream
     * {"message": "ORD001 的订单状态怎么样"}
     * </pre>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId()
                : UUID.randomUUID().toString();

        log.info("[Controller-Stream] 收到流式对话请求: sessionId={}", sessionId);

        Map<String, Object> initialState = buildInitialState(request.message());
        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        return mallCustomerServiceGraph.stream(initialState, config)
                .map(output -> formatNodeOutput(output))
                .onErrorReturn("[ERROR] 处理出错，请稍后再试或拨打客服热线：400-123-4567");
    }

    // ------------------------------------------------------------------
    // 演示接口（快速验证）
    // ------------------------------------------------------------------

    /**
     * 演示接口 —— 使用预置 message 快速体验，无需传参。
     *
     * <p>示例：GET /api/chat/demo?message=查询ORD002物流
     */
    @GetMapping("/demo")
    public ChatResponse demo(
            @RequestParam(defaultValue = "帮我查一下订单 ORD002 的快递到哪了") String message) {
        return chat(new ChatRequest(message, "demo-session-" + UUID.randomUUID().toString().substring(0, 8)));
    }

    // ------------------------------------------------------------------
    // 内部工具方法
    // ------------------------------------------------------------------

    private Map<String, Object> buildInitialState(String userMessage) {
        Map<String, Object> state = new HashMap<>();
        state.put("user_input", userMessage);
        state.put("messages", new ArrayList<String>());
        return state;
    }

    private void extractStateValues(NodeOutput output,
                                    AtomicReference<String> finalAnswer,
                                    AtomicReference<String> sceneType,
                                    AtomicReference<String> orderNo) {
        if (output == null || output.state() == null) return;

        output.state().value("final_answer").ifPresent(v -> {
            if (v != null && !v.toString().isBlank()) {
                finalAnswer.set(v.toString());
            }
        });
        output.state().value("scene_type").ifPresent(v -> sceneType.set(v.toString()));
        output.state().value("order_no").ifPresent(v -> orderNo.set(v.toString()));
    }

    private String formatNodeOutput(NodeOutput output) {
        if (output == null) return "";
        String nodeName = output.node();

        // 根据节点名称返回进度描述
        String progress = switch (nodeName) {
            case "intent_parser"      -> "🔍 正在分析您的问题...";
            case "logistics_query_node" -> "📦 正在查询物流信息...";
            case "order_status_node"  -> "📋 正在查询订单状态...";
            case "order_review_node"  -> "⭐ 正在提交评价...";
            case "general_answer_node"-> "💬 正在生成回答...";
            case "response_formatter" -> "✍️ 正在整理回复...";
            default -> "⚙️ 处理中...";
        };

        // 若是最终节点，附上答案
        if (output.state() != null) {
            Object answer = output.state().value("final_answer").orElse(null);
            if (answer != null && !answer.toString().isBlank()) {
                return "data: " + progress + "\n\ndata: [ANSWER]\n" + answer + "\n\n";
            }
        }
        return "data: " + progress + "\n\n";
    }
}

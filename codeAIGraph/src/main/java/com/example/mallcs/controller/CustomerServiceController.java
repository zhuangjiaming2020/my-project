package com.example.mallcs.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.example.mallcs.service.ChatHistoryAdvisor;
import jakarta.servlet.http.HttpServletRequest;
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
 * <p>新增功能（v2）：
 * <ul>
 *   <li>从 JWT 中提取 userId，与 sessionId 组合存储对话历史</li>
 *   <li>通过 {@link ChatHistoryAdvisor} 异步持久化每轮对话到 PostgreSQL</li>
 *   <li>GET /api/chat/history/{sessionId}  - 查询指定会话历史</li>
 *   <li>GET /api/chat/sessions             - 查询当前用户所有会话摘要</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class CustomerServiceController {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceController.class);

    private final CompiledGraph mallCustomerServiceGraph;
    private final ChatHistoryAdvisor chatHistoryAdvisor;

    public CustomerServiceController(CompiledGraph mallCustomerServiceGraph,
                                     ChatHistoryAdvisor chatHistoryAdvisor) {
        this.mallCustomerServiceGraph = mallCustomerServiceGraph;
        this.chatHistoryAdvisor       = chatHistoryAdvisor;
    }

    // ------------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------------

    public record ChatRequest(String message, String sessionId) {}

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

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        String userId    = resolveUserId(httpRequest);
        String sessionId = resolveSessionId(request.sessionId());

        log.info("[Controller] 收到对话: userId={}, sessionId={}, message={}", userId, sessionId, request.message());

        // Advisor 前置：异步记录用户消息
        chatHistoryAdvisor.recordUserMessage(userId, sessionId, request.message());

        try {
            Map<String, Object> initialState = buildInitialState(request.message());
            RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();

            AtomicReference<String> finalAnswer = new AtomicReference<>("抱歉，我暂时无法处理您的请求，请稍后重试。");
            AtomicReference<String> sceneType   = new AtomicReference<>("unknown");
            AtomicReference<String> orderNo     = new AtomicReference<>("");

            mallCustomerServiceGraph.stream(initialState, config)
                    .doOnNext(output -> extractStateValues(output, finalAnswer, sceneType, orderNo))
                    .blockLast();

            // Advisor 后置：异步记录 AI 回复
            chatHistoryAdvisor.recordAssistantMessage(userId, sessionId,
                    finalAnswer.get(), sceneType.get(), orderNo.get());

            log.info("[Controller] Graph 完成: scene={}", sceneType.get());
            return new ChatResponse(sessionId, finalAnswer.get(), sceneType.get(), orderNo.get(), true, null);

        } catch (Exception e) {
            log.error("[Controller] Graph 异常: {}", e.getMessage(), e);
            String errMsg = "系统繁忙，请稍后再试。如需帮助请拨打客服热线：400-123-4567";
            chatHistoryAdvisor.recordAssistantMessage(userId, sessionId, errMsg, "error", "");
            return new ChatResponse(sessionId, errMsg, "error", "", false, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 流式对话接口（SSE）
    // ------------------------------------------------------------------

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        String userId    = resolveUserId(httpRequest);
        String sessionId = resolveSessionId(request.sessionId());

        log.info("[Controller-Stream] 收到流式对话: userId={}, sessionId={}", userId, sessionId);

        // 前置：记录用户消息
        chatHistoryAdvisor.recordUserMessage(userId, sessionId, request.message());

        Map<String, Object> initialState = buildInitialState(request.message());
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();

        AtomicReference<String> collectedAnswer = new AtomicReference<>("");
        AtomicReference<String> collectedScene  = new AtomicReference<>("");

        return mallCustomerServiceGraph.stream(initialState, config)
                .map(output -> {
                    if (output.state() != null) {
                        output.state().value("final_answer").ifPresent(v -> collectedAnswer.set(v.toString()));
                        output.state().value("scene_type").ifPresent(v -> collectedScene.set(v.toString()));
                    }
                    return formatNodeOutput(output);
                })
                .doOnComplete(() ->
                    chatHistoryAdvisor.recordAssistantMessage(
                            userId, sessionId, collectedAnswer.get(), collectedScene.get(), ""))
                .onErrorReturn("[ERROR] 处理出错，请稍后再试或拨打客服热线：400-123-4567");
    }

    // ------------------------------------------------------------------
    // 查询对话历史
    // ------------------------------------------------------------------

    @GetMapping("/history/{sessionId}")
    public Object getHistory(@PathVariable String sessionId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        return chatHistoryAdvisor.getHistory(userId, sessionId);
    }

    @GetMapping("/sessions")
    public Object getSessions(HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        return chatHistoryAdvisor.getSessionSummaries(userId);
    }

    // ------------------------------------------------------------------
    // 演示接口
    // ------------------------------------------------------------------

    @GetMapping("/demo")
    public ChatResponse demo(
            @RequestParam(defaultValue = "帮我查一下订单 ORD002 的快递到哪了") String message,
            HttpServletRequest httpRequest) {
        return chat(new ChatRequest(message, "demo-session-" + UUID.randomUUID().toString().substring(0, 8)),
                httpRequest);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private String resolveUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return (userId != null && !userId.isBlank()) ? userId : "anonymous";
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();
    }

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
            if (v != null && !v.toString().isBlank()) finalAnswer.set(v.toString());
        });
        output.state().value("scene_type").ifPresent(v -> sceneType.set(v.toString()));
        output.state().value("order_no").ifPresent(v -> orderNo.set(v.toString()));
    }

    private String formatNodeOutput(NodeOutput output) {
        if (output == null) return "";
        String progress = switch (output.node()) {
            case "intent_parser"       -> "data: 🔍 正在分析您的问题...\n\n";
            case "logistics_query_node"-> "data: 📦 正在查询物流信息...\n\n";
            case "order_status_node"   -> "data: 📋 正在查询订单状态...\n\n";
            case "order_review_node"   -> "data: ⭐ 正在提交评价...\n\n";
            case "general_answer_node" -> "data: 💬 正在生成回答...\n\n";
            case "response_formatter"  -> "data: ✍️ 正在整理回复...\n\n";
            default                    -> "data: ⚙️ 处理中...\n\n";
        };

        if (output.state() != null) {
            Object answer = output.state().value("final_answer").orElse(null);
            if (answer != null && !answer.toString().isBlank()) {
                return progress + "data: [ANSWER]\n" + answer + "\n\n";
            }
        }
        return progress;
    }
}

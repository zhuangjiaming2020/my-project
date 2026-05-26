package com.example.mallcs.config;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.example.mallcs.graph.MallStateFactory;
import com.example.mallcs.graph.nodes.*;
import com.example.mallcs.service.MallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 商城客服 Graph 工作流装配配置。
 *
 * <h3>工作流拓扑图</h3>
 * <pre>
 *                    ┌─────────────────────────────────────────────┐
 *                    │                                             │
 *   START ──► [intent_parser] ──► logistics_query_node ──► [response_formatter] ──► END
 *                    │        ──► order_status_node    ──► [response_formatter] ──► END
 *                    │        ──► order_review_node    ──► [response_formatter] ──► END
 *                    │        ──► general_answer_node  ──────────────────────────► END
 *                    │                                             │
 *                    └─────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>节点职责</h3>
 * <ul>
 *   <li>intent_parser：LLM 提取场景类型（scene_type）与实体（订单号、评分等）</li>
 *   <li>logistics_query_node：查询物流轨迹（MockDB）</li>
 *   <li>order_status_node：查询订单状态（MockDB）</li>
 *   <li>order_review_node：提交订单评价（MockDB）</li>
 *   <li>general_answer_node：LLM 直接回答通用问题，写 final_answer，跳过 formatter</li>
 *   <li>response_formatter：LLM 将结构化数据转化为自然语言客服回复</li>
 * </ul>
 */
@Configuration
public class MallGraphConfig {

    private static final Logger log = LoggerFactory.getLogger(MallGraphConfig.class);

    // 节点名称常量
    public static final String NODE_INTENT_PARSER     = "intent_parser";
    public static final String NODE_LOGISTICS_QUERY   = "logistics_query_node";
    public static final String NODE_ORDER_STATUS      = "order_status_node";
    public static final String NODE_ORDER_REVIEW      = "order_review_node";
    public static final String NODE_GENERAL_ANSWER    = "general_answer_node";
    public static final String NODE_RESPONSE_FORMATTER = "response_formatter";

    // 场景类型常量（与 IntentParserNode 的 LLM 输出对齐）
    public static final String SCENE_LOGISTICS  = "logistics_query";
    public static final String SCENE_ORDER      = "order_status";
    public static final String SCENE_REVIEW     = "order_review";
    public static final String SCENE_GENERAL    = "general";

    @Bean
    public CompiledGraph mallCustomerServiceGraph(
            ChatModel chatModel,
            MallDataService mallDataService) throws GraphStateException {

        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);

        log.info("[MallGraph] 开始构建商城客服 Graph 工作流...");

        // ----------------------------------------------------------
        // 1. 创建节点（传入所需依赖）
        // ----------------------------------------------------------
        var intentParserNode    = node_async(new IntentParserNode(chatClientBuilder));
        var logisticsQueryNode  = node_async(new LogisticsQueryNode(mallDataService));
        var orderStatusNode     = node_async(new OrderStatusNode(mallDataService));
        var orderReviewNode     = node_async(new OrderReviewNode(mallDataService));
        var generalAnswerNode   = node_async(new GeneralAnswerNode(chatClientBuilder));
        var responseFormatter   = node_async(new ResponseFormatterNode(chatClientBuilder));

        // ----------------------------------------------------------
        // 2. 构建 StateGraph
        // ----------------------------------------------------------
        StateGraph workflow = new StateGraph(MallStateFactory.create())
                .addNode(NODE_INTENT_PARSER,      intentParserNode)
                .addNode(NODE_LOGISTICS_QUERY,    logisticsQueryNode)
                .addNode(NODE_ORDER_STATUS,       orderStatusNode)
                .addNode(NODE_ORDER_REVIEW,       orderReviewNode)
                .addNode(NODE_GENERAL_ANSWER,     generalAnswerNode)
                .addNode(NODE_RESPONSE_FORMATTER, responseFormatter);

        // ----------------------------------------------------------
        // 3. 添加固定边
        // ----------------------------------------------------------
        // 入口
        workflow.addEdge(START, NODE_INTENT_PARSER);

        // 数据节点 → 格式化节点
        workflow.addEdge(NODE_LOGISTICS_QUERY,   NODE_RESPONSE_FORMATTER);
        workflow.addEdge(NODE_ORDER_STATUS,      NODE_RESPONSE_FORMATTER);
        workflow.addEdge(NODE_ORDER_REVIEW,      NODE_RESPONSE_FORMATTER);

        // 出口
        workflow.addEdge(NODE_RESPONSE_FORMATTER, END);
        workflow.addEdge(NODE_GENERAL_ANSWER,     END);

        // ----------------------------------------------------------
        // 4. 添加条件边：intent_parser → 按 scene_type 路由
        // ----------------------------------------------------------
        workflow.addConditionalEdges(
                NODE_INTENT_PARSER,
                edge_async(state -> {
                    String sceneType = state.value("scene_type")
                            .map(Object::toString)
                            .orElse(SCENE_GENERAL);
                    log.debug("[Router] scene_type={}", sceneType);
                    return switch (sceneType) {
                        case SCENE_LOGISTICS -> NODE_LOGISTICS_QUERY;
                        case SCENE_ORDER     -> NODE_ORDER_STATUS;
                        case SCENE_REVIEW    -> NODE_ORDER_REVIEW;
                        default              -> NODE_GENERAL_ANSWER;
                    };
                }),
                Map.of(
                        NODE_LOGISTICS_QUERY, NODE_LOGISTICS_QUERY,
                        NODE_ORDER_STATUS,    NODE_ORDER_STATUS,
                        NODE_ORDER_REVIEW,    NODE_ORDER_REVIEW,
                        NODE_GENERAL_ANSWER,  NODE_GENERAL_ANSWER
                )
        );

        // ----------------------------------------------------------
        // 5. 编译（启用 MemorySaver 支持多轮会话持久化）
        // ----------------------------------------------------------
        MemorySaver memorySaver = new MemorySaver();
        CompileConfig compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(memorySaver)
                        .build())
                .build();

        CompiledGraph compiledGraph = workflow.compile(compileConfig);
        log.info("[MallGraph] Graph 工作流构建完成！");
        return compiledGraph;
    }
}

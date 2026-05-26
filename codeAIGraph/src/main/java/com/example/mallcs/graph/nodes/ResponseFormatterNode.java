package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * 响应格式化节点（所有数据类场景的最终输出节点）。
 *
 * <p>接收上游数据节点写入的 query_result（结构化文本），
 * 调用 DeepSeek LLM 将其转化为自然、友好的客服话术，写入 final_answer。
 *
 * <p>适用场景：logistics_query、order_status、order_review
 * （general 场景由 GeneralAnswerNode 直接写入 final_answer，跳过本节点）
 */
public class ResponseFormatterNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(ResponseFormatterNode.class);

    private static final String SYSTEM_PROMPT = """
            你是一个商城智能客服助手"小智"。
            你将收到一段数据查询结果文本（可能是订单信息、物流信息或评价结果），
            请将其转化为亲切、自然的客服回复，发送给客户。
            
            格式要求：
            1. 开头称呼：用"亲爱的顾客，您好！"或"您好，" 开头
            2. 将数据信息以友好的方式呈现，重要信息可以加粗（使用**文字**语法）
            3. 结尾提供进一步帮助的引导，如"如有其他问题，欢迎随时告知！"
            4. 语气温暖、专业，字数控制在200字以内
            5. 若数据中包含错误提示，也以友好语气转述
            """;

    private final ChatClient chatClient;

    public ResponseFormatterNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String queryResult = state.value("query_result")
                .map(Object::toString)
                .orElse("暂时无法获取相关信息");

        String userInput = state.value("user_input")
                .map(Object::toString)
                .orElse("");

        String sceneType = state.value("scene_type")
                .map(Object::toString)
                .orElse("unknown");

        log.info("[ResponseFormatter] 格式化场景[{}]的查询结果", sceneType);

        String userPrompt = String.format("""
                用户提问: %s
                
                查询结果数据:
                %s
                
                请基于以上数据生成一段客服回复。
                """, userInput, queryResult);

        String finalAnswer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        log.info("[ResponseFormatter] 回复生成完毕");
        return Map.of("final_answer", finalAnswer);
    }
}

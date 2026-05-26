package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * 通用问答节点（兜底节点）。
 *
 * <p>处理不属于具体业务场景的通用咨询，直接调用 DeepSeek LLM 生成回答。
 * 本节点直接写入 final_answer，跳过 ResponseFormatterNode，直接到达 END。
 */
public class GeneralAnswerNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(GeneralAnswerNode.class);

    private static final String SYSTEM_PROMPT = """
            你是一个专业的商城客服助手，名字叫"小智"。
            你的工作是回答用户关于购物的各类问题，包括但不限于：
            - 商品介绍、使用说明
            - 购物流程（下单、支付、发货、收货）
            - 退换货政策
            - 优惠活动咨询
            - 账号与安全问题
            
            回答要求：
            1. 语气亲切、专业，用"您"称呼用户
            2. 回答简洁明了，重点突出
            3. 若用户问题超出您的能力范围，礼貌地建议联系人工客服（电话：400-123-4567，工作时间 9:00-21:00）
            4. 不要编造具体的订单、商品信息
            """;

    private final ChatClient chatClient;

    public GeneralAnswerNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userInput = state.value("user_input")
                .map(Object::toString)
                .orElse("你好");

        log.info("[GeneralAnswer] 处理通用咨询: {}", userInput);

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .call()
                .content();

        log.info("[GeneralAnswer] 已生成回答");
        return Map.of("final_answer", answer);
    }
}

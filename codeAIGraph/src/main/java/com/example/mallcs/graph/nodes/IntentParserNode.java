package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图解析节点（Graph 入口）。
 *
 * <p>职责：
 * <ol>
 *   <li>调用 DeepSeek LLM 分析用户输入，提取场景类型与关键实体</li>
 *   <li>将结构化解析结果写入 Graph 状态</li>
 *   <li>通过 scene_type 驱动下游条件路由</li>
 * </ol>
 *
 * <p>输出状态键：scene_type, order_no, review_rating, review_content
 */
public class IntentParserNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(IntentParserNode.class);

    private static final String SYSTEM_PROMPT = """
            你是一个商城智能客服意图解析助手。请分析用户的输入，提取以下信息并严格按照指定格式输出。
            
            场景类型（scene_type）说明：
            - logistics_query：用户想查询快递/物流/包裹状态（包括："到哪了""快递""运输""签收"等）
            - order_status：用户想查询订单状态/订单信息（包括："订单状态""订单详情""有没有发货"等）
            - order_review：用户想对订单进行评价/打分（包括："评价""评分""写评论""满意度"等）
            - general：不属于以上三类的通用咨询
            
            输出格式（每行一个字段，严格按此格式）：
            SCENE_TYPE: <logistics_query|order_status|order_review|general>
            ORDER_NO: <订单号，若未提及则填 NONE>
            REVIEW_RATING: <1-5的整数，仅评价场景填写，其他填 NONE>
            REVIEW_CONTENT: <评价内容文本，仅评价场景填写，其他填 NONE>
            
            注意：
            1. 只输出上述4行，不要有任何额外解释
            2. 订单号通常以字母+数字组合，如 ORD001、202412345 等
            3. 评价内容从用户输入中提取，如未提供则填 NONE
            """;

    private final ChatClient chatClient;

    public IntentParserNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userInput = state.value("user_input")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalStateException("user_input 不能为空"));

        log.info("[IntentParser] 开始解析用户输入: {}", userInput);

        String llmResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .call()
                .content();

        log.info("[IntentParser] LLM 解析结果:\n{}", llmResponse);

        Map<String, Object> parsed = parseResponse(llmResponse);

        String sceneType = (String) parsed.getOrDefault("scene_type", "general");
        log.info("[IntentParser] 识别场景: {}, 订单号: {}", sceneType, parsed.get("order_no"));

        Map<String, Object> updates = new HashMap<>(parsed);
        return updates;
    }

    private Map<String, Object> parseResponse(String response) {
        Map<String, Object> result = new HashMap<>();

        result.put("scene_type", extractField(response, "SCENE_TYPE", "general"));

        String orderNo = extractField(response, "ORDER_NO", "NONE");
        result.put("order_no", "NONE".equals(orderNo) ? "" : orderNo);

        String ratingStr = extractField(response, "REVIEW_RATING", "NONE");
        if (!"NONE".equals(ratingStr)) {
            try {
                int rating = Integer.parseInt(ratingStr.trim());
                result.put("review_rating", Math.min(5, Math.max(1, rating)));
            } catch (NumberFormatException e) {
                result.put("review_rating", 5);
            }
        } else {
            result.put("review_rating", 0);
        }

        String reviewContent = extractField(response, "REVIEW_CONTENT", "NONE");
        result.put("review_content", "NONE".equals(reviewContent) ? "" : reviewContent);

        return result;
    }

    private String extractField(String text, String fieldName, String defaultValue) {
        Pattern pattern = Pattern.compile(
                "^" + fieldName + ":\\s*(.+)$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }
}

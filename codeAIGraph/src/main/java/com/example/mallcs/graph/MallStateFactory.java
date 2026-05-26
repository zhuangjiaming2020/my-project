package com.example.mallcs.graph;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * Graph 状态键策略工厂。
 *
 * <p>定义工作流中各个状态键的更新策略：
 * <ul>
 *   <li>{@link ReplaceStrategy} - 每次更新直接替换旧值（适用于单值字段）</li>
 *   <li>{@link AppendStrategy} - 每次更新追加到列表（适用于消息历史）</li>
 * </ul>
 *
 * <h3>状态键说明</h3>
 * <pre>
 * user_input      用户原始输入
 * order_no        从用户输入中提取的订单号
 * scene_type      场景类型: logistics_query | order_status | order_review | general
 * review_rating   评价星级（仅 order_review 场景）
 * review_content  评价内容（仅 order_review 场景）
 * query_result    数据层查询结果（格式化文本，供 LLM 生成回答）
 * final_answer    最终回复内容（发送给用户）
 * messages        对话消息历史（追加模式）
 * error_msg       错误信息（可选）
 * </pre>
 */
public class MallStateFactory {

    private MallStateFactory() {}

    public static KeyStrategyFactory create() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();

            strategies.put("user_input",      new ReplaceStrategy());
            strategies.put("order_no",        new ReplaceStrategy());
            strategies.put("scene_type",      new ReplaceStrategy());
            strategies.put("review_rating",   new ReplaceStrategy());
            strategies.put("review_content",  new ReplaceStrategy());
            strategies.put("query_result",    new ReplaceStrategy());
            strategies.put("final_answer",    new ReplaceStrategy());
            strategies.put("error_msg",       new ReplaceStrategy());
            strategies.put("messages",        new AppendStrategy());

            return strategies;
        };
    }
}

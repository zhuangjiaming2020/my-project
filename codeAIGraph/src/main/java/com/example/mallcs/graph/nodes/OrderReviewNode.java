package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.mallcs.service.MallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 订单评价节点。
 *
 * <p>根据意图解析节点提取的订单号、评分、评价内容，
 * 调用 {@link MallDataService} 提交评价并将结果写入 query_result。
 */
public class OrderReviewNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(OrderReviewNode.class);

    private final MallDataService dataService;

    public OrderReviewNode(MallDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String orderNo = state.value("order_no").map(Object::toString).orElse("");
        int rating = state.value("review_rating")
                .map(v -> v instanceof Integer i ? i : Integer.parseInt(v.toString()))
                .orElse(0);
        String content = state.value("review_content").map(Object::toString).orElse("");

        if (orderNo.isBlank()) {
            log.warn("[OrderReview] 缺少订单号");
            return Map.of("query_result",
                    "请告诉我您想评价的订单号（如：ORD001），以及您的评分（1-5星）和评价内容，我来帮您提交评价。");
        }

        if (rating < 1 || rating > 5) {
            log.warn("[OrderReview] 缺少有效评分，当前: {}", rating);
            return Map.of("query_result",
                    String.format("收到您对订单【%s】的评价请求。请问您给几星好评？（1-5星）以及您的评价内容是什么？", orderNo));
        }

        if (content.isBlank()) {
            content = generateDefaultContent(rating);
        }

        log.info("[OrderReview] 提交评价: orderId={}, rating={}", orderNo, rating);
        String result = dataService.submitOrderReview(orderNo, rating, content);
        log.info("[OrderReview] 评价完成，orderNo={}", orderNo);

        return Map.of("query_result", result);
    }

    private String generateDefaultContent(int rating) {
        return switch (rating) {
            case 5 -> "非常满意，商品质量很好，物流很快，强烈推荐！";
            case 4 -> "整体不错，商品符合描述，物流速度满意。";
            case 3 -> "一般般，基本符合预期。";
            case 2 -> "不太满意，有待改进。";
            case 1 -> "很不满意。";
            default -> "已收到商品。";
        };
    }
}

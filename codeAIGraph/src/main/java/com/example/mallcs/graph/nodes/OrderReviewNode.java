package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.mallcs.domain.ReviewResult;
import com.example.mallcs.mock.MockMallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 订单评价节点。
 *
 * <p>根据意图解析节点提取的订单号、评分、评价内容，调用数据服务提交评价。
 * 若关键信息缺失，返回引导提示。
 */
public class OrderReviewNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(OrderReviewNode.class);

    private final MockMallDataService dataService;

    public OrderReviewNode(MockMallDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String orderNo = state.value("order_no").map(Object::toString).orElse("");
        int rating = state.value("review_rating")
                .map(v -> v instanceof Integer i ? i : Integer.parseInt(v.toString()))
                .orElse(0);
        String content = state.value("review_content").map(Object::toString).orElse("");

        // 校验必要参数
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

        ReviewResult result = dataService.submitReview(orderNo, rating, content);

        String queryResult = formatReviewResult(result);
        log.info("[OrderReview] 评价结果: success={}", result.isSuccess());

        return Map.of("query_result", queryResult);
    }

    private String formatReviewResult(ReviewResult result) {
        if (result.isSuccess()) {
            String stars = "⭐".repeat(result.getRating());
            return String.format(
                    "评价提交成功！\n订单号: %s\n评分: %s（%d星）\n评价内容: %s\n评价编号: %s\n%s",
                    result.getOrderId(), stars, result.getRating(),
                    result.getContent(), result.getReviewId(), result.getMessage()
            );
        } else {
            return String.format("评价提交失败。\n原因: %s", result.getMessage());
        }
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

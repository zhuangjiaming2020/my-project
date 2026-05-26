package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.mallcs.domain.Order;
import com.example.mallcs.mock.MockMallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * 订单状态查询节点。
 *
 * <p>根据订单号查询订单详情，将结构化数据转换为文本写入 query_result。
 */
public class OrderStatusNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusNode.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    private final MockMallDataService dataService;

    public OrderStatusNode(MockMallDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String orderNo = state.value("order_no").map(Object::toString).orElse("");

        if (orderNo.isBlank()) {
            log.warn("[OrderStatus] 未获取到订单号");
            return Map.of(
                    "query_result", "很抱歉，我没有找到您提供的订单号。请告诉我您的订单号（如：ORD001），我来帮您查询。",
                    "error_msg", "missing_order_no"
            );
        }

        log.info("[OrderStatus] 查询订单状态，订单号: {}", orderNo);

        Optional<Order> orderOpt = dataService.getOrderStatus(orderNo);
        if (orderOpt.isEmpty()) {
            return Map.of("query_result",
                    String.format("未找到订单号【%s】对应的订单，请确认订单号是否正确。", orderNo));
        }

        Order order = orderOpt.get();
        String result = formatOrder(order);
        log.info("[OrderStatus] 查询成功: {} - {}", order.getOrderId(), order.getStatus());

        return Map.of("query_result", result);
    }

    private String formatOrder(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("订单号: %s\n", order.getOrderId()));
        sb.append(String.format("商品: %s（%s）\n", order.getProductName(), order.getProductSpec()));
        sb.append(String.format("实付金额: ¥%s\n", order.getAmount()));
        sb.append(String.format("订单状态: 【%s】\n", order.getStatusDesc()));
        sb.append(String.format("下单时间: %s\n", order.getCreateTime().format(FORMATTER)));
        if (order.getPayTime() != null) {
            sb.append(String.format("付款时间: %s\n", order.getPayTime().format(FORMATTER)));
        }
        sb.append(String.format("收货地址: %s\n", order.getReceiverAddress()));

        // 补充状态说明
        String tip = switch (order.getStatus()) {
            case "PENDING_PAY" -> "温馨提示：订单还未付款，请尽快完成支付，超时将自动取消。";
            case "PENDING_SHIP" -> "温馨提示：订单已付款，商家正在备货，预计1-2个工作日内发货。";
            case "SHIPPED" -> "温馨提示：您的包裹已在路上，可查询物流了解快递动态。";
            case "COMPLETED" -> order.isReviewed()
                    ? "订单已完成且已评价，感谢您的反馈！"
                    : "订单已完成，欢迎对本次购物进行评价！";
            case "CANCELLED" -> "订单已取消，如有问题请联系在线客服。";
            case "REFUNDING" -> "退款申请正在处理中，预计3-5个工作日退款到账。";
            default -> "";
        };
        if (!tip.isBlank()) {
            sb.append(tip).append("\n");
        }

        return sb.toString();
    }
}

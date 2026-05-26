package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.mallcs.service.MallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 订单状态查询节点。
 *
 * <p>根据订单号调用 {@link MallDataService} 查询订单状态，
 * 将结果文本写入 query_result 供 ResponseFormatterNode 使用。
 */
public class OrderStatusNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusNode.class);

    private final MallDataService dataService;

    public OrderStatusNode(MallDataService dataService) {
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
        String result = dataService.queryOrderStatus(orderNo);
        log.info("[OrderStatus] 查询完成，orderNo={}", orderNo);

        return Map.of("query_result", result);
    }
}

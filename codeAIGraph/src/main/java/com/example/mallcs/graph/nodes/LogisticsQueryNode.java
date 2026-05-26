package com.example.mallcs.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.mallcs.domain.Logistics;
import com.example.mallcs.domain.LogisticsEvent;
import com.example.mallcs.mock.MockMallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * 物流查询节点。
 *
 * <p>根据订单号查询物流轨迹信息，将结构化数据转换为文本写入 query_result。
 * 下游的 ResponseFormatterNode 将基于此文本生成自然语言回复。
 */
public class LogisticsQueryNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(LogisticsQueryNode.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    private final MockMallDataService dataService;

    public LogisticsQueryNode(MockMallDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String orderNo = state.value("order_no").map(Object::toString).orElse("");

        if (orderNo.isBlank()) {
            log.warn("[LogisticsQuery] 未获取到订单号");
            return Map.of(
                    "query_result", "很抱歉，我没有找到您提供的订单号。请确认订单号是否正确，通常格式如：ORD001。",
                    "error_msg", "missing_order_no"
            );
        }

        log.info("[LogisticsQuery] 查询物流，订单号: {}", orderNo);

        Optional<Logistics> logisticsOpt = dataService.getLogisticsInfo(orderNo);
        if (logisticsOpt.isEmpty()) {
            // 检查订单是否存在但未发货
            boolean orderExists = dataService.getOrderStatus(orderNo).isPresent();
            if (orderExists) {
                return Map.of("query_result",
                        String.format("订单【%s】暂无物流信息，可能还未发货或物流信息尚未更新，请稍后再查。", orderNo));
            }
            return Map.of("query_result",
                    String.format("未找到订单【%s】的物流信息，请确认订单号是否正确。", orderNo));
        }

        Logistics logistics = logisticsOpt.get();
        String result = formatLogistics(logistics);
        log.info("[LogisticsQuery] 查询成功: {}", logistics.getStatus());

        return Map.of("query_result", result);
    }

    private String formatLogistics(Logistics logistics) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("订单号: %s\n", logistics.getOrderId()));
        sb.append(String.format("快递公司: %s（客服电话: %s）\n", logistics.getCarrier(), logistics.getCarrierPhone()));
        sb.append(String.format("快递单号: %s\n", logistics.getTrackingNo()));
        sb.append(String.format("当前状态: %s\n", logistics.getStatusDesc()));
        sb.append(String.format("预计送达: %s\n", logistics.getEstimatedDelivery()));
        sb.append("物流轨迹（最新在前）:\n");
        if (logistics.getEvents() != null) {
            for (LogisticsEvent event : logistics.getEvents()) {
                sb.append(String.format("  [%s] %s - %s\n",
                        event.getEventTime().format(FORMATTER),
                        event.getLocation(),
                        event.getDescription()));
            }
        }
        return sb.toString();
    }
}

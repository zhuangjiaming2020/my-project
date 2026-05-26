package com.example.mallcs.mock;

import com.example.mallcs.domain.Logistics;
import com.example.mallcs.domain.LogisticsEvent;
import com.example.mallcs.domain.Order;
import com.example.mallcs.domain.ReviewResult;
import com.example.mallcs.service.MallDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 数据服务 —— 模拟数据库操作，同时实现 {@link MallDataService} 接口。
 *
 * <p>实现策略：
 * <ul>
 *   <li>{@link #queryOrderStatus}/{@link #queryLogisticsInfo}/{@link #submitOrderReview}
 *       实现 {@link MallDataService} 接口，返回格式化字符串供 Graph 节点直接使用</li>
 *   <li>{@link #getOrderStatus}/{@link #getLogisticsInfo}/{@link #submitReview}
 *       保留原始领域对象返回值，供 {@link com.example.mallcs.service.ConfigurableMallDataService} 降级调用</li>
 * </ul>
 *
 * <p>生产替换方式：
 * <ol>
 *   <li>将本类中的 Map 查询替换为对应 Repository/Mapper 调用</li>
 *   <li>submitReview 改为真实数据库 INSERT</li>
 *   <li>上层 Graph 节点无需改动</li>
 * </ol>
 */
@Service
public class MockMallDataService implements MallDataService {

    private static final Logger log = LoggerFactory.getLogger(MockMallDataService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
    private static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");

    // ----------------------------------------------------------------
    // 静态 Mock 数据集（模拟数据库表）
    // ----------------------------------------------------------------

    private static final Map<String, Order> ORDER_TABLE = new ConcurrentHashMap<>();
    private static final Map<String, Logistics> LOGISTICS_TABLE = new ConcurrentHashMap<>();

    static {
        initOrders();
        initLogistics();
    }

    // ================================================================
    // MallDataService 接口实现（返回格式化字符串）
    // ================================================================

    @Override
    public String queryOrderStatus(String orderId) {
        log.info("[MockDB] 查询订单状态: {}", orderId);
        Optional<Order> orderOpt = getOrderStatus(orderId);
        if (orderOpt.isEmpty()) {
            return String.format("未找到订单号【%s】对应的订单，请确认订单号是否正确。", orderId);
        }
        return formatOrder(orderOpt.get());
    }

    @Override
    public String queryLogisticsInfo(String orderId) {
        log.info("[MockDB] 查询物流信息: {}", orderId);
        Optional<Logistics> logisticsOpt = getLogisticsInfo(orderId);
        if (logisticsOpt.isEmpty()) {
            boolean orderExists = getOrderStatus(orderId).isPresent();
            if (orderExists) {
                return String.format("订单【%s】暂无物流信息，可能还未发货或物流信息尚未更新，请稍后再查。", orderId);
            }
            return String.format("未找到订单【%s】的物流信息，请确认订单号是否正确。", orderId);
        }
        return formatLogistics(logisticsOpt.get());
    }

    @Override
    public String submitOrderReview(String orderId, int rating, String content) {
        ReviewResult result = submitReview(orderId, rating, content);
        return formatReviewResult(result);
    }

    // ================================================================
    // 原始领域对象方法（供 ConfigurableMallDataService 降级使用）
    // ================================================================

    public Optional<Order> getOrderStatus(String orderId) {
        return Optional.ofNullable(ORDER_TABLE.get(orderId.toUpperCase()));
    }

    public Optional<Logistics> getLogisticsInfo(String orderId) {
        return Optional.ofNullable(LOGISTICS_TABLE.get(orderId.toUpperCase()));
    }

    public ReviewResult submitReview(String orderId, int rating, String content) {
        log.info("[MockDB] 提交评价: orderId={}, rating={}", orderId, rating);

        Order order = ORDER_TABLE.get(orderId.toUpperCase());
        if (order == null) {
            return ReviewResult.builder()
                    .success(false).orderId(orderId)
                    .message("订单 " + orderId + " 不存在，无法评价")
                    .build();
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            return ReviewResult.builder()
                    .success(false).orderId(orderId)
                    .message("订单状态为【" + order.getStatusDesc() + "】，只有已完成的订单才能评价")
                    .build();
        }
        if (order.isReviewed()) {
            return ReviewResult.builder()
                    .success(false).orderId(orderId)
                    .message("订单 " + orderId + " 已经评价过了，不能重复评价")
                    .build();
        }

        order.setReviewed(true);
        String reviewId = "REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ReviewResult.builder()
                .success(true).orderId(orderId).reviewId(reviewId)
                .rating(rating).content(content)
                .message("评价成功！您的评价将帮助更多买家。评价ID：" + reviewId)
                .build();
    }

    // ================================================================
    // 格式化方法
    // ================================================================

    private String formatOrder(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("订单号: %s\n", order.getOrderId()));
        sb.append(String.format("商品: %s（%s）\n", order.getProductName(), order.getProductSpec()));
        sb.append(String.format("实付金额: ¥%s\n", order.getAmount()));
        sb.append(String.format("订单状态: 【%s】\n", order.getStatusDesc()));
        sb.append(String.format("下单时间: %s\n", order.getCreateTime().format(DATE_FMT)));
        if (order.getPayTime() != null) {
            sb.append(String.format("付款时间: %s\n", order.getPayTime().format(DATE_FMT)));
        }
        sb.append(String.format("收货地址: %s\n", order.getReceiverAddress()));

        String tip = switch (order.getStatus()) {
            case "PENDING_PAY"  -> "温馨提示：订单还未付款，请尽快完成支付，超时将自动取消。";
            case "PENDING_SHIP" -> "温馨提示：订单已付款，商家正在备货，预计1-2个工作日内发货。";
            case "SHIPPED"      -> "温馨提示：您的包裹已在路上，可查询物流了解快递动态。";
            case "COMPLETED"    -> order.isReviewed()
                    ? "订单已完成且已评价，感谢您的反馈！"
                    : "订单已完成，欢迎对本次购物进行评价！";
            case "CANCELLED"    -> "订单已取消，如有问题请联系在线客服。";
            case "REFUNDING"    -> "退款申请正在处理中，预计3-5个工作日退款到账。";
            default -> "";
        };
        if (!tip.isBlank()) sb.append(tip).append("\n");
        return sb.toString();
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
                        event.getEventTime().format(SHORT_FMT),
                        event.getLocation(),
                        event.getDescription()));
            }
        }
        return sb.toString();
    }

    private String formatReviewResult(ReviewResult result) {
        if (result.isSuccess()) {
            String stars = "⭐".repeat(result.getRating());
            return String.format(
                    "评价提交成功！\n订单号: %s\n评分: %s（%d星）\n评价内容: %s\n评价编号: %s\n%s",
                    result.getOrderId(), stars, result.getRating(),
                    result.getContent(), result.getReviewId(), result.getMessage()
            );
        }
        return String.format("评价提交失败。\n原因: %s", result.getMessage());
    }

    // ================================================================
    // 初始化 Mock 数据
    // ================================================================

    private static void initOrders() {
        ORDER_TABLE.put("ORD001", Order.builder()
                .orderId("ORD001").userId("U10001").userName("张小明")
                .productName("Apple iPhone 16 Pro").productSpec("256GB 沙漠钛金色")
                .amount(new BigDecimal("9999.00")).status("COMPLETED").statusDesc("已完成")
                .createTime(LocalDateTime.now().minusDays(15))
                .payTime(LocalDateTime.now().minusDays(15))
                .receiverAddress("北京市朝阳区建国路88号").reviewed(false).build());

        ORDER_TABLE.put("ORD002", Order.builder()
                .orderId("ORD002").userId("U10001").userName("张小明")
                .productName("Nike Air Max 2024 跑步鞋").productSpec("42码 黑色")
                .amount(new BigDecimal("899.00")).status("SHIPPED").statusDesc("已发货")
                .createTime(LocalDateTime.now().minusDays(3))
                .payTime(LocalDateTime.now().minusDays(3))
                .receiverAddress("北京市朝阳区建国路88号").reviewed(false).build());

        ORDER_TABLE.put("ORD003", Order.builder()
                .orderId("ORD003").userId("U10002").userName("李晓红")
                .productName("小米平板 7 Pro").productSpec("12GB+256GB 深空灰")
                .amount(new BigDecimal("2999.00")).status("PENDING_SHIP").statusDesc("待发货")
                .createTime(LocalDateTime.now().minusDays(1))
                .payTime(LocalDateTime.now().minusDays(1))
                .receiverAddress("上海市浦东新区陆家嘴路100号").reviewed(false).build());

        ORDER_TABLE.put("ORD004", Order.builder()
                .orderId("ORD004").userId("U10003").userName("王大力")
                .productName("Sony WH-1000XM6 头戴耳机").productSpec("黑色")
                .amount(new BigDecimal("2499.00")).status("PENDING_PAY").statusDesc("待付款")
                .createTime(LocalDateTime.now().minusHours(2)).payTime(null)
                .receiverAddress("广州市天河区珠江新城100号").reviewed(false).build());

        ORDER_TABLE.put("ORD005", Order.builder()
                .orderId("ORD005").userId("U10002").userName("李晓红")
                .productName("戴森 V15 吸尘器").productSpec("金色限定版")
                .amount(new BigDecimal("4990.00")).status("REFUNDING").statusDesc("退款中")
                .createTime(LocalDateTime.now().minusDays(7))
                .payTime(LocalDateTime.now().minusDays(7))
                .receiverAddress("上海市浦东新区陆家嘴路100号").reviewed(false).build());
    }

    private static void initLogistics() {
        LOGISTICS_TABLE.put("ORD001", Logistics.builder()
                .orderId("ORD001").trackingNo("SF1234567890")
                .carrier("顺丰速运").carrierPhone("95338")
                .status("DELIVERED").statusDesc("已签收").estimatedDelivery("已签收")
                .events(List.of(
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(10))
                                .location("北京市朝阳区建国路88号")
                                .description("快件已由本人签收，感谢使用顺丰").build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(10).minusHours(2))
                                .location("北京朝阳区配送站")
                                .description("快件正在派送中，派送员：王师傅，电话：138****8888").build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(11))
                                .location("北京顺丰转运中心")
                                .description("快件已到达北京顺丰转运中心").build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(12))
                                .location("上海浦东顺丰转运中心")
                                .description("快件已从上海发出，正在运往北京").build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(13))
                                .location("上海顺丰仓储中心")
                                .description("商家已打包完毕，顺丰已揽收").build()
                )).build());

        LOGISTICS_TABLE.put("ORD002", Logistics.builder()
                .orderId("ORD002").trackingNo("JD9876543210")
                .carrier("京东物流").carrierPhone("950616")
                .status("OUT_FOR_DELIVERY").statusDesc("派送中")
                .estimatedDelivery("预计今天18:00前送达")
                .events(List.of(
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusHours(3))
                                .location("北京朝阳区京东配送站")
                                .description("快件已由京东快递员李师傅揽收，正在派送，电话：139****6666").build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(1))
                                .location("北京京东亚洲一号仓储中心")
                                .description("快件已到达北京分拣中心").build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(2))
                                .location("武汉京东仓储中心")
                                .description("商品出库，开始运输").build()
                )).build());
    }
}

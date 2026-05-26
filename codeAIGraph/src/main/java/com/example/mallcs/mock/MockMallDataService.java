package com.example.mallcs.mock;

import com.example.mallcs.domain.Logistics;
import com.example.mallcs.domain.LogisticsEvent;
import com.example.mallcs.domain.Order;
import com.example.mallcs.domain.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 数据服务 —— 模拟数据库操作。
 *
 * 生产替换方式：
 * 1. 将本类中的 Map 查询替换为对应 Repository/Mapper 方法调用；
 * 2. submitReview 改为真实数据库 INSERT；
 * 3. 删除 @Service 注解并注入真实 DAO 即可，上层 Graph 节点无需改动。
 */
@Service
public class MockMallDataService {

    private static final Logger log = LoggerFactory.getLogger(MockMallDataService.class);

    // ----------------------------------------------------------------
    // 静态 Mock 数据集（模拟数据库表）
    // ----------------------------------------------------------------

    private static final Map<String, Order> ORDER_TABLE = new ConcurrentHashMap<>();
    private static final Map<String, Logistics> LOGISTICS_TABLE = new ConcurrentHashMap<>();

    static {
        initOrders();
        initLogistics();
    }

    // ----------------------------------------------------------------
    // 公开 API
    // ----------------------------------------------------------------

    /**
     * 查询订单状态
     *
     * @param orderId 订单号
     * @return Optional.empty() 表示订单不存在
     */
    public Optional<Order> getOrderStatus(String orderId) {
        log.info("[MockDB] 查询订单: {}", orderId);
        return Optional.ofNullable(ORDER_TABLE.get(orderId.toUpperCase()));
    }

    /**
     * 查询物流信息
     *
     * @param orderId 订单号
     * @return Optional.empty() 表示暂无物流信息
     */
    public Optional<Logistics> getLogisticsInfo(String orderId) {
        log.info("[MockDB] 查询物流: {}", orderId);
        return Optional.ofNullable(LOGISTICS_TABLE.get(orderId.toUpperCase()));
    }

    /**
     * 提交订单评价
     *
     * @param orderId 订单号
     * @param rating  星级 1-5
     * @param content 评价内容
     * @return 评价结果
     */
    public ReviewResult submitReview(String orderId, int rating, String content) {
        log.info("[MockDB] 提交评价: orderId={}, rating={}, content={}", orderId, rating, content);

        Order order = ORDER_TABLE.get(orderId.toUpperCase());
        if (order == null) {
            return ReviewResult.builder()
                    .success(false)
                    .orderId(orderId)
                    .message("订单 " + orderId + " 不存在，无法评价")
                    .build();
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            return ReviewResult.builder()
                    .success(false)
                    .orderId(orderId)
                    .message("订单状态为【" + order.getStatusDesc() + "】，只有已完成的订单才能评价")
                    .build();
        }
        if (order.isReviewed()) {
            return ReviewResult.builder()
                    .success(false)
                    .orderId(orderId)
                    .message("订单 " + orderId + " 已经评价过了，不能重复评价")
                    .build();
        }

        // 标记已评价（生产环境改为 DB UPDATE）
        order.setReviewed(true);
        String reviewId = "REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return ReviewResult.builder()
                .success(true)
                .orderId(orderId)
                .reviewId(reviewId)
                .rating(rating)
                .content(content)
                .message("评价成功！您的评价将帮助更多买家。评价ID：" + reviewId)
                .build();
    }

    // ----------------------------------------------------------------
    // 初始化 Mock 数据
    // ----------------------------------------------------------------

    private static void initOrders() {
        // ORD001 - 已完成，可评价
        ORDER_TABLE.put("ORD001", Order.builder()
                .orderId("ORD001")
                .userId("U10001")
                .userName("张小明")
                .productName("Apple iPhone 16 Pro")
                .productSpec("256GB 沙漠钛金色")
                .amount(new BigDecimal("9999.00"))
                .status("COMPLETED")
                .statusDesc("已完成")
                .createTime(LocalDateTime.now().minusDays(15))
                .payTime(LocalDateTime.now().minusDays(15))
                .receiverAddress("北京市朝阳区建国路88号")
                .reviewed(false)
                .build());

        // ORD002 - 已发货，有物流
        ORDER_TABLE.put("ORD002", Order.builder()
                .orderId("ORD002")
                .userId("U10001")
                .userName("张小明")
                .productName("Nike Air Max 2024 跑步鞋")
                .productSpec("42码 黑色")
                .amount(new BigDecimal("899.00"))
                .status("SHIPPED")
                .statusDesc("已发货")
                .createTime(LocalDateTime.now().minusDays(3))
                .payTime(LocalDateTime.now().minusDays(3))
                .receiverAddress("北京市朝阳区建国路88号")
                .reviewed(false)
                .build());

        // ORD003 - 待发货
        ORDER_TABLE.put("ORD003", Order.builder()
                .orderId("ORD003")
                .userId("U10002")
                .userName("李晓红")
                .productName("小米平板 7 Pro")
                .productSpec("12GB+256GB 深空灰")
                .amount(new BigDecimal("2999.00"))
                .status("PENDING_SHIP")
                .statusDesc("待发货")
                .createTime(LocalDateTime.now().minusDays(1))
                .payTime(LocalDateTime.now().minusDays(1))
                .receiverAddress("上海市浦东新区陆家嘴路100号")
                .reviewed(false)
                .build());

        // ORD004 - 待付款
        ORDER_TABLE.put("ORD004", Order.builder()
                .orderId("ORD004")
                .userId("U10003")
                .userName("王大力")
                .productName("Sony WH-1000XM6 头戴耳机")
                .productSpec("黑色")
                .amount(new BigDecimal("2499.00"))
                .status("PENDING_PAY")
                .statusDesc("待付款")
                .createTime(LocalDateTime.now().minusHours(2))
                .payTime(null)
                .receiverAddress("广州市天河区珠江新城100号")
                .reviewed(false)
                .build());

        // ORD005 - 退款中
        ORDER_TABLE.put("ORD005", Order.builder()
                .orderId("ORD005")
                .userId("U10002")
                .userName("李晓红")
                .productName("戴森 V15 吸尘器")
                .productSpec("金色限定版")
                .amount(new BigDecimal("4990.00"))
                .status("REFUNDING")
                .statusDesc("退款中")
                .createTime(LocalDateTime.now().minusDays(7))
                .payTime(LocalDateTime.now().minusDays(7))
                .receiverAddress("上海市浦东新区陆家嘴路100号")
                .reviewed(false)
                .build());
    }

    private static void initLogistics() {
        // ORD001 物流 - 已签收
        LOGISTICS_TABLE.put("ORD001", Logistics.builder()
                .orderId("ORD001")
                .trackingNo("SF1234567890")
                .carrier("顺丰速运")
                .carrierPhone("95338")
                .status("DELIVERED")
                .statusDesc("已签收")
                .estimatedDelivery("已签收")
                .events(List.of(
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(10))
                                .location("北京市朝阳区建国路88号")
                                .description("快件已由本人签收，感谢使用顺丰")
                                .build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(10).minusHours(2))
                                .location("北京朝阳区配送站")
                                .description("快件正在派送中，派送员：王师傅，电话：138****8888")
                                .build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(11))
                                .location("北京顺丰转运中心")
                                .description("快件已到达北京顺丰转运中心")
                                .build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(12))
                                .location("上海浦东顺丰转运中心")
                                .description("快件已从上海发出，正在运往北京")
                                .build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(13))
                                .location("上海顺丰仓储中心")
                                .description("商家已打包完毕，顺丰已揽收")
                                .build()
                ))
                .build());

        // ORD002 物流 - 派送中
        LOGISTICS_TABLE.put("ORD002", Logistics.builder()
                .orderId("ORD002")
                .trackingNo("JD9876543210")
                .carrier("京东物流")
                .carrierPhone("950616")
                .status("OUT_FOR_DELIVERY")
                .statusDesc("派送中")
                .estimatedDelivery("预计今天18:00前送达")
                .events(List.of(
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusHours(3))
                                .location("北京朝阳区京东配送站")
                                .description("快件已由京东快递员李师傅揽收，正在派送，电话：139****6666")
                                .build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(1))
                                .location("北京京东亚洲一号仓储中心")
                                .description("快件已到达北京分拣中心")
                                .build(),
                        LogisticsEvent.builder()
                                .eventTime(LocalDateTime.now().minusDays(2))
                                .location("武汉京东仓储中心")
                                .description("商品出库，开始运输")
                                .build()
                ))
                .build());

        // ORD003 待发货 - 无物流信息
        // ORD004、ORD005 同上
    }
}

package com.example.mallcs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（Mock 阶段用内存数据，生产切换为 JPA/MyBatis 查询）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /** 订单号 */
    private String orderId;

    /** 用户 ID */
    private String userId;

    /** 用户昵称 */
    private String userName;

    /** 商品名称 */
    private String productName;

    /** 商品规格 */
    private String productSpec;

    /** 实付金额（元）*/
    private BigDecimal amount;

    /**
     * 订单状态：
     * PENDING_PAY - 待付款
     * PENDING_SHIP - 待发货
     * SHIPPED - 已发货
     * COMPLETED - 已完成
     * CANCELLED - 已取消
     * REFUNDING - 退款中
     */
    private String status;

    /** 状态中文描述 */
    private String statusDesc;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 付款时间 */
    private LocalDateTime payTime;

    /** 收货地址 */
    private String receiverAddress;

    /** 是否已评价 */
    private boolean reviewed;
}

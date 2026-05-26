package com.example.mallcs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 物流信息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Logistics {

    /** 关联订单号 */
    private String orderId;

    /** 快递单号 */
    private String trackingNo;

    /** 快递公司 */
    private String carrier;

    /** 快递公司电话 */
    private String carrierPhone;

    /**
     * 物流状态：
     * WAIT_COLLECT - 待揽收
     * IN_TRANSIT - 运输中
     * OUT_FOR_DELIVERY - 派送中
     * DELIVERED - 已签收
     * EXCEPTION - 异常
     */
    private String status;

    /** 状态中文描述 */
    private String statusDesc;

    /** 预计送达时间描述 */
    private String estimatedDelivery;

    /** 物流轨迹（时间倒序） */
    private List<LogisticsEvent> events;
}

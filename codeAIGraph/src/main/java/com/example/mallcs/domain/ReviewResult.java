package com.example.mallcs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评价结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResult {

    /** 是否成功 */
    private boolean success;

    /** 关联订单号 */
    private String orderId;

    /** 评价 ID */
    private String reviewId;

    /** 评分（1-5 星）*/
    private int rating;

    /** 评价内容 */
    private String content;

    /** 结果消息 */
    private String message;
}

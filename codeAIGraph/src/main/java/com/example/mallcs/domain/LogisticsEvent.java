package com.example.mallcs.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物流轨迹节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsEvent {

    /** 事件时间 */
    private LocalDateTime eventTime;

    /** 事件地点 */
    private String location;

    /** 事件描述 */
    private String description;
}

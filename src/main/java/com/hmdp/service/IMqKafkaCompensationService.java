package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.MqKafkaLog;

import java.util.List;

/**
 * Kafka 补偿：对照业务表与审计日志，支持秒杀订单安全重投
 */
public interface IMqKafkaCompensationService {

    /**
     * 若订单已落库则拒绝重投，避免重复写；否则按与秒杀接口相同方式发 Kafka。
     */
    Result republishVoucherOrder(Long orderId, Long userId, Long voucherId);

    List<MqKafkaLog> listRecentFailedLogs(int limit);
}

package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.MqKafkaLog;

/**
 * Kafka 审计落库：发送失败、消费失败、DLT 归档
 */
public interface IMqKafkaLogService extends IService<MqKafkaLog> {

    void logSendSuccess(String msgId, String bizType, String bizKey, String topic, Integer partition, long offset);

    void logSendFailed(String msgId, String bizType, String bizKey, String topic, String errorMsg);

    void logConsumeFailed(String msgId, String bizType, String bizKey, String topic, Integer partition,
                          Long offset, String errorMsg);

    void logDlt(String msgId, String bizType, String bizKey, String topic, String errorMsg);
}

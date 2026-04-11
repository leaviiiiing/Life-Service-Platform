package com.hmdp.mq.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;

/**
 * Kafka 监听线程写入 MDC，便于日志关联 msgId / 位点（与 HTTP traceId 区分线程，各自独立）
 */
public final class KafkaMdcHelper {

    private KafkaMdcHelper() {
    }

    public static void put(ConsumerRecord<?, ?> record, String msgId) {
        put(record, msgId, "", "");
    }

    /**
     * @param idempotentKey 与计划书 idempotentKey 一致；空串则日志里不占位
     * @param retryCount    与计划书 retryCount 一致
     */
    public static void put(ConsumerRecord<?, ?> record, String msgId, String idempotentKey, String retryCount) {
        MDC.put("msgId", msgId != null ? msgId : "");
        MDC.put("idempotentKey", idempotentKey != null ? idempotentKey : "");
        MDC.put("retryCount", retryCount != null ? retryCount : "");
        MDC.put("kafkaTopic", record.topic());
        MDC.put("kafkaPartition", Integer.toString(record.partition()));
        MDC.put("kafkaOffset", Long.toString(record.offset()));
    }

    public static void clear() {
        MDC.remove("msgId");
        MDC.remove("idempotentKey");
        MDC.remove("retryCount");
        MDC.remove("kafkaTopic");
        MDC.remove("kafkaPartition");
        MDC.remove("kafkaOffset");
    }
}

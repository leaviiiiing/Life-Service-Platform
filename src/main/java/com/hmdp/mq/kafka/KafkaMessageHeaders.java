package com.hmdp.mq.kafka;

import java.nio.charset.StandardCharsets;

/**
 * Kafka 自定义头：与生产者、消费者约定一致
 */
public final class KafkaMessageHeaders {

    public static final String MSG_ID = "MSG_ID";

    /** 跨重试不变，消费幂等 Redis 键用该值（与计划书 idempotentKey 对齐） */
    public static final String IDEMPOTENT_KEY = "IDEMPOTENT_KEY";

    /** 当前重试代数，0 表示首次进入主 Topic */
    public static final String RETRY_COUNT = "RETRY_COUNT";

    private KafkaMessageHeaders() {
    }

    public static byte[] msgIdBytes(String msgId) {
        return msgId.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}

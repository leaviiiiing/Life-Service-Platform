package com.hmdp.mq.kafka;

import java.nio.charset.StandardCharsets;

/**
 * Kafka 自定义头：与生产者、消费者约定一致
 */
public final class KafkaMessageHeaders {

    public static final String MSG_ID = "MSG_ID";

    private KafkaMessageHeaders() {
    }

    public static byte[] msgIdBytes(String msgId) {
        return msgId.getBytes(StandardCharsets.UTF_8);
    }
}

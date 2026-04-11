package com.hmdp.mq.kafka;

/**
 * Kafka Topic 与消息命名约定（与 docs/MQ_KAFKA_IMPLEMENTATION_REPORT.md 第一节一致）
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /**
     * 探店笔记粉丝 Feed（原 blog.feed.topic，与 {@link com.hmdp.config.KafkaConfig} 中 Bean 名一致）
     */
    public static final String BLOG_FEED = "blog.feed.topic";

    /**
     * 秒杀异步创建订单（分区键：userId）
     */
    public static final String VOUCHER_ORDER = "voucher.order.topic";

    /**
     * 秒杀订单消费失败后的死信 Topic（仅投递失败样本，便于人工排查）
     */
    public static final String VOUCHER_ORDER_DLT = "voucher.order.dlt";

    /**
     * 可选：重试 Topic（当前实现中可与 DLT 策略二选一扩展，占位保留命名）
     */
    public static final String VOUCHER_ORDER_RETRY = "voucher.order.retry";
}

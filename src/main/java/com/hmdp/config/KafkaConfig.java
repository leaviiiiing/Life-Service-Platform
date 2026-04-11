package com.hmdp.config;

import com.hmdp.mq.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    /** 与 {@link KafkaTopics#BLOG_FEED} 一致，供原有代码引用 */
    public static final String BLOG_FEED_TOPIC = KafkaTopics.BLOG_FEED;

    @Bean
    public NewTopic blogFeedTopic() {
        return TopicBuilder.name(KafkaTopics.BLOG_FEED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic voucherOrderTopic() {
        return TopicBuilder.name(KafkaTopics.VOUCHER_ORDER)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic voucherOrderDltTopic() {
        return TopicBuilder.name(KafkaTopics.VOUCHER_ORDER_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic voucherOrderRetryTopic() {
        return TopicBuilder.name(KafkaTopics.VOUCHER_ORDER_RETRY)
                .partitions(1)
                .replicas(1)
                .build();
    }
}

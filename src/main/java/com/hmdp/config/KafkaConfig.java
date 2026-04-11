package com.hmdp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String BLOG_FEED_TOPIC = "blog.feed.topic";

    @Bean
    public NewTopic blogFeedTopic() {
        return TopicBuilder.name(BLOG_FEED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String BLOG_FEED_EXCHANGE = "blog.feed.exchange";
    public static final String BLOG_FEED_QUEUE = "blog.feed.queue";
    public static final String BLOG_FEED_ROUTING_KEY = "blog.feed.push";

    @Bean
    public DirectExchange blogFeedExchange() {
        return new DirectExchange(BLOG_FEED_EXCHANGE, true, false);
    }

    @Bean
    public Queue blogFeedQueue() {
        return QueueBuilder.durable(BLOG_FEED_QUEUE).build();
    }

    @Bean
    public Binding blogFeedBinding() {
        return BindingBuilder.bind(blogFeedQueue()).to(blogFeedExchange()).with(BLOG_FEED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

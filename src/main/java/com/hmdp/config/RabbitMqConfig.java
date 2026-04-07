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
    public static final String VOUCHER_ORDER_EXCHANGE = "voucher.order.exchange";
    public static final String VOUCHER_ORDER_QUEUE = "voucher.order.queue";
    public static final String VOUCHER_ORDER_ROUTING_KEY = "voucher.order.create";

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
    public DirectExchange voucherOrderExchange() {
        return new DirectExchange(VOUCHER_ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue voucherOrderQueue() {
        return QueueBuilder.durable(VOUCHER_ORDER_QUEUE).build();
    }

    @Bean
    public Binding voucherOrderBinding() {
        return BindingBuilder.bind(voucherOrderQueue()).to(voucherOrderExchange()).with(VOUCHER_ORDER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
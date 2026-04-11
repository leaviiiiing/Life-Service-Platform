package com.hmdp.utils;

import com.hmdp.config.KafkaConfig;
import com.hmdp.dto.BlogFeedMessage;
import com.hmdp.entity.Follow;
import com.hmdp.mq.kafka.KafkaConsumeIdempotencyService;
import com.hmdp.mq.kafka.KafkaMdcHelper;
import com.hmdp.mq.kafka.KafkaMessageHeaders;
import com.hmdp.service.IFollowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Slf4j
@Component
public class BlogFeedConsumer {

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KafkaConsumeIdempotencyService kafkaConsumeIdempotencyService;

    @KafkaListener(
            topics = KafkaConfig.BLOG_FEED_TOPIC,
            groupId = "blog-feed-consumer-group",
            containerFactory = "manualKafkaListenerContainerFactory"
    )
    public void listenBlogFeed(ConsumerRecord<String, BlogFeedMessage> record, Acknowledgment ack) {
        BlogFeedMessage message = record.value();
        String msgId = extractMsgId(record);
        if (msgId == null) {
            msgId = KafkaConsumeIdempotencyService.fallbackMsgId(record.topic(), record.partition(), record.offset());
        }
        String idempotentKey = extractIdempotentKey(record);
        if (idempotentKey == null) {
            idempotentKey = msgId;
        }
        KafkaMdcHelper.put(record, msgId, idempotentKey, "");
        try {
            if (message == null) {
                ack.acknowledge();
                return;
            }
            if (kafkaConsumeIdempotencyService.alreadyProcessed(idempotentKey)) {
                ack.acknowledge();
                return;
            }
            List<Follow> followList = followService.lambdaQuery().eq(Follow::getFollowUserId, message.getUserId()).list();
            for (Follow follow : followList) {
                Long id = follow.getUserId();
                String key = FEED_KEY + id;
                stringRedisTemplate.opsForZSet().add(key, message.getBlogId().toString(), message.getTimestamp());
            }
            kafkaConsumeIdempotencyService.markProcessed(idempotentKey);
            ack.acknowledge();
        } catch (Exception e) {
            // 坏消息先 ack 避免堵分区；生产可配合 DLT（本阶段仅打日志）
            log.error("Feed 消费异常 blogId={}", message != null ? message.getBlogId() : null, e);
            ack.acknowledge();
        } finally {
            KafkaMdcHelper.clear();
        }
    }

    private static String extractMsgId(ConsumerRecord<String, BlogFeedMessage> record) {
        if (record.headers().lastHeader(KafkaMessageHeaders.MSG_ID) == null) {
            return null;
        }
        return new String(record.headers().lastHeader(KafkaMessageHeaders.MSG_ID).value(), StandardCharsets.UTF_8);
    }

    private static String extractIdempotentKey(ConsumerRecord<String, BlogFeedMessage> record) {
        if (record.headers().lastHeader(KafkaMessageHeaders.IDEMPOTENT_KEY) == null) {
            return null;
        }
        return new String(record.headers().lastHeader(KafkaMessageHeaders.IDEMPOTENT_KEY).value(), StandardCharsets.UTF_8);
    }
}

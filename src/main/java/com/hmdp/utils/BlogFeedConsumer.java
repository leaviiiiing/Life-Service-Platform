package com.hmdp.utils;

import com.hmdp.config.KafkaConfig;
import com.hmdp.dto.BlogFeedMessage;
import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Slf4j
@Component
public class BlogFeedConsumer {

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @KafkaListener(
            topics = KafkaConfig.BLOG_FEED_TOPIC,
            groupId = "blog-feed-consumer-group",
            containerFactory = "manualKafkaListenerContainerFactory"
    )
    public void listenBlogFeed(BlogFeedMessage message, Acknowledgment ack) {
        try {
            List<Follow> followList = followService.lambdaQuery().eq(Follow::getFollowUserId, message.getUserId()).list();
            for (Follow follow : followList) {
                Long id = follow.getUserId();
                String key = FEED_KEY + id;
                stringRedisTemplate.opsForZSet().add(key, message.getBlogId().toString(), message.getTimestamp());
            }
            ack.acknowledge();
        } catch (Exception e) {
            // 坏消息先 ack 避免堵分区；生产可配合 DLT（本阶段仅打日志）
            log.error("Feed 消费异常 blogId={}", message != null ? message.getBlogId() : null, e);
            ack.acknowledge();
        }
    }
}

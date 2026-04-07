package com.hmdp.utils;

import com.hmdp.config.RabbitMqConfig;
import com.hmdp.dto.BlogFeedMessage;
import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Component
public class BlogFeedConsumer {

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMqConfig.BLOG_FEED_QUEUE)
    public void listenBlogFeed(BlogFeedMessage message) {
        List<Follow> followList = followService.lambdaQuery().eq(Follow::getFollowUserId, message.getUserId()).list();
        for (Follow follow : followList) {
            Long id = follow.getUserId();
            String key = FEED_KEY + id;
            stringRedisTemplate.opsForZSet().add(key, message.getBlogId().toString(), message.getTimestamp());
        }
    }
}

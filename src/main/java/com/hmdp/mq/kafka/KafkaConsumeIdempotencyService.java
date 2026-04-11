package com.hmdp.mq.kafka;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 消费幂等：业务成功后写入 Redis；重复投递时先查键，避免重复写（失败不落键，可重试）
 */
@Service
public class KafkaConsumeIdempotencyService {

    private static final String PREFIX = "mq:kafka:consumed:";

    /** 与消息重试窗口匹配，略大于典型业务周期即可 */
    private static final long TTL_DAYS = 7L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean alreadyProcessed(String msgId) {
        if (StrUtil.isBlank(msgId)) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(PREFIX + msgId));
    }

    public void markProcessed(String msgId) {
        if (StrUtil.isBlank(msgId)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(PREFIX + msgId, "1", TTL_DAYS, TimeUnit.DAYS);
    }

    public static String fallbackMsgId(String topic, int partition, long offset) {
        return topic + ":" + partition + ":" + offset;
    }
}

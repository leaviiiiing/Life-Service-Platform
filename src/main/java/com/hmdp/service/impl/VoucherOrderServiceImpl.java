package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.mq.kafka.KafkaMessageHeaders;
import com.hmdp.mq.kafka.KafkaTopics;
import com.hmdp.service.MqKafkaLogService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_IDEM_KEY;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final String BIZ_VOUCHER = "VOUCHER_ORDER";

    @Resource
    private ISeckillVoucherService seckillVoucherService ;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Resource
    private MqKafkaLogService mqKafkaLogService;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //获取代理对象
    @Resource
    @Lazy
    private IVoucherOrderService proxy;
    public void handleVoucherOrder(VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:"+userId,stringRedisTemplate);
        RLock lock =  redissonClient.getLock("lock:order:"+userId);
        //获取锁
        boolean isLock = lock.tryLock();
        if(!isLock){
            log.error( "不允许重复下单！");
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        //一人一单
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = Math.toIntExact(query()
                .eq("user_id", userId).eq("voucher_id", voucherId).count());
        if(count>0){
            log.error("该用户已经购买过一次！");
            return;
        }

        //扣减库存
        boolean success=seckillVoucherService.update().
                setSql("stock = stock - 1 ")
                .eq("voucher_id", voucherId).gt("stock",0)//where
                .update();
        if (!success){
            log.error("库存不足！");
            return;
        }

        //创建订单
        save(voucherOrder);

    }

    @Override
    public Result seckillVoucher(Long voucherId, String idempotencyKey) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        // HTTP 幂等：相同 Idempotency-Key 返回已受理的 orderId（与计划书「重复请求稳定结果」一致）
        if (StrUtil.isNotBlank(idempotencyKey)) {
            String idemRedisKey = SECKILL_IDEM_KEY + userId + ":" + voucherId + ":" + DigestUtil.md5Hex(idempotencyKey);
            String cached = stringRedisTemplate.opsForValue().get(idemRedisKey);
            if (StrUtil.isNotBlank(cached)) {
                try {
                    return Result.ok(Long.parseLong(cached));
                } catch (NumberFormatException e) {
                    log.warn("幂等键缓存非数字 key={}", idemRedisKey);
                }
            }
        }
        //获取订单ID
        long orderId = redisIdWorker.nextId("order");
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),userId.toString(),String.valueOf(orderId)
        );
        int r = result.intValue();
        if(result!=0){
            return Result.fail(result==1?"库存不足！":"无法重复下单！");
        }

        //发送消息到 Kafka（原 RabbitMQ 队列已移除）
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        String msgId = BIZ_VOUCHER + ":" + orderId + ":" + UUID.randomUUID().toString().replace("-", "");
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                KafkaTopics.VOUCHER_ORDER,
                String.valueOf(userId),
                voucherOrder
        );
        record.headers().add(KafkaMessageHeaders.MSG_ID, KafkaMessageHeaders.msgIdBytes(msgId));
        record.headers().add(KafkaMessageHeaders.IDEMPOTENT_KEY, KafkaMessageHeaders.msgIdBytes(msgId));
        kafkaTemplate.send(record).addCallback(
                result -> { /* 成功不落库，失败与消费侧见 MqKafkaLog */ },
                ex -> mqKafkaLogService.logSendFailed(
                        msgId,
                        BIZ_VOUCHER,
                        String.valueOf(orderId),
                        KafkaTopics.VOUCHER_ORDER,
                        ex != null ? ex.getMessage() : "send failed"
                )
        );

        if (StrUtil.isNotBlank(idempotencyKey)) {
            String idemRedisKey = SECKILL_IDEM_KEY + userId + ":" + voucherId + ":" + DigestUtil.md5Hex(idempotencyKey);
            stringRedisTemplate.opsForValue().set(idemRedisKey, String.valueOf(orderId), 24, TimeUnit.HOURS);
        }

        return Result.ok(orderId);
    }

}

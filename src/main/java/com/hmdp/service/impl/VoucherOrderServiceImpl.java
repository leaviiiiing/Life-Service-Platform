package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.config.RabbitMqConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService ;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private  final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    
    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";

        @Override
        public void run() {
            while (true){
                try {
                    //获取队列信息 XREADGROUP
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断获取消息是否成功
                    if (list==null || list.isEmpty()){
                        //获取失败，没有消息，进行下一次循环
                        continue;
                    }
                    //解析消息中的订单消息
                    MapRecord<String, Object, Object> message = list.get(0);
                    Map<Object, Object> value = message.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);
                    //获取成功，创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认 SACK streams.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",message.getId());
                    
                } catch (Exception e) {

                    log.error("处理订单异常",e);
                    handlePendingList();
                }

            }

        }

        private void handlePendingList() {
            while (true){
                try {
                    //获取队列信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS streams.order 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断获取消息是否成功
                    if (list==null || list.isEmpty()){
                        //获取失败，pending-list没有消息，结束循环
                        break;
                    }
                    //解析消息中的订单消息
                    MapRecord<String, Object, Object> message = list.get(0);
                    Map<Object, Object> value = message.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);
                    //获取成功，创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认 SACK streams.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",message.getId());

                } catch (Exception e) {

                    log.error("处理pending-list订单异常",e);

                }

            }
        }
    }

/*
    private final BlockingQueue<VoucherOrder> voucherOrderQueue = new ArrayBlockingQueue<>(1024*1024);
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                //获取队列信息
                try {
                    VoucherOrder voucherOrder = voucherOrderQueue.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }

            }

        }
    }
*/
    //获取代理对象
    @Resource
    @Lazy
    private IVoucherOrderService proxy;
    private void handleVoucherOrder(VoucherOrder voucherOrder){
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
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
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

        //获取代理对象
        //proxy= (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

}


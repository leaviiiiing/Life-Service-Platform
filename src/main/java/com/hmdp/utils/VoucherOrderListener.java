package com.hmdp.utils;

import com.hmdp.config.RabbitMqConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class VoucherOrderListener {

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @RabbitListener(queues = RabbitMqConfig.VOUCHER_ORDER_QUEUE)
    public void listenVoucherOrderMessage(VoucherOrder voucherOrder) {
        try {
            //获取队列信息 XREADGROUP
            //改为RabbitMQ监听队列消息
            //判断获取消息是否成功
            if (voucherOrder == null){
                //获取失败，没有消息，直接返回
                return;
            }
            //解析消息中的订单消息
            //获取成功，创建订单
            voucherOrderService.handleVoucherOrder(voucherOrder);
            //ACK确认 SACK streams.order g1 id
            //RabbitMQ默认自动ACK

        } catch (Exception e) {

            log.error("处理订单异常",e);
            throw e;
        }
    }
}

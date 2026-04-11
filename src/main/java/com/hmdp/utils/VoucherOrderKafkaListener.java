package com.hmdp.utils;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mq.kafka.KafkaConsumeIdempotencyService;
import com.hmdp.mq.kafka.KafkaMdcHelper;
import com.hmdp.mq.kafka.KafkaMessageHeaders;
import com.hmdp.mq.kafka.KafkaTopics;
import com.hmdp.service.MqKafkaLogService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀订单异步创建：Kafka 消费，手动 ack；失败写入 DLT Topic 并落库
 * （原 RabbitMQ 队列监听已迁移为本类）
 */
@Slf4j
@Component
public class VoucherOrderKafkaListener {

    private static final String BIZ = "VOUCHER_ORDER";

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Resource
    private MqKafkaLogService mqKafkaLogService;

    @Resource
    private KafkaConsumeIdempotencyService kafkaConsumeIdempotencyService;

    @KafkaListener(
            topics = KafkaTopics.VOUCHER_ORDER,
            groupId = "voucher-order-consumer-group",
            containerFactory = "manualKafkaListenerContainerFactory"
    )
    public void listenVoucherOrder(ConsumerRecord<String, VoucherOrder> record, Acknowledgment ack) {
        VoucherOrder voucherOrder = record.value();
        String msgId = extractMsgId(record);
        if (msgId == null) {
            msgId = KafkaConsumeIdempotencyService.fallbackMsgId(record.topic(), record.partition(), record.offset());
        }
        KafkaMdcHelper.put(record, msgId);
        try {
            if (voucherOrder == null) {
                ack.acknowledge();
                return;
            }
            if (kafkaConsumeIdempotencyService.alreadyProcessed(msgId)) {
                log.debug("跳过重复消息 msgId={}", msgId);
                ack.acknowledge();
                return;
            }
            // 创建订单（内部一人一单与库存）
            voucherOrderService.handleVoucherOrder(voucherOrder);
            kafkaConsumeIdempotencyService.markProcessed(msgId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Kafka 消费秒杀订单异常 msgId={}", msgId, e);
            mqKafkaLogService.logConsumeFailed(
                    msgId != null ? msgId : "unknown",
                    BIZ,
                    voucherOrder != null ? String.valueOf(voucherOrder.getId()) : null,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    e.getMessage()
            );
            try {
                // 转入死信 Topic，避免无限重试；业务侧按日志补偿
                kafkaTemplate.send(KafkaTopics.VOUCHER_ORDER_DLT, record.key(), voucherOrder);
                mqKafkaLogService.logDlt(msgId != null ? msgId : "unknown", BIZ,
                        voucherOrder != null ? String.valueOf(voucherOrder.getId()) : null,
                        KafkaTopics.VOUCHER_ORDER_DLT, e.getMessage());
            } catch (Exception ex) {
                log.error("发送 DLT 失败", ex);
            }
            ack.acknowledge();
        } finally {
            KafkaMdcHelper.clear();
        }
    }

    private static String extractMsgId(ConsumerRecord<String, VoucherOrder> record) {
        if (record.headers().lastHeader(KafkaMessageHeaders.MSG_ID) == null) {
            return null;
        }
        return new String(record.headers().lastHeader(KafkaMessageHeaders.MSG_ID).value(), StandardCharsets.UTF_8);
    }
}

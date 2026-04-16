package com.hmdp.utils;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mq.kafka.KafkaConsumeIdempotencyService;
import com.hmdp.mq.kafka.KafkaMdcHelper;
import com.hmdp.mq.kafka.KafkaMessageHeaders;
import com.hmdp.mq.kafka.KafkaTopics;
import com.hmdp.service.IMqKafkaLogService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀订单异步创建：Kafka 消费，手动 ack；先经重试 Topic 再 DLT（原 RabbitMQ 队列已迁移为本类）
 */
@Slf4j
@Component
public class VoucherOrderKafkaListener {

    private static final String BIZ = "VOUCHER_ORDER";

    /** 当前重试头为 0、1 时失败仍会发重试 Topic；达到 2 仍失败则 DLT */
    private static final int MAX_RETRY_BEFORE_DLT = 2;

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Resource
    private IMqKafkaLogService mqKafkaLogService;

    @Resource
    private KafkaConsumeIdempotencyService kafkaConsumeIdempotencyService;

    @KafkaListener(
            topics = {KafkaTopics.VOUCHER_ORDER, KafkaTopics.VOUCHER_ORDER_RETRY},
            groupId = "voucher-order-consumer-group",
            containerFactory = "manualKafkaListenerContainerFactory"
    )
    public void listenVoucherOrder(ConsumerRecord<String, VoucherOrder> record, Acknowledgment ack) {
        VoucherOrder voucherOrder = record.value();
        String msgId = extractMsgId(record);
        if (msgId == null) {
            msgId = KafkaConsumeIdempotencyService.fallbackMsgId(record.topic(), record.partition(), record.offset());
        }
        String idempotentKey = extractIdempotentKey(record);
        if (idempotentKey == null) {
            idempotentKey = msgId;
        }
        int retryCount = extractRetryCount(record);
        KafkaMdcHelper.put(record, msgId, idempotentKey, Integer.toString(retryCount));
        try {
            if (voucherOrder == null) {
                ack.acknowledge();
                return;
            }
            if (kafkaConsumeIdempotencyService.alreadyProcessed(idempotentKey)) {
                log.debug("跳过重复消息 idempotentKey={} msgId={}", idempotentKey, msgId);
                ack.acknowledge();
                return;
            }
            voucherOrderService.handleVoucherOrder(voucherOrder);
            kafkaConsumeIdempotencyService.markProcessed(idempotentKey);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Kafka 消费秒杀订单异常 msgId={} retryCount={}", msgId, retryCount, e);
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
                if (retryCount < MAX_RETRY_BEFORE_DLT) {
                    int next = retryCount + 1;
                    String newMsgId = idempotentKey + ":r" + next;
                    ProducerRecord<String, Object> retryRecord = new ProducerRecord<>(
                            KafkaTopics.VOUCHER_ORDER_RETRY,
                            record.key(),
                            voucherOrder
                    );
                    retryRecord.headers().add(KafkaMessageHeaders.MSG_ID, KafkaMessageHeaders.msgIdBytes(newMsgId));
                    retryRecord.headers().add(KafkaMessageHeaders.IDEMPOTENT_KEY, KafkaMessageHeaders.msgIdBytes(idempotentKey));
                    retryRecord.headers().add(KafkaMessageHeaders.RETRY_COUNT, KafkaMessageHeaders.utf8(Integer.toString(next)));
                    kafkaTemplate.send(retryRecord);
                    log.warn("秒杀订单转入重试 Topic retryCount={} newMsgId={}", next, newMsgId);
                } else {
                    kafkaTemplate.send(KafkaTopics.VOUCHER_ORDER_DLT, record.key(), voucherOrder);
                    mqKafkaLogService.logDlt(msgId != null ? msgId : "unknown", BIZ,
                            voucherOrder != null ? String.valueOf(voucherOrder.getId()) : null,
                            KafkaTopics.VOUCHER_ORDER_DLT, e.getMessage());
                }
            } catch (Exception ex) {
                log.error("发送重试/DLT 失败", ex);
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

    private static String extractIdempotentKey(ConsumerRecord<String, VoucherOrder> record) {
        if (record.headers().lastHeader(KafkaMessageHeaders.IDEMPOTENT_KEY) == null) {
            return null;
        }
        return new String(record.headers().lastHeader(KafkaMessageHeaders.IDEMPOTENT_KEY).value(), StandardCharsets.UTF_8);
    }

    private static int extractRetryCount(ConsumerRecord<String, VoucherOrder> record) {
        if (record.headers().lastHeader(KafkaMessageHeaders.RETRY_COUNT) == null) {
            return 0;
        }
        try {
            return Integer.parseInt(new String(
                    record.headers().lastHeader(KafkaMessageHeaders.RETRY_COUNT).value(),
                    StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

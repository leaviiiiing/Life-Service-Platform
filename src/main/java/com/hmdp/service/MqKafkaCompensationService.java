package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.MqKafkaLog;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mq.kafka.KafkaMessageHeaders;
import com.hmdp.mq.kafka.KafkaTopics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

/**
 * Kafka 补偿：对照业务表与审计日志，支持秒杀订单安全重投（幂等由后续 todo 与业务唯一约束兜底）
 */
@Service
public class MqKafkaCompensationService {

    private static final String BIZ_VOUCHER = "VOUCHER_ORDER";

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Resource
    private MqKafkaLogService mqKafkaLogService;

    /**
     * 若订单已落库则拒绝重投，避免重复写；否则按与秒杀接口相同方式发 Kafka。
     */
    public Result republishVoucherOrder(Long orderId, Long userId, Long voucherId) {
        if (orderId == null || userId == null || voucherId == null) {
            return Result.fail("orderId、userId、voucherId 不能为空");
        }
        VoucherOrder existing = voucherOrderService.getById(orderId);
        if (existing != null) {
            return Result.fail("订单已存在，无需重投");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        String msgId = BIZ_VOUCHER + ":" + orderId + ":" + UUID.randomUUID().toString().replace("-", "")
                + ":comp";
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                KafkaTopics.VOUCHER_ORDER,
                String.valueOf(userId),
                voucherOrder
        );
        record.headers().add(KafkaMessageHeaders.MSG_ID, KafkaMessageHeaders.msgIdBytes(msgId));
        kafkaTemplate.send(record).addCallback(
                r -> { },
                ex -> mqKafkaLogService.logSendFailed(
                        msgId,
                        BIZ_VOUCHER,
                        String.valueOf(orderId),
                        KafkaTopics.VOUCHER_ORDER,
                        ex != null ? ex.getMessage() : "compensation send failed"
                )
        );
        return Result.ok("已发起重投，msgId=" + msgId);
    }

    public List<MqKafkaLog> listRecentFailedLogs(int limit) {
        int n = Math.min(Math.max(limit, 1), 200);
        return mqKafkaLogService.lambdaQuery()
                .eq(MqKafkaLog::getStatus, "FAILED")
                .orderByDesc(MqKafkaLog::getCreateTime)
                .last("LIMIT " + n)
                .list();
    }
}

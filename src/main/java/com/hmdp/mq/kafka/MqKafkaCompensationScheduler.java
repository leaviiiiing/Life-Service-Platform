package com.hmdp.mq.kafka;

import com.hmdp.entity.MqKafkaLog;
import com.hmdp.service.MqKafkaLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 定时巡检：消费/DLT 失败审计条数，便于与监控或人工补偿流程衔接（不做自动重投，避免误伤）
 */
@Slf4j
@Component
public class MqKafkaCompensationScheduler {

    @Resource
    private MqKafkaLogService mqKafkaLogService;

    @Scheduled(fixedDelayString = "${mq.compensation.scan-ms:300000}")
    public void logFailedAuditCount() {
        long consumeFailed = mqKafkaLogService.lambdaQuery()
                .eq(MqKafkaLog::getDirection, "CONSUME")
                .eq(MqKafkaLog::getStatus, "FAILED")
                .count();
        long dlt = mqKafkaLogService.lambdaQuery()
                .eq(MqKafkaLog::getDirection, "DLT")
                .count();
        if (consumeFailed > 0 || dlt > 0) {
            log.warn("[MQ补偿巡检] 审计表消费失败条数={} DLT条数={}（详见 tb_mq_kafka_log）", consumeFailed, dlt);
        }
    }
}

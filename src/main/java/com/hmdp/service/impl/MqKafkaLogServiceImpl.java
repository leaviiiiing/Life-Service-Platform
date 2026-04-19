package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.MqKafkaLog;
import com.hmdp.mapper.MqKafkaLogMapper;
import com.hmdp.service.IMqKafkaLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Kafka 审计落库：发送失败、消费失败、DLT 归档
 */
@Slf4j
@Service
public class MqKafkaLogServiceImpl extends ServiceImpl<MqKafkaLogMapper, MqKafkaLog> implements IMqKafkaLogService {

    @Override
    public void logSendSuccess(String msgId, String bizType, String bizKey, String topic, Integer partition, long offset) {
        MqKafkaLog row = new MqKafkaLog();
        row.setMsgId(msgId);
        row.setBizType(bizType);
        row.setBizKey(bizKey);
        row.setTopic(topic);
        row.setPartitionId(partition);
        row.setOffsetVal(offset);
        row.setDirection("SEND");
        row.setStatus("SUCCESS");
        row.setCreateTime(LocalDateTime.now());
        save(row);
    }

    @Override
    public void logSendFailed(String msgId, String bizType, String bizKey, String topic, String errorMsg) {
        MqKafkaLog row = new MqKafkaLog();
        row.setMsgId(msgId);
        row.setBizType(bizType);
        row.setBizKey(bizKey);
        row.setTopic(topic);
        row.setDirection("SEND");
        row.setStatus("FAILED");
        row.setErrorMsg(truncate(errorMsg));
        row.setCreateTime(LocalDateTime.now());
        save(row);
    }

    @Override
    public void logConsumeFailed(String msgId, String bizType, String bizKey, String topic, Integer partition,
                                 Long offset, String errorMsg) {
        MqKafkaLog row = new MqKafkaLog();
        row.setMsgId(msgId);
        row.setBizType(bizType);
        row.setBizKey(bizKey);
        row.setTopic(topic);
        row.setPartitionId(partition);
        row.setOffsetVal(offset);
        row.setDirection("CONSUME");
        row.setStatus("FAILED");
        row.setErrorMsg(truncate(errorMsg));
        row.setCreateTime(LocalDateTime.now());
        save(row);
    }

    @Override
    public void logDlt(String msgId, String bizType, String bizKey, String topic, String errorMsg) {
        MqKafkaLog row = new MqKafkaLog();
        row.setMsgId(msgId);
        row.setBizType(bizType);
        row.setBizKey(bizKey);
        row.setTopic(topic);
        row.setDirection("DLT");
        row.setStatus("FAILED");
        row.setErrorMsg(truncate(errorMsg));
        row.setCreateTime(LocalDateTime.now());
        save(row);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}

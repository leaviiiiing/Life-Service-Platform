package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Kafka 发送/消费/死信审计表 {@code tb_mq_kafka_log} 对应实体
 */
@Data
@TableName("tb_mq_kafka_log")
public class MqKafkaLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String msgId;

    private String bizType;

    private String bizKey;

    private String topic;

    private Integer partitionId;

    private Long offsetVal;

    /** SEND / CONSUME / DLT */
    private String direction;

    /** SUCCESS / FAILED */
    private String status;

    private String errorMsg;

    private LocalDateTime createTime;
}

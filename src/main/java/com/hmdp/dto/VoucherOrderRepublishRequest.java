package com.hmdp.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 运维补偿：按已知 orderId/userId/voucherId 重投 Kafka（用于消费失败后的安全重放）
 */
@Data
public class VoucherOrderRepublishRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private Long userId;

    private Long voucherId;
}

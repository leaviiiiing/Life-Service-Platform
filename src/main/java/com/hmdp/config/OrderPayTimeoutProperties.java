package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 未支付订单超时自动取消（与秒杀异步建单配合：订单默认未支付，超时关单并回补 DB + Redis）
 */
@Data
@ConfigurationProperties(prefix = "order.pay-timeout")
public class OrderPayTimeoutProperties {

    /** 是否启用定时关单 */
    private boolean enabled = true;

    /** 超过多少分钟未支付则取消 */
    private int minutes = 15;

    /** 每次扫描最多处理条数 */
    private int batchSize = 100;

    /** 扫描间隔（毫秒） */
    private long scanMs = 60_000L;
}

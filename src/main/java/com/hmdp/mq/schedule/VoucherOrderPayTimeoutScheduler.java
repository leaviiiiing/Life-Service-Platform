package com.hmdp.mq.schedule;

import com.hmdp.config.OrderPayTimeoutProperties;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.VoucherOrderPayCancelExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描未支付超时订单并关单（回补 MySQL + Redis，与 {@link com.hmdp.service.impl.VoucherOrderServiceImpl#createVoucherOrder} 默认未支付状态配合）
 */
@Slf4j
@Component
public class VoucherOrderPayTimeoutScheduler {

    @Resource
    private OrderPayTimeoutProperties orderPayTimeoutProperties;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private VoucherOrderPayCancelExecutor voucherOrderPayCancelExecutor;

    @Scheduled(fixedDelayString = "${order.pay-timeout.scan-ms:60000}")
    public void scanAndCancelUnpaid() {
        if (!orderPayTimeoutProperties.isEnabled()) {
            return;
        }
        int minutes = Math.max(1, orderPayTimeoutProperties.getMinutes());
        int batch = Math.min(Math.max(orderPayTimeoutProperties.getBatchSize(), 1), 500);
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);

        List<VoucherOrder> due = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getStatus, 1)
                .lt(VoucherOrder::getCreateTime, deadline)
                .last("LIMIT " + batch)
                .list();

        if (due.isEmpty()) {
            return;
        }
        log.debug("支付超时扫描：待处理未支付订单数={}（早于 {} 分钟前创建）", due.size(), minutes);

        for (VoucherOrder order : due) {
            try {
                boolean cancelled = voucherOrderPayCancelExecutor.markCancelledAndRestoreDbStock(order);
                if (cancelled) {
                    voucherOrderPayCancelExecutor.restoreRedisAfterCancel(order.getUserId(), order.getVoucherId());
                }
            } catch (Exception e) {
                log.error("支付超时关单失败 orderId={}", order.getId(), e);
            }
        }
    }
}

package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 未支付关单：DB 状态 + 秒杀库存回补；Redis 回补在事务提交后由调用方执行，避免与库不一致时难以回滚。
 */
@Slf4j
@Service
public class VoucherOrderPayCancelExecutor {

    /** tb_voucher_order.status：未支付 */
    private static final int STATUS_UNPAID = 1;
    /** 已取消 */
    private static final int STATUS_CANCELLED = 4;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将仍为「未支付」的订单关为「已取消」，并回补 MySQL 秒杀库存。
     *
     * @return true 表示本线程完成了关单与 DB 回补（调用方应继续回补 Redis）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markCancelledAndRestoreDbStock(VoucherOrder order) {
        Long orderId = order.getId();
        Long voucherId = order.getVoucherId();
        boolean updated = voucherOrderService.lambdaUpdate()
                .set(VoucherOrder::getStatus, STATUS_CANCELLED)
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getStatus, STATUS_UNPAID)
                .update();
        if (!updated) {
            return false;
        }
        boolean stockOk = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock + 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .update();
        if (!stockOk) {
            throw new IllegalStateException("回补秒杀库存失败 orderId=" + orderId + " voucherId=" + voucherId);
        }
        log.info("支付超时关单（DB）orderId={} voucherId={} userId={}", orderId, voucherId, order.getUserId());
        return true;
    }

    /**
     * 与 seckill.lua 一致：回补 Redis 库存并从「已下单用户」集合移除（须在 DB 事务已成功提交后调用）。
     */
    public void restoreRedisAfterCancel(Long userId, Long voucherId) {
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
                stringRedisTemplate.opsForValue().increment(stockKey, 1L);
            } else {
                log.warn("支付超时回补：Redis 无库存 key，跳过 INCR stockKey={}", stockKey);
            }
            Long removed = stringRedisTemplate.opsForSet().remove(orderKey, userId.toString());
            log.debug("支付超时回补 Redis SREM orderKey={} userId={} removed={}", orderKey, userId, removed);
        } catch (Exception e) {
            log.error("支付超时回补 Redis 失败 voucherId={} userId={}，请人工核对 Redis 与 DB 库存", voucherId, userId, e);
        }
    }
}

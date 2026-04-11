package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * @param idempotencyKey 请求头 Idempotency-Key，可选；相同键在 TTL 内返回同一 orderId
     */
    Result seckillVoucher(Long voucherId, String idempotencyKey);

    void createVoucherOrder(VoucherOrder voucherOrder);
}

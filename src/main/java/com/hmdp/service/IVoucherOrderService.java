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

    /**
     * 支付成功回调（或用户主动确认支付）：将未支付订单置为已支付。
     * 真实渠道（微信/支付宝/余额扣款、签名、对账）见实现类 TODO。
     */
    Result paySuccess(Long orderId);
}

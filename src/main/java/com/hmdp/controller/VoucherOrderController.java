package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(
            @PathVariable("id") Long voucherId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        return voucherOrderService.seckillVoucher(voucherId, idempotencyKey);
    }

    /**
     * 支付成功：将当前用户名下「未支付」订单更新为已支付（渠道验签、第三方回调等见服务层 TODO）。
     */
    @PostMapping("pay/{orderId}")
    public Result paySuccess(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.paySuccess(orderId);
    }
}

package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.VoucherOrderRepublishRequest;
import com.hmdp.service.MqKafkaCompensationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Kafka 补偿与失败审计查询（已加入拦截器白名单；生产环境请改为鉴权或内网-only）
 */
@RestController
@RequestMapping("/mq/compensation/kafka")
public class MqKafkaCompensationController {

    @Resource
    private MqKafkaCompensationService mqKafkaCompensationService;

    @GetMapping("/failed-logs")
    public Result listFailedLogs(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(mqKafkaCompensationService.listRecentFailedLogs(limit));
    }

    @PostMapping("/voucher/republish")
    public Result republishVoucher(@RequestBody VoucherOrderRepublishRequest body) {
        if (body == null) {
            return Result.fail("body 不能为空");
        }
        return mqKafkaCompensationService.republishVoucherOrder(
                body.getOrderId(),
                body.getUserId(),
                body.getVoucherId()
        );
    }
}

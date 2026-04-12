package com.hmdp.agent.web;

import com.hmdp.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 将 Agent 侧 /api/agent/reliability/* 转发到主后端已存在的 MQ 补偿接口（见主工程 MqKafkaCompensationController）
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/reliability")
public class AgentReliabilityProxyController {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private AgentProperties agentProperties;

    @GetMapping(value = "/failed-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public String failedLogs(@RequestParam(defaultValue = "20") int limit) {
        String url = agentProperties.getBackendBaseUrl() + "/mq/compensation/kafka/failed-logs?limit=" + limit;
        log.debug("proxy GET {}", url);
        return restTemplate.getForObject(url, String.class);
    }

    @PostMapping(value = "/voucher/republish", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String voucherRepublish(@RequestBody Map<String, Object> body) {
        String url = agentProperties.getBackendBaseUrl() + "/mq/compensation/kafka/voucher/republish";
        log.warn("proxy POST voucher republish body keys={}", body != null ? body.keySet() : null);
        return restTemplate.postForObject(url, body, String.class);
    }
}

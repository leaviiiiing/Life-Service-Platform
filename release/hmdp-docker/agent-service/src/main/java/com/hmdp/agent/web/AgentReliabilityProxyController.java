package com.hmdp.agent.web;

import com.hmdp.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

/**
 * 将 /api/agent/reliability/* 转发到主后端 /agent/reliability/*（编排入口与主域解耦）
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/reliability")
public class AgentReliabilityProxyController {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private AgentProperties agentProperties;

    @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public String check() {
        String url = agentProperties.getBackendBaseUrl() + "/agent/reliability/check";
        log.debug("proxy GET {}", url);
        return restTemplate.getForObject(url, String.class);
    }

    @PostMapping(value = "/replay/{msgId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String replay(@PathVariable("msgId") String msgId) {
        String url = agentProperties.getBackendBaseUrl() + "/agent/reliability/replay/" + msgId;
        log.warn("proxy POST replay msgId={}", msgId);
        return restTemplate.postForObject(url, null, String.class);
    }
}

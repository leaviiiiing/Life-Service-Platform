package com.hmdp.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 编排代理：主后端基址（Docker 网络内为 http://backend:8081）
 */
@Data
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 主应用 base URL，不含尾斜杠 */
    private String backendBaseUrl = "http://localhost:8081";

    /** 每 IP 每分钟最大 /api/agent 请求数，0 表示不限制 */
    private int rateLimitPerMinute = 120;
}

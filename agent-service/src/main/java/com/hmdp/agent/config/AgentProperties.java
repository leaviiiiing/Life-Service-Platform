package com.hmdp.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 配置项：主后端代理地址、限流、LLM（对应 application.yml 中 agent.*）
 */
@Data
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 主应用 base URL，不含尾斜杠（Docker 内常为 http://backend:8081） */
    private String backendBaseUrl = "http://localhost:8081";

    /** 每 IP 每分钟最大 /api/agent 请求数，0 表示不限制 */
    private int rateLimitPerMinute = 120;

    /** 大模型相关（OpenAI 兼容 Chat Completions），规则未命中时使用 */
    private Llm llm = new Llm();

    @Data
    public static class Llm {
        /** 为 true 且配置了 api-key、model 时，规则未命中则调用大模型 */
        private boolean enabled = true;
        /** 勿提交到 Git，使用环境变量 AGENT_LLM_API_KEY */
        private String apiKey = "";
        /** Chat Completions API 根路径（不含 /chat/completions 时将自动拼接） */
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        /**
         * 服务商提供的模型名或推理接入点 ID（如 ep-xxxx）；须与 base-url 对应平台一致。
         */
        private String model = "";
        /** HTTP 读超时（毫秒） */
        private int timeoutMs = 90000;
        /** 采样温度 */
        private double temperature = 0.6;
    }
}

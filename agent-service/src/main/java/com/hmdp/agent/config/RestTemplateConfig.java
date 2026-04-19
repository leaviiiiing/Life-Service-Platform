package com.hmdp.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 供 {@link com.hmdp.agent.web.AgentReliabilityProxyController} 调用主后端 MQ 补偿等 HTTP 接口。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        // 连接 5s、读 15s；排障接口可能触库，略放宽避免误杀
        f.setConnectTimeout(5000);
        f.setReadTimeout(15000);
        return new RestTemplate(f);
    }
}

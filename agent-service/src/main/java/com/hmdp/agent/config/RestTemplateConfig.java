package com.hmdp.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** 调用主后端 MQ 补偿等 HTTP 接口 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        // 代理排障接口略放宽读超时，避免主库慢时误杀
        f.setConnectTimeout(5000);
        f.setReadTimeout(15000);
        return new RestTemplate(f);
    }
}

package com.hmdp.agent;

import com.hmdp.agent.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Agent 独立进程入口，与消费社交生活服务平台主业务后端（Spring Boot 工程名 life-service-platform）分离部署。
 */
@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}

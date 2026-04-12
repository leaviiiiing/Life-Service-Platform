# Agent 服务说明（hmdp-agent-service）

独立 Spring Boot 进程，与主业务后端（`life-service-platform`）分离，经 Nginx 将 **`/api/agent/*`** 转发至本服务（容器内端口 **8082**）。

## 职责

- **对话**：`POST /api/agent/chat`，规则 FAQ（`agent-service/src/main/resources/agent/faq-rules.json`）+ Redis 会话。
- **MQ 排障代理**：将下列路径转发到主后端（`AGENT_BACKEND_BASE_URL`，Docker 内默认 `http://backend:8081`）：
  - `GET /api/agent/reliability/failed-logs?limit=` → `GET /mq/compensation/kafka/failed-logs`
  - `POST /api/agent/reliability/voucher/republish` → `POST /mq/compensation/kafka/voucher/republish`

主后端上述补偿接口在 `MvcConfig` 中为白名单（无需登录），生产环境请收紧。

## 环境变量

| 变量 | 说明 |
|------|------|
| `SPRING_REDIS_HOST` / `PORT` / `PASSWORD` | 与主站共用 Redis 时可同 compose 中的 `redis` 服务 |
| `AGENT_BACKEND_BASE_URL` | 主后端根 URL，无尾斜杠，如 `http://backend:8081` |
| `AGENT_RATE_LIMIT` | 每分钟每 IP 请求 `/api/agent` 上限，0 不限制 |

## 本地运行

```bash
cd agent-service
mvn -q -DskipTests package
java -jar target/hmdp-agent-service-0.0.1-SNAPSHOT.jar
```

默认端口 `8082`，需本机 Redis；主后端用于代理时设 `AGENT_BACKEND_BASE_URL=http://localhost:8081`。

## 前端入口

静态页 [`deploy/frontend/dist/agent.html`](../deploy/frontend/dist/agent.html)，首页顶部有「运维助手」图标链接。浏览器访问 API 须走 **Nginx 80**（`/api/agent/` 才会转发到 agent 容器）。

## 与 MQ 文档的关系

Kafka 可靠性、补偿与幂等详见 [MQ_KAFKA_IMPLEMENTATION_REPORT.md](./MQ_KAFKA_IMPLEMENTATION_REPORT.md)。

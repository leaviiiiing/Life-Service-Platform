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

静态页 [`deploy/frontend/dist/agent.html`](../deploy/frontend/dist/agent.html)；首页顶部有「运维助手」图标，底栏 **「消息」** 亦跳转助手页。浏览器访问 API 须走 **Nginx 80**（`/api/agent/` 才会转发到 agent 容器）。Nginx 已转发 `Authorization`，便于后续与主站登录态对齐。

## 源码位置（唯一维护）

以仓库根目录 **[`agent-service/`](../agent-service/)** 为准；若存在历史目录 `release/hmdp-docker/agent-service/`，请勿双轨修改，应以此处为源同步或删除重复。

## 规则热更新（可选）

`agent/faq-rules.json` 在镜像内来自 classpath；生产可将文件挂载覆盖，例如 compose 中增加：

`./agent-service/src/main/resources/agent/faq-rules.json:/app/BOOT-INF/classes/agent/faq-rules.json:ro`（路径以实际解压层为准，或用 `docker config` / ConfigMap）。

## 生产加固（可选）

- 为 `/api/agent/*` 增加网关鉴权、内网 ACL 或独立 API Key；与 [`AgentRateLimitFilter`](../agent-service/src/main/java/com/hmdp/agent/config/AgentRateLimitFilter.java) 组合使用。
- 将 MQ 补偿代理改为需登录：主后端去掉 `/mq/compensation/**` 白名单并在代理层转发 `Authorization`（需在 `RestTemplate` 侧显式带主站 token，当前为薄转发 JSON）。

## Phase 3（可选扩展）

- **LLM**：OpenAI 兼容 HTTP，`FaqRuleService` 未命中时调用；密钥与超时走环境变量。
- **RAG**：MQ 文档片段或向量检索接入同一 `POST /api/agent/chat` 管道。

## 与 MQ 文档的关系

Kafka 可靠性、补偿与幂等详见 [MQ_KAFKA_IMPLEMENTATION_REPORT.md](./MQ_KAFKA_IMPLEMENTATION_REPORT.md)。

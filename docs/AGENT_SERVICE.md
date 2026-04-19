# Agent 服务说明（hmdp-agent-service）

**源码级串讲（与其它「代码详解」文档同风格）**：见 **[`AGENT_CODE_GUIDE.md`](./AGENT_CODE_GUIDE.md)**。

<!-- 与 MQ 报告分文档，避免单篇过长；部署端口见 DEPLOY_DOCKER.md -->

独立 Spring Boot 进程，与 **消费社交生活服务平台** 主业务后端分离（主工程 Maven 名 `life-service-platform`），经 Nginx 将 **`/api/agent/*`** 转发至本服务（容器内端口 **8082**）。

## 职责

- **对话**：`POST /api/agent/chat`，**关键词规则**（`agent/faq-rules.json`）优先；未命中时若配置了 **`AGENT_LLM_API_KEY`** 与 **`AGENT_LLM_MODEL`** 则调用 **OpenAI 兼容**大模型接口 + Redis 会话；响应多字段 **`source`**：`rule` | `llm` | `fallback`。
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
| `AGENT_LLM_API_KEY` | 大模型 API Key（**勿提交 Git**）；未配置或未配 `AGENT_LLM_MODEL` 时未命中规则仅用默认文案 |
| `AGENT_LLM_MODEL` | 模型名或服务商接入点 ID（如 `ep-xxxx`） |
| `AGENT_LLM_BASE_URL` | 可选，Chat Completions 兼容根路径 |
| `AGENT_LLM_ENABLED` | 可选，`false` 关闭 LLM 兜底 |

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

## LLM（已实现）

- **OpenAI 兼容** `POST .../chat/completions`，实现类 **`LlmChatService`**，编排见 **`AgentChatService`**。
- **RAG**：可将文档片段拼入 `system` 或历史消息，接入同一管道（待扩展）。

**安全**：API Key 仅通过环境变量注入；若密钥曾泄露，请在对应服务商控制台**轮换**。

## 与 MQ 文档的关系

Kafka 可靠性、补偿与幂等详见 [MQ_KAFKA_IMPLEMENTATION_REPORT.md](./MQ_KAFKA_IMPLEMENTATION_REPORT.md)。

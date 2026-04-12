# Life-Service-Platform Docker 一键部署

## 1. Linux 服务器部署

1. 上传 `life-service-platform-docker.tar.gz` 到 Linux
2. 解压并进入目录：

```bash
tar -xzf life-service-platform-docker.tar.gz
cd life-service-platform-docker
```

3. 一键启动：

```bash
bash deploy/scripts/start.sh
```

4. 查看状态/日志：

```bash
docker compose ps
bash deploy/scripts/logs.sh
```

5. 停止服务：

```bash
bash deploy/scripts/stop.sh
```

## 2. 默认端口

- 前端（Nginx）：`80`
- 主后端：`8081`
- **Agent 服务**：`8082`（浏览器一般通过 Nginx 访问 **`/api/agent/*`**，无需直连 8082）
- MySQL：`3306`
- Redis：`6379`
- Kafka：`9092`

## 3. Nginx 与 Agent

[`deploy/frontend/nginx.conf`](deploy/frontend/nginx.conf) 将 **`/api/agent/`** 转发到 `agent:8082`，其余 **`/api/`** 仍转发主后端。静态页 [`deploy/frontend/dist/agent.html`](deploy/frontend/dist/agent.html) 通过同源 `/api` 调用助手接口。

## 4. 默认账号密码

- MySQL root: `123456`
- Redis: `123456`

建议上线前在 `docker-compose.yml` 里改掉默认密码。

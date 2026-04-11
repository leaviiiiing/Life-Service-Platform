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

- 前端：`80`
- 后端：`8081`
- MySQL：`3306`
- Redis：`6379`
- Kafka：`9092`
- RabbitMQ：`5672`
- RabbitMQ 管理台：`15672`

## 3. 默认账号密码

- MySQL root: `123456`
- Redis: `123456`
- RabbitMQ: `root / 123456`

建议上线前在 `docker-compose.yml` 里改掉默认密码。

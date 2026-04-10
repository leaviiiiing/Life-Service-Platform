# HMDP Docker 一键部署

## 1. 本地准备

1. 确保后端 jar 已生成到 `target/hm-dianping-0.0.1-SNAPSHOT.jar`
2. 把前端打包产物覆盖到 `deploy/frontend/dist`
3. 执行打包脚本（在支持 bash 的环境中）：

```bash
bash deploy/scripts/package-linux.sh
```

会得到：`release/hmdp-docker-bundle.tar.gz`

## 2. Linux 服务器部署

1. 上传 `hmdp-docker-bundle.tar.gz` 到 Linux
2. 解压并进入目录：

```bash
tar -xzf hmdp-docker-bundle.tar.gz
cd hmdp-docker-bundle
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

## 3. 默认端口

- 前端：`80`
- 后端：`8081`
- MySQL：`3306`
- Redis：`6379`
- Kafka：`9092`
- RabbitMQ：`5672`
- RabbitMQ 管理台：`15672`

## 4. 默认账号密码

- MySQL root: `123456`
- Redis: `123456`
- RabbitMQ: `root / 123456`

建议上线前在 `docker-compose.yml` 里改掉默认密码。

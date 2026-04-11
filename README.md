# Life-Service-Platform

本地生活服务示例平台：商铺浏览、探店笔记（Blog）、点赞与关注动态、优惠券与秒杀下单等；配套静态前端与 Docker 一键部署。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 2.3、MyBatis-Plus、Redis（Lettuce）、Redisson |
| 数据 | MySQL 8 |
| 消息 | **Kafka**（探店笔记粉丝 Feed 推送）、**RabbitMQ**（秒杀订单异步创建等） |
| 前端 | 静态 HTML + Vue 2 + Element UI（见 `deploy/frontend/dist`） |

## 功能概览

- **商铺**：列表、详情、类型筛选
- **探店笔记**：发布、热门流、详情、点赞；关注用户的笔记流（收件箱 Feed）
- **用户**：手机号 + 验证码登录（验证码写入 Redis，开发环境可在日志中查看）
- **优惠券 / 秒杀**：与 RabbitMQ 异步处理相关的下单链路

## 消息队列说明

- **Blog 推送**：发布笔记后向粉丝 Redis Feed 写入由 **Kafka** 异步消费（Topic：`blog.feed.topic`）。
- **其他 MQ 场景**：如秒杀订单仍使用 **RabbitMQ**，与 Kafka 并存。

本地或容器内需同时保证 **Kafka** 与 **RabbitMQ** 可达；Docker 编排见下文。

## 快速开始（Docker）

完整步骤、端口与默认口令见 **[DEPLOY_DOCKER.md](./DEPLOY_DOCKER.md)**。

简要流程：

1. 准备可运行的部署目录（含 `docker-compose.yml`、`target/*.jar`、`deploy/frontend/dist`、数据库初始化 SQL 等）。
2. 在 Linux 上解压后进入项目根目录，执行：

```bash
bash deploy/scripts/start.sh
```

3. 访问：前端 `http://<主机>:80`，后端 API `http://<主机>:8081`（或通过 Nginx 的 `/api` 反代）。

## 本地开发（简要）

1. 安装 **JDK 8**、**Maven**，本机启动 **MySQL**、**Redis**、**Kafka**、**RabbitMQ**。
2. 导入数据库脚本：`src/main/resources/db/hmdp.sql`（库名仍为 `hmdp`，与示例数据一致）。
3. 按环境修改 `src/main/resources/application.yaml`（数据源、Redis、RabbitMQ、Kafka `spring.kafka.bootstrap-servers` 等）。
4. 打包运行：

```bash
mvn -DskipTests package
java -jar target/life-service-platform-0.0.1-SNAPSHOT.jar
```

默认后端端口 **8081**（可在配置中修改）。

## 目录结构（主要）

```
├── src/main/java/com/hmdp/ # 业务与配置代码（包名未改，与历史课程一致）
├── src/main/resources/
│   ├── application.yaml       # 默认配置
│   ├── application-docker.yaml # Docker profile 示例
│   └── db/hmdp.sql            # MySQL 初始化脚本
├── deploy/
│   ├── frontend/              # Nginx 静态资源与 nginx.conf
│   └── scripts/               # start / stop / logs / 打包脚本
├── docker-compose.yml         # 多容器编排（含 MySQL、Redis、Kafka、RabbitMQ 等）
├── Dockerfile                 # 后端镜像构建
├── DEPLOY_DOCKER.md           # Docker 部署说明
└── pom.xml
```

## 安全提示

示例中的数据库、Redis、RabbitMQ 默认密码仅用于学习与本地/测试环境，**上线前务必在 `docker-compose.yml` 或配置中心中修改为强口令**。

## 许可证

教学示例项目，使用与修改请遵循课程或团队约定。

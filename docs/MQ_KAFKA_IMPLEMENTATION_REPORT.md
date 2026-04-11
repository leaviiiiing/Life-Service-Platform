# Kafka MQ 改造实施报告（单文档汇总）

<!-- 本文件按 todo 顺序追加各阶段完成情况，便于对照计划验收 -->

---

## Todo 1 — Kafka：msgId / Topic / 分区策略 / 验收范围

**状态：已完成**

### 范围说明

- **msgId 格式**：`{bizType}:{bizKey}:{uuid}`，其中 `bizType` 如 `VOUCHER_ORDER`、`BLOG_FEED`；`bizKey` 为业务主键或组合键字符串；`uuid` 为一次发送的唯一标识（可用 `UUID.randomUUID()` 去横杠）。
- **Topic 命名**：小写点分，与代码常量 `KafkaTopics` 保持一致；新增 Topic 需在 `KafkaConfig` 中注册 `NewTopic`（开发环境自动创建）。
- **分区键**：秒杀订单使用 `userId` 字符串作为 key，保证同一用户消息进入同一分区，便于顺序与排查；探店 Feed 使用 `blogId` 字符串。
- **验收（本阶段）**：常量与文档可追溯；不涉及运行态行为变更。

### 备注

<!-- 后续 todo 完成后在此文档继续追加章节，不删本段 -->

---

## Todo 2 — 后端可靠性：Kafka 生产/消费、手动 ack、审计表、移除 Rabbit

**状态：已完成**

### 做了什么

- **手动 ack**：`KafkaListenerConfig` 提供 `manualKafkaListenerContainerFactory`（`MANUAL_IMMEDIATE`），`BlogFeedConsumer` 与 `VoucherOrderKafkaListener` 均显式 `containerFactory` 绑定，业务结束后 `ack.acknowledge()`。
- **秒杀链路迁 Kafka**：`VoucherOrderServiceImpl` 使用 `KafkaTemplate` + `ProducerRecord`，头 `MSG_ID`；`VoucherOrderKafkaListener` 消费 `KafkaTopics.VOUCHER_ORDER`，异常时写 `tb_mq_kafka_log`、再发到 `KafkaTopics.VOUCHER_ORDER_DLT` 后仍 ack，避免无限重试堵分区。
- **Feed**：`BlogServiceImpl` 发送时带头 `MSG_ID`；`BlogFeedConsumer` 走同一手动 ack 工厂。
- **落库脚本**：`src/main/resources/db/z_mq_kafka_log.sql`（表 `tb_mq_kafka_log`）；实体 `MqKafkaLog`、Mapper、`MqKafkaLogService`。
- **依赖与部署**：`pom.xml` 去掉 `spring-boot-starter-amqp`；删除 `RabbitMqConfig`、`VoucherOrderListener`；`application.yaml` / `application-docker.yaml` 去掉 `spring.rabbitmq`，Kafka `producer.acks=all`、`retries=3`，`trusted.packages` 含 `com.hmdp.entity`；根目录 `docker-compose.yml` 去掉 `rabbitmq` 服务及 backend 相关环境变量与 `depends_on`。

### 验收注意

- 新环境或存量库需执行 `z_mq_kafka_log.sql` 后再压测发送失败/消费失败路径。
- 本机未检测到 `mvn` 命令时，请在已安装 Maven 的环境执行 `mvn compile` 做编译自检。

<!-- Todo3 起仍在本文件下方追加 -->

---

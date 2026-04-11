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

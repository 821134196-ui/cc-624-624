# 工厂设备停机原因拆分与产能损失核算系统

车间停机管理：操作员登记停机起止，班长在**同一时间轴**上把整次停机拆分为
**故障 / 待料 / 换线**等原因段，复核后进入正式统计；报表按当班采用的
**标准节拍**估算产能损失（明确标注为估算值，非实际减产数据）。

## 一条命令启动

```bash
./start.sh
```

启动后访问 http://localhost:8080 （前端构建产物由 Quarkus 直接托管）。
脚本会自动定位 JDK（`JAVA_HOME` → `~/tools/jdk-21` → 系统 java）与 Maven，
完成前端构建、后端打包并启动。SQLite 数据库文件位于 `backend/data/downtime.db`。

开发模式：`./start.sh --dev`（后端 quarkus:dev；前端另开终端 `cd frontend && npm run dev`，Vite 已配置 /api 代理）。

## 技术栈

- 后端：Java 17+ · Quarkus 3.15（REST + Hibernate ORM Panache）· SQLite（quarkiverse jdbc-sqlite）
- 前端：Svelte 4 + Vite
- 产量回传：本地模拟接口 `GET /api/mock/production?date=&shift=`（确定性伪数据）

## 业务规则

| 规则 | 实现 |
| --- | --- |
| 分段不重叠、不留空白、合计=整次停机时长 | `SegmentValidator` 服务端强校验（前端另有预检） |
| 复核后才进入正式统计 | 状态机 OPEN → PENDING_REVIEW → REVIEWED；报表仅含 REVIEWED |
| 跨班未恢复沿用同一事件 | 事件归属起始班次并标记 `crossShift`，下一班次不重复统计 |
| 损失为估算并明确标注 | 报表 `estimate=true`，页面固定“估算值”标识 |
| 保留节拍标准及版本、重算留痕 | `CycleTimeStandard` 版本化；每次核算生成 `LossCalculation` 快照（含版本号与节拍值），旧结果归档可查 |

班次模型：早班 08:00–20:00、夜班 20:00–次日 08:00（Asia/Shanghai，可在
`backend/src/main/resources/application.properties` 调整）。

## 主要 API

- `POST /api/events` 操作员登记停机（`endTime` 可空，跨班未恢复不新建事件）
- `PUT /api/events/{id}/times` 补登/修改起止（仅登记中）
- `PUT /api/events/{id}/segments` 班长提交时间轴分段（守恒校验）
- `POST /api/events/{id}/review` 复核通过 → 进入统计并生成损失核算
- `POST /api/events/{id}/recalculate` 标准调整后重算（旧结果保留）
- `GET  /api/events/{id}/calculations` 核算历史（含每次采用的节拍版本）
- `GET  /api/reports/shift?date=&shift=` 班次正式统计（estimate=true）
- `GET  /api/standards`、`POST /api/standards` 节拍标准版本管理

## 测试

```bash
cd backend && mvn test
```

覆盖：分段时长守恒（重叠/缺口/合计）、跨班事件只计一次、标准变更后重算且
旧结果及依据可查、未复核事件不混入正式统计。

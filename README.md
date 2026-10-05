# 工厂设备停机原因拆分与产能损失核算系统

一次停机不再只填一个原因。操作员登记停机起止时间,班长在**同一时间轴**上把整段停机
拆分为 **故障 / 待料 / 换线** 多段分别确认;系统校验各段**不重叠、不留白、合计等于整次
停机时长**,班长复核后才进入正式统计,并按**当班采用的标准节拍**估算各原因的产能损失。

> ⚠️ 报表中的损失件数全部是**按标准节拍推算的估算值,不是实际减产数据**;
> 实际产量以数采 / MES 回传为准(本项目用本地模拟接口代替,数据标注 `SIMULATED`)。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Quarkus 3.15(REST / CDI / 事务)、Hibernate ORM + Panache |
| 数据库 | SQLite(文件位于 `data/downtime.db`,零外部依赖) |
| 前端 | Svelte 4 + Vite,构建后由 Quarkus 同源提供 |
| 产量回传 | 本地确定性模拟接口 `/api/equipment/{id}/countback/*` |

## 一条命令启动

```bash
./run.sh
```

脚本会:安装前端依赖(首次)→ 构建 Svelte 到 Quarkus 静态资源目录 →
`mvn package` 打包后端 → 启动。启动后访问 **http://localhost:8080**。

前置:JDK 17+、Maven 3.8+、Node 18+。
(JDK/Maven 若装在 `~/tools/jdk-17.0.13+11`、`~/tools/apache-maven-3.9.9` 会被自动识别。)

空库首次启动会自动播种演示数据(3 台设备、4 起停机事件、节拍 v1/v2、模拟产量)。
重置演示数据:停止进程后删除 `data/downtime.db` 再启动。

## 核心业务规则与实现位置

1. **分段时长守恒** — `service/ConservationService.java`
   - 每段结束晚于开始;第一段起点 = 停机起点;最后一段终点 = 恢复时间;
   - 相邻段首尾严格相接(秒级),既不重叠也不留白;
   - 分段时长合计必须等于整次停机时长。保存分段与班长复核前各强制校验一次;
   - 前端时间轴编辑器(`EventDetail.svelte`)本地实时提示。

2. **跨班连续记录** — `service/EventService.java`
   - 停机事件以 `eventCode` 贯穿,设备未恢复时 `endTime` 为空(事件开放);
   - 同一设备存在开放事件时**禁止再次登记**(`EVENT_STILL_OPEN`),从入口杜绝
     "一次停机跨班被拆成两次独立故障";
   - 设备恢复时在同一事件回填时间;连续性视图展示途经班次。

3. **班长复核门控** — `service/ReviewService.java`
   - 只有 `REVIEWED` 且生成过核算批次的事件才进入报表
     (`LossCalculationService.reviewedEventsWithCalc` + `ReportService`);
   - `待分段 / 待复核` 事件一律不混入正式统计。

4. **节拍标准版本留痕 + 重算** — `model/CycleTimeStandard.java`、`service/LossCalculationService.java`
   - 每次调整产生新版本,旧版本置 `SUPERSEDED` 但永不删除;
   - 首算 `HISTORICAL` 按**每个分段当班当时生效版本**取节拍,跨标准生效点可引用多版本;
   - 每行快照标准 ID / 版本 / 节拍秒数,批次头保存所用版本清单与人读计算依据;
   - 标准变更后 `RECALC` 重算**只新增批次**,旧批次标记 `superseded` 但结果与依据永久可查。

5. **估算口径** — `LossCalculationService.ESTIMATE_DISCLAIMER`
   - 分段估算损失 = 分段秒数 ÷ 节拍(秒/件);批次与报表均带估算声明,禁止当作实际减产。

## 主要接口

| 方法 & 路径 | 说明 |
|---|---|
| `POST /api/events` | 操作员登记停机(同设备有开放事件时 400) |
| `POST /api/events/{id}/resolve` | 设备恢复,回填同一事件(可跨班) |
| `POST /api/events/{id}/segments` | 班长保存时间轴分段(强制守恒) |
| `GET /api/events/{id}/conservation` | 守恒检查(结构化问题清单) |
| `GET /api/events/{id}/continuity` | 跨班连续性视图 |
| `POST /api/events/{id}/review` / `/reject` | 班长复核(通过即按当班标准首算)/ 驳回 |
| `GET /api/events/{id}/calculations` | 全部核算批次(首算 + 历次重算) |
| `POST /api/events/{id}/recalculate` | 用当前最新标准重算(新增批次) |
| `GET/POST /api/equipment/{id}/standards` | 节拍版本查询 / 发布新版本 |
| `GET /api/reports/shift?date=&shift=` | 班次正式报表(仅已复核,估算标注) |
| `POST /api/equipment/{id}/countback/backfill` | 生成本地模拟分钟产量 |

## 演示数据场景

- **事件 DT-…-001(已复核)**:故障 40 分 + 待料 40 分 + 换线 20 分 = 100 分,三段守恒;
  已用今日生效的 v2(50s/件)重算,首算 v1(60s/件)=100 件与重算 v2=120 件两版并列可查。
- **事件 DT-…-002(已复核)**:19:00→21:30 跨越 早班→夜班,全程同一单号;早班报表只算
  故障段、夜班报表只算待料段,**同一事件不会在两班重复计为两次故障**。
- **事件 DT-…-001 次日(开放)**:跨班未恢复,沿用同一单号,不能重复登记。
- **事件 DT-…-003(待复核)**:分段守恒但未复核,**不进入**任何正式报表。

## 测试

四个端到端验收点见 `src/test/java/factory/downtime/DowntimeWorkflowTest.java`:

```bash
mvn test
```

- `test01` 分段守恒:留白 / 重叠 / 合计不等被拒,合法三段通过;
- `test02` 跨班连续:开放事件禁重复登记、单号不变、跨班报表不重复计数;
- `test03` 标准变更:首算 v1=100 件保留,重算 v2=120 件新增,依据可查;未复核禁重算;
- `test04` 复核门控:待复核事件不混入报表。

## 目录结构

```
src/main/java/factory/downtime/
  model/      JPA 实体:设备/事件/分段/节拍标准版本/核算批次/损失行/模拟产量
  service/    守恒校验、事件、分段、复核、标准版本、损失核算、报表、模拟产量
  api/        REST 资源、DTO、异常映射、视图装配
  infra/      Instant 转换器、演示数据播种
frontend/     Svelte + Vite 源码(时间轴分段编辑器 / 结果对照 / 报表 / 标准版本)
run.sh        一条命令构建并启动
```

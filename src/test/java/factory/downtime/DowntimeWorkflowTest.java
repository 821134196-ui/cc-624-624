package factory.downtime;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * 端到端验收:
 * 1) 分段时长守恒(留白/重叠/合计不等被拒);
 * 2) 跨班连续记录(开放事件禁重复登记、跨班事件单号不变且报表只算一次);
 * 3) 节拍标准变更后重算,首算批次保留、依据可查;
 * 4) 未复核事件不混入正式统计。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class DowntimeWorkflowTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final LocalDate D = LocalDate.of(2026, 10, 4);

    @Inject
    TestCleaner cleaner;

    static Instant t(LocalDate d, int h, int m) {
        return LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(ZONE).toInstant();
    }

    static Object std(double secs, Instant from, String reason) {
        return Map.of("cycleSeconds", secs, "effectiveFrom", from.toString(),
                "changedBy", "测试工程师", "changeReason", reason);
    }

    static Map<String, Object> seg(String reason, Instant s, Instant e) {
        return Map.of("reason", reason, "startTime", s.toString(), "endTime", e.toString());
    }

    Long createEquipment(String code) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("code", code, "name", code + "设备", "workshop", "测试车间"))
                .post("/api/equipment").then().statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    Long register(Long equipId, Instant s, Instant e) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("equipmentId", equipId);
        body.put("startTime", s.toString());
        body.put("endTime", e == null ? null : e.toString());
        body.put("registeredBy", "操作员甲");
        return given().contentType(ContentType.JSON)
                .body(body)
                .post("/api/events").then().statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    @BeforeEach
    void clean() {
        cleaner.wipe();
    }

    // ------------------------------------------------------------------
    // 1) 分段时长守恒
    // ------------------------------------------------------------------
    @Test
    void test01_segmentConservation_enforced() {
        Long eq = createEquipment("EQ-T1");
        given().contentType(ContentType.JSON)
                .body(std(60, t(D.minusMonths(9), 0, 0), "v1")).post("/api/equipment/{id}/standards", eq)
                .then().statusCode(200);

        Instant s = t(D, 9, 0), e = t(D, 10, 40); // 100 分钟
        Long ev = register(eq, s, e);

        // (a) 两段之间留白:09:00-09:30 + 09:40-10:40,中间 10 分钟无原因 -> 拒绝
        given().contentType(ContentType.JSON)
                .body(Map.of("confirmedBy", "班长", "segments", List.of(
                        seg("故障", t(D, 9, 0), t(D, 9, 30)),
                        seg("待料", t(D, 9, 40), t(D, 10, 40)))))
                .post("/api/events/{id}/segments", ev)
                .then().statusCode(400)
                .body("code", equalTo("CONSERVATION_VIOLATION"))
                .body("message", containsString("留白"));

        // (b) 两段重叠 -> 拒绝
        given().contentType(ContentType.JSON)
                .body(Map.of("confirmedBy", "班长", "segments", List.of(
                        seg("故障", t(D, 9, 0), t(D, 9, 50)),
                        seg("待料", t(D, 9, 40), t(D, 10, 40)))))
                .post("/api/events/{id}/segments", ev)
                .then().statusCode(400)
                .body("message", containsString("重叠"));

        // (c) 合计不等于整次停机(结尾留白) -> 拒绝
        given().contentType(ContentType.JSON)
                .body(Map.of("confirmedBy", "班长", "segments", List.of(
                        seg("故障", t(D, 9, 0), t(D, 9, 40)),
                        seg("待料", t(D, 9, 40), t(D, 10, 20)))))
                .post("/api/events/{id}/segments", ev)
                .then().statusCode(400)
                .body("message", containsString("结尾留白"));

        // (d) 合法三段 40+40+20=100 分钟 -> 保存并复核成功
        saveValidSegments(ev, s, e);
        given().contentType(ContentType.JSON)
                .body(Map.of("reviewedBy", "班长", "comment", "ok"))
                .post("/api/events/{id}/review", ev)
                .then().statusCode(200)
                .body("status", equalTo("REVIEWED"))
                .body("conservationValid", equalTo(true))
                .body("coveredSeconds", equalTo(6000))
                .body("totalDurationSeconds", equalTo(6000));
    }

    private void saveValidSegments(Long ev, Instant s, Instant e) {
        given().contentType(ContentType.JSON)
                .body(Map.of("confirmedBy", "班长", "segments", List.of(
                        seg("故障", s, t(D, 9, 40)),
                        seg("待料", t(D, 9, 40), t(D, 10, 20)),
                        seg("换线", t(D, 10, 20), e))))
                .post("/api/events/{id}/segments", ev)
                .then().statusCode(200);
    }

    // ------------------------------------------------------------------
    // 2) 跨班连续记录
    // ------------------------------------------------------------------
    @Test
    void test02_crossShift_continuity_andNoDuplicate() {
        Long eq = createEquipment("EQ-T2");
        given().contentType(ContentType.JSON)
                .body(std(30, t(D.minusMonths(9), 0, 0), "v1")).post("/api/equipment/{id}/standards", eq)
                .then().statusCode(200);

        // 开放事件:只登记开始、未恢复(Map 允许 null 值)
        java.util.Map<String, Object> openBody = new java.util.HashMap<>();
        openBody.put("equipmentId", eq);
        openBody.put("startTime", t(D, 1, 0).toString());
        openBody.put("endTime", null);
        openBody.put("registeredBy", "夜班孙");
        Long ev = given().contentType(ContentType.JSON)
                .body(openBody)
                .post("/api/events").then().statusCode(200)
                .extract().jsonPath().getLong("id");
        String code = given().get("/api/events/{id}", ev).then().statusCode(200)
                .extract().jsonPath().getString("eventCode");

        // 同设备再次登记 -> 拒绝,防止拆成两次独立故障
        java.util.Map<String, Object> dupBody = new java.util.HashMap<>();
        dupBody.put("equipmentId", eq);
        dupBody.put("startTime", t(D, 7, 0).toString());
        dupBody.put("endTime", null);
        dupBody.put("registeredBy", "早班李");
        given().contentType(ContentType.JSON)
                .body(dupBody)
                .post("/api/events").then().statusCode(400)
                .body("code", equalTo("EVENT_STILL_OPEN"))
                .body("message", containsString(code));

        // 跨班后在同一事件上回填恢复时间:01:00 属夜班,09:30 已交接到早班 -> 真正跨班
        given().contentType(ContentType.JSON)
                .body(Map.of("endTime", t(D, 9, 30).toString()))
                .post("/api/events/{id}/resolve", ev).then().statusCode(200)
                .body("eventCode", equalTo(code))   // 单号不变
                .body("open", equalTo(false));

        // 连续性视图:跨越 夜班->早班
        given().get("/api/events/{id}/continuity", ev).then().statusCode(200)
                .body("eventCode", equalTo(code))
                .body("crossesShifts", equalTo(true));

        // ---- 跨班次边界的完整事件:19:00(早班) -> 21:30(夜班) ----
        Long ev2 = register(eq, t(D, 19, 0), t(D, 21, 30)); // 150 分钟
        given().contentType(ContentType.JSON)
                .body(Map.of("confirmedBy", "双班班长", "segments", List.of(
                        seg("故障", t(D, 19, 0), t(D, 20, 30)),   // 90 分钟,早班
                        seg("待料", t(D, 20, 30), t(D, 21, 30))))) // 60 分钟,夜班
                .post("/api/events/{id}/segments", ev2).then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body(Map.of("reviewedBy", "夜班陈", "comment", "跨班连续"))
                .post("/api/events/{id}/review", ev2).then().statusCode(200);

        given().get("/api/events/{id}/continuity", ev2).then().statusCode(200)
                .body("crossesShifts", equalTo(true));

        // 早班报表:只纳入故障段(中点 19:45),事件计数 1,时长 5400s,损失 5400/30=180
        given().queryParam("date", D.toString()).queryParam("shift", "早班")
                .get("/api/reports/shift").then().statusCode(200)
                .body("eventCount", equalTo(1))
                .body("totalDurationSeconds", equalTo(5400))
                .body("totalEstimatedLossUnits", equalTo(180.0f));
        // 夜班报表:只纳入待料段(中点 21:00),仍是同一个事件(不重复计为两次故障)
        given().queryParam("date", D.toString()).queryParam("shift", "夜班")
                .get("/api/reports/shift").then().statusCode(200)
                .body("eventCount", equalTo(1))
                .body("totalDurationSeconds", equalTo(3600))
                .body("totalEstimatedLossUnits", equalTo(120.0f));
    }

    // ------------------------------------------------------------------
    // 3) 标准变更后重算:原结果与依据保留
    // ------------------------------------------------------------------
    @Test
    void test03_standardChange_recalcKeepsOriginalBatch() {
        Long eq = createEquipment("EQ-T3");
        // v1=60s 早已生效;v2=50s 在停机事件之后才生效
        given().contentType(ContentType.JSON)
                .body(std(60, t(D.minusMonths(9), 0, 0), "初始"))
                .post("/api/equipment/{id}/standards", eq).then().statusCode(200);

        Instant s = t(D, 9, 0), e = t(D, 10, 40); // 100 分钟
        Long ev = register(eq, s, e);
        saveValidSegments(ev, s, e);
        given().contentType(ContentType.JSON)
                .body(Map.of("reviewedBy", "班长", "comment", "ok"))
                .post("/api/events/{id}/review", ev).then().statusCode(200);

        // 首算:按当班当时标准 v1,损失 = 6000/60 = 100 件
        given().get("/api/events/{id}/calculations", ev).then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].batchNo", equalTo(1))
                .body("[0].triggerType", equalTo("REVIEW"))
                .body("[0].standardMode", equalTo("HISTORICAL"))
                .body("[0].superseded", equalTo(false))
                .body("[0].totalEstimatedLossUnits", equalTo(100.0f))
                .body("[0].lines[0].standardVersion", equalTo(1))
                .body("[0].lines[0].cycleSecondsUsed", equalTo(60.0f))
                .body("[0].basisText", containsString("v1"))
                .body("[0].estimateDisclaimer", containsString("估算"));

        // 发布 v2(次日生效)
        given().contentType(ContentType.JSON)
                .body(std(50, D.plusDays(1).atStartOfDay(ZONE).toInstant(), "提速"))
                .post("/api/equipment/{id}/standards", eq).then().statusCode(200)
                .body("version", equalTo(2)).body("status", equalTo("ACTIVE"));
        // 旧版本仍可查
        given().get("/api/equipment/{id}/standards", eq).then().statusCode(200)
                .body("find { it.version == 1 }.status", equalTo("SUPERSEDED"));

        // 用当前标准重算 -> 6000/50 = 120 件
        given().contentType(ContentType.JSON)
                .body(Map.of("mode", "CURRENT", "calculatedBy", "计划员"))
                .post("/api/events/{id}/recalculate", ev).then().statusCode(200)
                .body("batchNo", equalTo(2))
                .body("triggerType", equalTo("RECALC"))
                .body("standardMode", equalTo("CURRENT"))
                .body("superseded", equalTo(false))
                .body("totalEstimatedLossUnits", equalTo(120.0f))
                .body("lines[0].standardVersion", equalTo(2))
                .body("lines[0].cycleSecondsUsed", equalTo(50.0f));

        // 两版批次并列:首算被标记取代但仍保留,结果与依据不变
        given().get("/api/events/{id}/calculations", ev).then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].batchNo", equalTo(1))
                .body("[0].superseded", equalTo(true))
                .body("[0].totalEstimatedLossUnits", equalTo(100.0f))
                .body("[0].basisText", containsString("v1"))
                .body("[1].batchNo", equalTo(2))
                .body("[1].totalEstimatedLossUnits", equalTo(120.0f));

        // 未复核事件不能重算
        Long other = register(eq, t(D, 13, 0), t(D, 14, 0));
        given().contentType(ContentType.JSON)
                .body(Map.of("mode", "CURRENT"))
                .post("/api/events/{id}/recalculate", other).then().statusCode(400);
    }

    // ------------------------------------------------------------------
    // 4) 未复核事件不进入正式统计
    // ------------------------------------------------------------------
    @Test
    void test04_unreviewedEvents_excludedFromReport() {
        Long eq = createEquipment("EQ-T4");
        given().contentType(ContentType.JSON)
                .body(std(60, t(D.minusMonths(9), 0, 0), "v1")).post("/api/equipment/{id}/standards", eq)
                .then().statusCode(200);

        // 已复核事件(90 分钟全故障)
        Long reviewed = register(eq, t(D, 9, 0), t(D, 10, 30));
        given().contentType(ContentType.JSON)
                .body(Map.of("confirmedBy", "班长", "segments", List.of(
                        seg("故障", t(D, 9, 0), t(D, 10, 0)),
                        seg("换线", t(D, 10, 0), t(D, 10, 30)))))
                .post("/api/events/{id}/segments", reviewed).then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body(Map.of("reviewedBy", "班长", "comment", "ok"))
                .post("/api/events/{id}/review", reviewed).then().statusCode(200);

        // 待复核事件(SEGMENTED,60 分钟)
        Long pending = register(eq, t(D, 14, 0), t(D, 15, 0));
        given().contentType(ContentType.JSON)
                .body(Map.of("confirmedBy", "班长", "segments", List.of(
                        seg("故障", t(D, 14, 0), t(D, 14, 40)),
                        seg("待料", t(D, 14, 40), t(D, 15, 0)))))
                .post("/api/events/{id}/segments", pending).then().statusCode(200);

        // 仅已复核事件进入报表:时长 5400s,损失 3600/60+1800/60=90 件
        given().queryParam("date", D.toString()).queryParam("shift", "早班")
                .get("/api/reports/shift").then().statusCode(200)
                .body("reviewedOnly", equalTo(true))
                .body("eventCount", equalTo(1))
                .body("totalDurationSeconds", equalTo(5400))
                .body("totalEstimatedLossUnits", equalTo(90.0f))
                .body("disclaimer", notNullValue());

        // 待复核事件在列表中确实存在,只是被门控排除
        given().get("/api/events").then().statusCode(200)
                .body("find { it.id == %d }.status".formatted(pending), equalTo("SEGMENTED"));
    }
}

package com.factory.downtime;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 标准节拍变更后的重算：新核算采用新标准，旧结果及其节拍依据保留可查。
 * 使用 3 号焊接线（WLD-03，仅 v1=18s），避免与其他用例互相影响。
 */
@QuarkusTest
class RecalculationTest {

    // 2026-01-08 早班，UTC 02:00-03:00（CST 10:00-11:00），共 3600 秒
    private static final String START = "2026-01-08T02:00:00Z";
    private static final String END = "2026-01-08T03:00:00Z";

    @Test
    void recalculationKeepsHistoryWithStandardBasis() {
        long id = given().contentType(ContentType.JSON)
                .body("""
                        {"equipmentId":3,"startTime":"%s","endTime":"%s",
                         "operatorName":"重算测试","note":"标准变更"}
                        """.formatted(START, END))
                .when().post("/api/events")
                .then().statusCode(200)
                .extract().jsonPath().getLong("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"segments":[{"start":"%s","end":"%s","reason":"FAULT"}]}
                        """.formatted(START, END))
                .when().put("/api/events/" + id + "/segments")
                .then().statusCode(200);

        // 复核：按当时适用的 v1（18 秒/件）核算 → 3600/18 = 200 件
        given().contentType(ContentType.JSON)
                .body("{\"reviewerName\":\"班长\"}")
                .when().post("/api/events/" + id + "/review")
                .then().statusCode(200)
                .body("calculation.standardVersion", is("v1"))
                .body("calculation.taktSeconds", is(18f))
                .body("calculation.totalEstimatedLoss", is(200f));

        // 发布更早生效的新版本 v0（6 秒/件，2026-01-01 起生效）
        given().contentType(ContentType.JSON)
                .body("""
                        {"equipmentId":3,"version":"v0","taktSeconds":6.0,
                         "effectiveFrom":"2026-01-01T00:00:00Z","note":"节拍调整"}
                        """)
                .when().post("/api/standards")
                .then().statusCode(200)
                .body("version", is("v0"));

        // 重算：采用 v0 → 3600/6 = 600 件
        given().when().post("/api/events/" + id + "/recalculate")
                .then().statusCode(200)
                .body("standardVersion", is("v0"))
                .body("taktSeconds", is(6f))
                .body("totalEstimatedLoss", is(600f))
                .body("active", is(true));

        // 历史：两次核算都在，旧结果保留 v1 依据且已归档
        given().when().get("/api/events/" + id + "/calculations")
                .then().statusCode(200)
                .body("size()", is(2))
                .body("find { it.active }.standardVersion", is("v0"))
                .body("find { !it.active }.standardVersion", is("v1"))
                .body("find { !it.active }.taktSeconds", is(18f))
                .body("find { !it.active }.totalEstimatedLoss", is(200f));

        // 报表采用当前生效核算（600 件），并标注为估算
        given().queryParam("date", "2026-01-08").queryParam("shift", "DAY")
                .when().get("/api/reports/shift")
                .then().statusCode(200)
                .body("estimate", is(true))
                .body("eventCount", is(1))
                .body("totalEstimatedLoss", is(600f));
    }
}

package com.factory.downtime;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 核心流程：登记 → 非法分段被拒 → 合法分段 → 复核前不进统计 → 复核后进入正式统计。
 * 班次口径：Asia/Shanghai，早班 08:00-20:00（UTC 00:00-12:00）。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DowntimeFlowTest {

    // 2026-01-06 早班（CST 08:00-20:00），UTC 00:00 起
    private static final String START = "2026-01-06T00:00:00Z";
    private static final String MID1 = "2026-01-06T00:40:00Z";
    private static final String MID2 = "2026-01-06T00:55:00Z";
    private static final String END = "2026-01-06T01:15:00Z";

    private static long eventId;

    @Test
    @Order(1)
    void operatorRegistersDowntime() {
        eventId = given().contentType(ContentType.JSON)
                .body("""
                        {"equipmentId":1,"startTime":"%s","endTime":"%s",
                         "operatorName":"测试操作员","note":"流程测试"}
                        """.formatted(START, END))
                .when().post("/api/events")
                .then().statusCode(200)
                .body("status", is("OPEN"))
                .body("shiftType", is("DAY"))
                .body("crossShift", is(false))
                .extract().jsonPath().getLong("id");
    }

    @Test
    @Order(2)
    void overlappingSegmentsRejected() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"segments":[
                          {"start":"%s","end":"%s","reason":"FAULT"},
                          {"start":"%s","end":"%s","reason":"WAIT_MATERIAL"}]}
                        """.formatted(START, MID2, MID1, END))
                .when().put("/api/events/" + eventId + "/segments")
                .then().statusCode(400)
                .body("message", containsString("重叠"));
    }

    @Test
    @Order(3)
    void gappedSegmentsRejected() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"segments":[
                          {"start":"%s","end":"%s","reason":"FAULT"},
                          {"start":"%s","end":"%s","reason":"WAIT_MATERIAL"}]}
                        """.formatted(START, MID1, MID2, END))
                .when().put("/api/events/" + eventId + "/segments")
                .then().statusCode(400)
                .body("message", containsString("空白"));
    }

    @Test
    @Order(4)
    void validSegmentsAcceptedAndMoveToPendingReview() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"segments":[
                          {"start":"%s","end":"%s","reason":"FAULT","note":"维修"},
                          {"start":"%s","end":"%s","reason":"WAIT_MATERIAL","note":"等料"},
                          {"start":"%s","end":"%s","reason":"CHANGEOVER","note":"换线"}]}
                        """.formatted(START, MID1, MID1, MID2, MID2, END))
                .when().put("/api/events/" + eventId + "/segments")
                .then().statusCode(200)
                .body("status", is("PENDING_REVIEW"))
                .body("segments.size()", is(3))
                .body("segments[0].reason", is("FAULT"));
    }

    @Test
    @Order(5)
    void unreviewedEventExcludedFromOfficialReport() {
        given().queryParam("date", "2026-01-06").queryParam("shift", "DAY")
                .when().get("/api/reports/shift")
                .then().statusCode(200)
                .body("estimate", is(true))
                .body("eventCount", is(0))
                .body("totalEstimatedLoss", is(0f))
                .body("excludedEvents.size()", is(1))
                .body("excludedEvents[0].status", is("PENDING_REVIEW"));
    }

    @Test
    @Order(6)
    void reviewTriggersCalculationAndEntersReport() {
        given().contentType(ContentType.JSON)
                .body("{\"reviewerName\":\"测试班长\"}")
                .when().post("/api/events/" + eventId + "/review")
                .then().statusCode(200)
                .body("status", is("REVIEWED"))
                .body("calculation.standardVersion", is("v1"))
                .body("calculation.taktSeconds", is(12f))
                // 4500 秒 / 12 秒每件 = 375 件
                .body("calculation.totalEstimatedLoss", is(375f));

        given().queryParam("date", "2026-01-06").queryParam("shift", "DAY")
                .when().get("/api/reports/shift")
                .then().statusCode(200)
                .body("eventCount", is(1))
                .body("totalDowntimeSeconds", is(4500))
                .body("totalEstimatedLoss", is(375f))
                .body("reasonTotals.size()", is(3))
                .body("excludedEvents.size()", is(0));
    }

    @Test
    @Order(7)
    void reviewedEventSegmentsAreLocked() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"segments":[{"start":"%s","end":"%s","reason":"FAULT"}]}
                        """.formatted(START, END))
                .when().put("/api/events/" + eventId + "/segments")
                .then().statusCode(400)
                .body("message", containsString("已复核"));
    }
}

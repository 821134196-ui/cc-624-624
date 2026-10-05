package com.factory.downtime;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 跨班连续记录：夜班前开始的停机延续到夜班，仍属同一事件，
 * 只计入起始班次，下一班次不得重复统计。
 */
@QuarkusTest
class CrossShiftTest {

    // 2026-01-07 19:00 CST（早班内）= 11:00Z，22:00 CST（夜班内）= 14:00Z
    private static final String START = "2026-01-07T11:00:00Z";
    private static final String MID = "2026-01-07T12:30:00Z";
    private static final String END = "2026-01-07T14:00:00Z";

    @Test
    void crossShiftEventCountedOnceInOriginShiftOnly() {
        long id = given().contentType(ContentType.JSON)
                .body("""
                        {"equipmentId":2,"startTime":"%s","endTime":"%s",
                         "operatorName":"跨班测试","note":"跨班停机"}
                        """.formatted(START, END))
                .when().post("/api/events")
                .then().statusCode(200)
                .body("shiftType", is("DAY"))
                .body("crossShift", is(true))   // 结束于夜班时段，标记跨班
                .extract().jsonPath().getLong("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"segments":[
                          {"start":"%s","end":"%s","reason":"FAULT"},
                          {"start":"%s","end":"%s","reason":"WAIT_MATERIAL"}]}
                        """.formatted(START, MID, MID, END))
                .when().put("/api/events/" + id + "/segments")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"reviewerName\":\"班长\"}")
                .when().post("/api/events/" + id + "/review")
                .then().statusCode(200).body("status", is("REVIEWED"));

        // 起始班次（01-07 早班）：计入一次。10800 秒 / 25 秒每件 = 432 件
        given().queryParam("date", "2026-01-07").queryParam("shift", "DAY")
                .when().get("/api/reports/shift")
                .then().statusCode(200)
                .body("eventCount", is(1))
                .body("totalEstimatedLoss", is(432f))
                .body("events[0].crossShift", is(true));

        // 下一班次（01-07 夜班）：不得重复统计该事件
        given().queryParam("date", "2026-01-07").queryParam("shift", "NIGHT")
                .when().get("/api/reports/shift")
                .then().statusCode(200)
                .body("eventCount", is(0))
                .body("totalEstimatedLoss", is(0f));
    }
}

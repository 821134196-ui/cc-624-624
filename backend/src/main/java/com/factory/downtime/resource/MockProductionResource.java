package com.factory.downtime.resource;

import com.factory.downtime.dto.Dtos;
import com.factory.downtime.model.Equipment;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * 产量回传（本地模拟接口）。模拟 MES/计数器回传的实际产量，
 * 按 设备+班次 确定性生成，便于与估算损失对照展示。
 */
@Path("/api/mock/production")
@Produces(MediaType.APPLICATION_JSON)
public class MockProductionResource {

    @GET
    public List<Dtos.ProductionDto> production(@QueryParam("date") String date,
                                               @QueryParam("shift") String shift) {
        if (date == null || shift == null) {
            throw new IllegalArgumentException("缺少参数 date 或 shift");
        }
        return Equipment.<Equipment>listAll().stream()
                .map(e -> new Dtos.ProductionDto(e.id, e.code, e.name,
                        simulatedOutput(e.code, date, shift), "本地模拟接口"))
                .toList();
    }

    /** 确定性伪随机产量：同一设备同一班次多次调用结果一致 */
    static int simulatedOutput(String equipmentCode, String date, String shift) {
        int seed = (equipmentCode + "|" + date + "|" + shift).hashCode();
        int base = 320 + Math.floorMod(seed, 160);      // 320..479
        int jitter = Math.floorMod(seed * 31, 25);      // 0..24
        return base + jitter;
    }
}

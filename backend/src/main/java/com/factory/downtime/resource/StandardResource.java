package com.factory.downtime.resource;

import com.factory.downtime.dto.DtoMapper;
import com.factory.downtime.dto.Dtos;
import com.factory.downtime.model.CycleTimeStandard;
import com.factory.downtime.model.Equipment;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;

@Path("/api/standards")
@Produces(MediaType.APPLICATION_JSON)
public class StandardResource {

    @Inject
    DtoMapper mapper;

    public record CreateStandardReq(long equipmentId, String version, double taktSeconds,
                                    Instant effectiveFrom, String note) {
    }

    /** 节拍标准列表（全部版本，含历史） */
    @GET
    public List<Dtos.StandardDto> list(@QueryParam("equipmentId") Long equipmentId) {
        List<CycleTimeStandard> standards = equipmentId == null
                ? CycleTimeStandard.list("order by equipment.id, effectiveFrom desc")
                : CycleTimeStandard.list("equipment.id = ?1 order by effectiveFrom desc", equipmentId);
        return standards.stream().map(mapper::toDto).toList();
    }

    /** 发布新版本节拍标准（旧版本保留，历史核算仍可追溯） */
    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Dtos.StandardDto create(CreateStandardReq req) {
        Equipment equipment = Equipment.findById(req.equipmentId());
        if (equipment == null) {
            throw new IllegalArgumentException("设备不存在: " + req.equipmentId());
        }
        if (req.version() == null || req.version().isBlank()) {
            throw new IllegalArgumentException("必须填写版本号");
        }
        if (req.taktSeconds() <= 0) {
            throw new IllegalArgumentException("节拍必须为正数（秒/件）");
        }
        if (req.effectiveFrom() == null) {
            throw new IllegalArgumentException("必须填写生效时间");
        }
        long dup = CycleTimeStandard.count("equipment = ?1 and version = ?2", equipment, req.version());
        if (dup > 0) {
            throw new IllegalArgumentException("该设备下版本 " + req.version() + " 已存在");
        }
        CycleTimeStandard standard = new CycleTimeStandard();
        standard.equipment = equipment;
        standard.version = req.version();
        standard.taktSeconds = req.taktSeconds();
        standard.effectiveFrom = req.effectiveFrom();
        standard.note = req.note();
        standard.persist();
        return mapper.toDto(standard);
    }
}

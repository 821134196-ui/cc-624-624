package com.factory.downtime.dto;

import com.factory.downtime.model.*;
import com.factory.downtime.service.LossService;
import com.factory.downtime.service.ShiftService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class DtoMapper {

    @Inject
    ShiftService shiftService;

    @Inject
    LossService lossService;

    public Dtos.EquipmentDto toDto(Equipment e) {
        return new Dtos.EquipmentDto(e.id, e.code, e.name);
    }

    public Dtos.SegmentDto toDto(DowntimeSegment s) {
        return new Dtos.SegmentDto(s.id, s.seq, s.startTime, s.endTime,
                s.reason.name(), s.reason.getLabel(),
                Duration.between(s.startTime, s.endTime).getSeconds(), s.note);
    }

    public Dtos.CalcDto toDto(LossCalculation c) {
        List<Dtos.CalcSegmentDto> segs = c.segments.stream()
                .map(s -> new Dtos.CalcSegmentDto(s.seq, s.reason.name(), s.reason.getLabel(),
                        s.durationSeconds, round1(s.estimatedLoss)))
                .toList();
        return new Dtos.CalcDto(c.id, c.standard.id, c.standardVersion, c.taktSeconds,
                c.calculatedAt, round1(c.totalEstimatedLoss), c.active, segs);
    }

    /** 事件详情：含分段、当前生效核算、班次归属与跨班标记 */
    public Dtos.EventDto toDto(DowntimeEvent e, List<DowntimeSegment> segments) {
        ShiftService.Shift shift = shiftService.shiftOf(e.startTime);
        LossCalculation active = lossService.activeCalculation(e.id);
        return new Dtos.EventDto(
                e.id, e.equipment.id, e.equipment.code, e.equipment.name,
                e.startTime, e.endTime,
                e.endTime == null ? null : Duration.between(e.startTime, e.endTime).getSeconds(),
                e.status.name(), e.operatorName, e.reviewerName, e.reviewedAt, e.note,
                shift.key(), shift.date().toString(), shift.type(),
                shiftService.isCrossShift(e.startTime, e.endTime),
                segments.stream().map(this::toDto).toList(),
                active == null ? null : toDto(active));
    }

    public Dtos.StandardDto toDto(CycleTimeStandard s) {
        return new Dtos.StandardDto(s.id, s.equipment.id, s.equipment.code,
                s.version, s.taktSeconds, s.effectiveFrom, s.note);
    }

    public static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

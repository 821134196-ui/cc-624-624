package factory.downtime.api;

import factory.downtime.api.dto.Dto;
import factory.downtime.model.CalcTrigger;
import factory.downtime.model.CycleTimeStandard;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeReason;
import factory.downtime.model.DowntimeSegment;
import factory.downtime.model.Equipment;
import factory.downtime.model.EventStatus;
import factory.downtime.model.LossCalculation;
import factory.downtime.model.LossLine;
import factory.downtime.service.ConservationService;
import factory.downtime.service.ShiftService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 实体 -> 前端视图装配。
 */
@ApplicationScoped
public class ViewMapper {

    @Inject
    ShiftService shiftService;

    public Dto.EquipmentView equipment(Equipment e) {
        return new Dto.EquipmentView(e.id, e.code, e.name, e.workshop);
    }

    public Dto.SegmentView segment(DowntimeSegment s) {
        return new Dto.SegmentView(s.id, s.ordinal, s.reason.name(), s.reason.label,
                s.startTime, s.endTime,
                Duration.between(s.startTime, s.endTime).getSeconds(),
                shiftService.shiftLabel(s.startTime), s.note);
    }

    public Dto.EventView event(DowntimeEvent e, List<DowntimeSegment> segments) {
        Equipment eq = Equipment.findById(e.equipmentId);
        long total = e.endTime == null
                ? Duration.between(e.startTime, java.time.Instant.now()).getSeconds()
                : e.durationSeconds();
        long covered = segments.stream()
                .mapToLong(s -> Duration.between(s.startTime, s.endTime).getSeconds()).sum();

        List<String> shiftLabels = new ArrayList<>();
        shiftLabels.add(shiftService.shiftLabel(e.startTime));
        for (DowntimeSegment s : segments) {
            String l = shiftService.shiftLabel(s.startTime);
            if (!shiftLabels.contains(l)) shiftLabels.add(l);
        }
        if (e.endTime != null) {
            String l = shiftService.shiftLabel(e.endTime);
            if (!shiftLabels.contains(l)) shiftLabels.add(l);
        }

        List<Dto.SegmentView> segViews = segments.stream().map(this::segment).toList();
        boolean conservationValid = e.endTime != null
                && !segments.isEmpty()
                && covered == total
                && segViews.get(0).startTime().equals(e.startTime)
                && segViews.get(segViews.size() - 1).endTime().equals(e.endTime);

        return new Dto.EventView(e.id, e.eventCode, e.equipmentId,
                eq == null ? null : eq.code, eq == null ? null : eq.name,
                e.startTime, e.endTime, e.endTime == null,
                e.status.name(), e.status.label,
                e.registeredBy, e.registeredAt,
                e.segmentedBy, e.reviewedBy, e.reviewComment,
                segViews, total, covered, conservationValid, shiftLabels,
                e.currentCalcId);
    }

    public Dto.StandardView standard(CycleTimeStandard s) {
        return new Dto.StandardView(s.id, s.equipmentId, s.version, s.cycleSeconds,
                s.effectiveFrom, s.status, s.changedBy, s.changeReason,
                s.supersededByVersion, s.createdAt);
    }

    public Dto.LineView line(LossLine l) {
        return new Dto.LineView(l.lineNo, l.reason.name(), l.reason.label,
                l.startTime, l.endTime, l.durationSeconds,
                l.standardVersion, l.cycleSecondsUsed,
                round2(l.estimatedLossUnits), l.shiftLabel);
    }

    public Dto.CalcView calc(LossCalculation c) {
        List<Dto.LineView> lines = c.lines == null ? List.of()
                : c.lines.stream().sorted(java.util.Comparator.comparingInt(x -> x.lineNo))
                        .map(this::line).toList();
        String modeLabel = "HISTORICAL".equals(c.standardMode)
                ? "按当班当时标准(历史口径)" : "按当前最新标准(重算口径)";
        return new Dto.CalcView(c.id, c.batchNo, c.triggerType.name(), c.triggerType.label,
                c.standardMode, modeLabel, c.calculatedAt, c.calculatedBy,
                c.standardVersionsUsed, c.totalDurationSeconds,
                round2(c.totalEstimatedLossUnits), c.estimateDisclaimer,
                c.basisText, c.superseded, lines);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

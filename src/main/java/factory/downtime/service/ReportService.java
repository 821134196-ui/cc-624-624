package factory.downtime.service;

import factory.downtime.api.dto.Dto;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeReason;
import factory.downtime.model.Equipment;
import factory.downtime.model.LossCalculation;
import factory.downtime.model.LossLine;
import factory.downtime.model.ProductionCountback;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 正式统计报表。
 *
 * <p><b>门控</b>:仅统计 status=REVIEWED 且 currentCalcId 非空的事件,
 * 未复核/待分段事件绝不混入。报表按班次(当班)区间过滤,
 * 每台跨班事件只出现一次(同一 event),其各段通过 shiftLabel 归属展示。</p>
 *
 * <p>所有损失列均为标准节拍估算值,报表头携带估算声明;
 * 模拟实际产量单独成区并标注 SIMULATED,两者不做数值等同。</p>
 */
@ApplicationScoped
public class ReportService {

    @Inject
    LossCalculationService lossCalculationService;

    @Inject
    ShiftService shiftService;

    public record ReasonAcc(long duration, double loss, int count) {
    }

    /**
     * @param dateLabel yyyy-MM-dd
     * @param shiftName 班次名;为 null 时返回当日所有已复核事件合计
     */
    public Dto.ReportView buildShiftReport(String dateLabel, String shiftName) {
        Instant from = null, to = null;
        String label;
        if (shiftName != null && !shiftName.isBlank()) {
            Instant[] r = shiftService.shiftRange(dateLabel, shiftName);
            from = r[0];
            to = r[1];
            label = dateLabel + " " + shiftName;
        } else {
            label = dateLabel + " 全天";
            // 全天按本地日期
            java.time.LocalDate d = java.time.LocalDate.parse(dateLabel);
            from = d.atStartOfDay(shiftService.zone()).toInstant();
            to = d.plusDays(1).atStartOfDay(shiftService.zone()).toInstant();
        }

        Map<DowntimeReason, ReasonAcc> acc = new EnumMap<>(DowntimeReason.class);
        for (DowntimeReason r : DowntimeReason.values()) {
            acc.put(r, new ReasonAcc(0, 0, 0));
        }

        int eventCount = 0;
        long totalDuration = 0;
        double totalLoss = 0;
        List<Long> touchedEquipment = new ArrayList<>();

        Instant f = from, t = to;
        List<DowntimeEvent> reviewed = lossCalculationService.reviewedEventsWithCalc();
        for (DowntimeEvent event : reviewed) {
            // 跨班事件:只要与班次区间有重叠即纳入该班报表,但只统计落在区间内的分段行
            if (event.endTime.isBefore(f) || event.startTime.isAfter(t)) {
                continue;
            }
            LossCalculation calc = lossCalculationService.get(event.currentCalcId);
            boolean eventTouched = false;
            for (LossLine line : calc.lines) {
                // 用分段中点判断归属班次区间
                Instant mid = line.startTime.plusSeconds(line.durationSeconds / 2);
                if (mid.isBefore(f) || !mid.isBefore(t)) {
                    continue;
                }
                ReasonAcc a = acc.get(line.reason);
                acc.put(line.reason, new ReasonAcc(
                        a.duration + line.durationSeconds,
                        round2(a.loss + line.estimatedLossUnits), a.count + 1));
                totalDuration += line.durationSeconds;
                totalLoss += line.estimatedLossUnits;
                eventTouched = true;
            }
            if (eventTouched) {
                eventCount++;
                if (!touchedEquipment.contains(event.equipmentId)) {
                    touchedEquipment.add(event.equipmentId);
                }
            }
        }

        List<Dto.ReportRow> rows = new ArrayList<>();
        for (DowntimeReason r : DowntimeReason.values()) {
            ReasonAcc a = acc.get(r);
            rows.add(new Dto.ReportRow(r.name(), r.label, a.count, a.duration, round2(a.loss)));
        }

        // 模拟实际产量(独立分区,明确标注来源)
        List<Dto.EquipmentActual> actuals = new ArrayList<>();
        for (Long equipId : touchedEquipment) {
            long sum = sumPieces(equipId, f, t);
            Equipment e = Equipment.findById(equipId);
            actuals.add(new Dto.EquipmentActual(
                    e == null ? "#" + equipId : e.code,
                    e == null ? "设备#" + equipId : e.name,
                    (int) sum, "SIMULATED(本地模拟回传)"));
        }

        return new Dto.ReportView(label, f, t, true, eventCount,
                totalDuration, round2(totalLoss), rows,
                LossCalculationService.ESTIMATE_DISCLAIMER, actuals);
    }

    private long sumPieces(Long equipmentId, Instant from, Instant to) {
        return ProductionCountback
                .<ProductionCountback>list("equipmentId = ?1 and bucketStart >= ?2 and bucketStart < ?3",
                        equipmentId, from, to)
                .stream().mapToLong(c -> c.pieces).sum();
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

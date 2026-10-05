package factory.downtime.service;

import factory.downtime.api.BusinessException;
import factory.downtime.api.dto.Dto;
import factory.downtime.model.CalcTrigger;
import factory.downtime.model.CycleTimeStandard;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeReason;
import factory.downtime.model.DowntimeSegment;
import factory.downtime.model.EventStatus;
import factory.downtime.model.LossCalculation;
import factory.downtime.model.LossLine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产能损失核算。
 *
 * <ul>
 *   <li><b>估算口径</b>:分段估算损失 = 分段秒数 ÷ 标准节拍(秒/件),
 *       全程明确标注为估算,禁止作为实际减产数据;</li>
 *   <li><b>当班标准</b>:首算(HISTORICAL)按每个分段开始时刻生效的节拍版本,
 *       跨标准生效点的事件可同时引用多个版本;</li>
 *   <li><b>版本留痕</b>:每行快照 standardId/version/cycleSeconds,
 *       批次头部记录所用版本清单与文字依据,标准以后如何调整都不影响本批次;</li>
 *   <li><b>重算</b>:标准调整后用 CURRENT 再算,只新增批次并把旧批次标记
 *       superseded,原结果及其依据永久可查。</li>
 * </ul>
 */
@ApplicationScoped
public class LossCalculationService {

    public static final String ESTIMATE_DISCLAIMER =
            "估算数据:按标准节拍推算的理论产能损失,非实际减产记录;实际产量以数采/MES 回传为准";

    @Inject
    EventService eventService;

    @Inject
    StandardService standardService;

    @Inject
    ShiftService shiftService;

    @Inject
    ConservationService conservation;

    /**
     * 生成一版核算结果。
     *
     * @param mode HISTORICAL(当班当时标准) / CURRENT(当前最新标准)
     */
    @Transactional
    public LossCalculation calculate(Long eventId, CalcTrigger trigger, String mode,
                                     String operator) {
        DowntimeEvent event = eventService.get(eventId);
        if (event.endTime == null) {
            throw new BusinessException("EVENT_STILL_OPEN",
                    "设备尚未恢复,损失核算需在恢复后进行(跨班沿用同一事件,恢复后再核算)");
        }
        List<DowntimeSegment> segments =
                DowntimeSegment.list("eventId = ?1 order by ordinal", event.id);

        int nextBatch = (int) LossCalculation.count("eventId", eventId) + 1;

        LossCalculation calc = new LossCalculation();
        calc.eventId = event.id;
        calc.batchNo = nextBatch;
        calc.triggerType = trigger;
        calc.standardMode = "CURRENT".equalsIgnoreCase(mode) ? "CURRENT" : "HISTORICAL";
        calc.calculatedAt = Instant.now();
        calc.calculatedBy = operator;
        calc.totalDurationSeconds = event.durationSeconds();
        calc.estimateDisclaimer = ESTIMATE_DISCLAIMER;

        Map<Integer, Double> versionsUsed = new LinkedHashMap<>();
        StringBuilder basis = new StringBuilder();
        DecimalFormat df = new DecimalFormat("0.##");
        double totalLoss = 0;
        long lineNo = 0;

        for (DowntimeSegment seg : segments) {
            CycleTimeStandard std = standardService.choose(
                    event.equipmentId, seg.startTime, calc.standardMode);
            long dur = Duration.between(seg.startTime, seg.endTime).getSeconds();
            double loss = dur / std.cycleSeconds;
            totalLoss += loss;

            LossLine line = new LossLine();
            line.lineNo = (int) ++lineNo;
            line.segmentId = seg.id;
            line.reason = seg.reason;
            line.startTime = seg.startTime;
            line.endTime = seg.endTime;
            line.durationSeconds = dur;
            line.standardId = std.id;
            line.standardVersion = std.version;
            line.cycleSecondsUsed = std.cycleSeconds;
            line.estimatedLossUnits = round2(loss);
            line.shiftLabel = shiftService.shiftLabel(seg.startTime);
            calc.lines.add(line);

            versionsUsed.merge(std.version, std.cycleSeconds, (a, b) -> a);
            basis.append(String.format(
                    "第%d段[%s] %s,时长 %d 秒(%.1f 分钟) ÷ v%d 节拍 %.1f 秒/件 ≈ %s 件(%s);%n",
                    line.lineNo, line.shiftLabel, seg.reason.label, dur, dur / 60.0,
                    std.version, std.cycleSeconds, df.format(round2(loss)),
                    calc.standardMode));
        }

        calc.totalEstimatedLossUnits = round2(totalLoss);
        calc.standardVersionsUsed = versionsUsed.entrySet().stream()
                .map(e -> "v" + e.getKey() + "=" + round2(e.getValue()) + "秒/件")
                .reduce((a, b) -> a + "; " + b).orElse("");
        basis.append(String.format("合计停机 %d 秒(%.1f 分钟),估算总损失 ≈ %s 件。%s",
                calc.totalDurationSeconds, calc.totalDurationSeconds / 60.0,
                df.format(calc.totalEstimatedLossUnits), ESTIMATE_DISCLAIMER));
        calc.basisText = basis.toString();

        // 旧的当前批次标记为已取代,但保留不删
        if (event.currentCalcId != null) {
            LossCalculation old = (LossCalculation) LossCalculation.findById(event.currentCalcId);
            if (old != null) {
                old.superseded = true;
            }
        }

        calc.persist();
        LossCalculation.flush();
        for (LossLine line : calc.lines) {
            line.calculationId = calc.id;
            line.persist();
        }
        event.currentCalcId = calc.id;
        return calc;
    }

    /**
     * 复核前预检:仅校验不写库,供时间轴页面提示。
     */
    public Dto.ConservationView inspect(Long eventId) {
        DowntimeEvent event = eventService.get(eventId);
        List<DowntimeSegment> segments =
                DowntimeSegment.list("eventId = ?1 order by ordinal", eventId);
        return conservation.inspect(event, segments);
    }

    public List<LossCalculation> listBatches(Long eventId) {
        return LossCalculation.find(
                "select c from LossCalculation c left join fetch c.lines "
                        + "where c.eventId = ?1 order by c.batchNo", eventId).list();
    }

    public LossCalculation get(Long calcId) {
        return (LossCalculation) LossCalculation.find(
                "select c from LossCalculation c left join fetch c.lines where c.id = ?1",
                calcId).firstResultOptional()
                .orElseThrow(() -> BusinessException.of("核算批次不存在: #" + calcId));
    }

    /**
     * 只有 REVIEWED 且存在当前核算批次的事件,才允许进入正式统计。
     */
    public List<DowntimeEvent> reviewedEventsWithCalc() {
        List<DowntimeEvent> all = DowntimeEvent
                .list("status = ?1 and currentCalcId is not null", EventStatus.REVIEWED);
        return all;
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

package com.factory.downtime.service;

import com.factory.downtime.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 产能损失核算。估算口径：分段时长 ÷ 当班采用的标准节拍 = 估算损失件数。
 * 每次核算生成新快照并保留采用的节拍标准与版本；重算不删除历史结果。
 */
@ApplicationScoped
public class LossService {

    /** 事件起始时刻适用的节拍标准（最新生效版本） */
    public CycleTimeStandard standardAt(Equipment equipment, Instant at) {
        CycleTimeStandard standard = CycleTimeStandard.find(
                "equipment = ?1 and effectiveFrom <= ?2 order by effectiveFrom desc, id desc",
                equipment, at).firstResult();
        if (standard == null) {
            // 事件早于所有标准时，退而使用最早版本，保证可核算且依据可查
            standard = CycleTimeStandard.find(
                    "equipment = ?1 order by effectiveFrom asc, id asc", equipment).firstResult();
        }
        if (standard == null) {
            throw new IllegalStateException("设备 " + equipment.code + " 尚未配置标准节拍，无法核算");
        }
        return standard;
    }

    /**
     * 对已复核事件进行损失核算（复核时自动调用；标准变更后可再次调用重算）。
     * 旧核算结果保留（active=false），新结果成为当前生效版本。
     */
    @Transactional
    public LossCalculation calculate(DowntimeEvent event) {
        if (event.status != EventStatus.REVIEWED) {
            throw new IllegalStateException("仅已复核事件可核算损失");
        }
        List<DowntimeSegment> segments =
                DowntimeSegment.list("event = ?1 order by seq", event);
        if (segments.isEmpty()) {
            throw new IllegalStateException("事件缺少原因分段，无法核算");
        }
        CycleTimeStandard standard = standardAt(event.equipment, event.startTime);

        // 旧结果归档
        List<LossCalculation> previous = LossCalculation.list("event = ?1 and active = true", event);
        for (LossCalculation old : previous) {
            old.active = false;
        }

        LossCalculation calc = new LossCalculation();
        calc.event = event;
        calc.standard = standard;
        calc.standardVersion = standard.version;
        calc.taktSeconds = standard.taktSeconds;
        calc.active = true;

        double total = 0;
        int seq = 0;
        for (DowntimeSegment seg : segments) {
            long seconds = Duration.between(seg.startTime, seg.endTime).getSeconds();
            double loss = seconds / standard.taktSeconds;
            LossCalculationSegment row = new LossCalculationSegment();
            row.calculation = calc;
            row.seq = seq++;
            row.reason = seg.reason;
            row.durationSeconds = seconds;
            row.estimatedLoss = loss;
            calc.segments.add(row);
            total += loss;
        }
        calc.totalEstimatedLoss = total;
        calc.persist();
        return calc;
    }

    /** 当前生效的核算结果 */
    public LossCalculation activeCalculation(long eventId) {
        return LossCalculation.find("event.id = ?1 and active = true", eventId).firstResult();
    }

    /** 全部核算历史（含重算前的旧结果及其节拍依据） */
    public List<LossCalculation> history(long eventId) {
        return LossCalculation.list("event.id = ?1 order by calculatedAt desc, id desc", eventId);
    }
}

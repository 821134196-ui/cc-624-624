package com.factory.downtime.resource;

import com.factory.downtime.dto.DtoMapper;
import com.factory.downtime.dto.Dtos;
import com.factory.downtime.model.*;
import com.factory.downtime.service.DowntimeService;
import com.factory.downtime.service.LossService;
import com.factory.downtime.service.ShiftService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 班次正式统计报表。口径说明：
 * - 仅统计已复核（REVIEWED）事件，未复核事件列入 excludedEvents 不计入合计；
 * - 事件归属其开始时刻所在班次，跨班事件只计一次；
 * - 损失 = 分段时长 ÷ 当班适用的标准节拍，为估算值（estimate=true）。
 */
@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    @Inject
    ShiftService shiftService;

    @Inject
    DowntimeService downtimeService;

    @Inject
    LossService lossService;

    @Inject
    DtoMapper mapper;

    @Inject
    MockProductionResource mockProduction;

    @GET
    @Path("/shift")
    public Dtos.ShiftReportDto shiftReport(@QueryParam("date") String date,
                                           @QueryParam("shift") String shift) {
        if (date == null || shift == null) {
            throw new IllegalArgumentException("缺少参数 date 或 shift");
        }
        ShiftService.Shift s = shiftService.shiftOf(date, shift);

        List<DowntimeEvent> startingInShift = DowntimeEvent.list(
                "startTime >= ?1 and startTime < ?2 order by startTime", s.start(), s.end());

        List<DowntimeEvent> reviewed = startingInShift.stream()
                .filter(e -> e.status == EventStatus.REVIEWED).toList();
        List<DowntimeEvent> excluded = startingInShift.stream()
                .filter(e -> e.status != EventStatus.REVIEWED).toList();

        // 按原因汇总（仅已复核事件的当前生效核算）
        Map<ReasonCode, long[]> secondsByReason = new EnumMap<>(ReasonCode.class);
        Map<ReasonCode, Double> lossByReason = new EnumMap<>(ReasonCode.class);
        double totalLoss = 0;
        long totalSeconds = 0;

        List<Dtos.EventDto> eventDtos = new ArrayList<>();
        for (DowntimeEvent e : reviewed) {
            List<DowntimeSegment> segments = downtimeService.segmentsOf(e.id);
            eventDtos.add(mapper.toDto(e, segments));
            LossCalculation calc = lossService.activeCalculation(e.id);
            if (calc == null) {
                continue;
            }
            for (LossCalculationSegment row : calc.segments) {
                secondsByReason.computeIfAbsent(row.reason, k -> new long[1])[0] += row.durationSeconds;
                lossByReason.merge(row.reason, row.estimatedLoss, Double::sum);
                totalSeconds += row.durationSeconds;
                totalLoss += row.estimatedLoss;
            }
        }

        List<Dtos.ReasonTotalDto> reasonTotals = Arrays.stream(ReasonCode.values())
                .filter(r -> secondsByReason.containsKey(r))
                .map(r -> new Dtos.ReasonTotalDto(r.name(), r.getLabel(),
                        secondsByReason.get(r)[0],
                        DtoMapper.round1(lossByReason.getOrDefault(r, 0.0))))
                .collect(Collectors.toList());

        List<Dtos.EventDto> excludedDtos = excluded.stream()
                .map(e -> mapper.toDto(e, downtimeService.segmentsOf(e.id)))
                .toList();

        return new Dtos.ShiftReportDto(
                s.key(), s.date().toString(), s.type(), s.start(), s.end(),
                true, // 损失为估算值，报表固定标注
                reviewed.size(),
                totalSeconds,
                DtoMapper.round1(totalLoss),
                reasonTotals,
                eventDtos,
                excludedDtos,
                mockProduction.production(date, shift));
    }
}

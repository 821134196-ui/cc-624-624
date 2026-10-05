package com.factory.downtime.dto;

import java.time.Instant;
import java.util.List;

/** API 传输对象集合 */
public final class Dtos {

    private Dtos() {
    }

    public record EquipmentDto(long id, String code, String name) {
    }

    public record SegmentDto(Long id, int seq, Instant start, Instant end,
                             String reason, String reasonLabel,
                             long durationSeconds, String note) {
    }

    public record CalcSegmentDto(int seq, String reason, String reasonLabel,
                                 long durationSeconds, double estimatedLoss) {
    }

    public record CalcDto(long id, long standardId, String standardVersion, double taktSeconds,
                          Instant calculatedAt, double totalEstimatedLoss, boolean active,
                          List<CalcSegmentDto> segments) {
    }

    public record EventDto(long id, long equipmentId, String equipmentCode, String equipmentName,
                           Instant startTime, Instant endTime, Long durationSeconds,
                           String status, String operatorName, String reviewerName,
                           Instant reviewedAt, String note,
                           String shiftKey, String shiftDate, String shiftType, boolean crossShift,
                           List<SegmentDto> segments, CalcDto calculation) {
    }

    public record StandardDto(long id, long equipmentId, String equipmentCode,
                              String version, double taktSeconds,
                              Instant effectiveFrom, String note) {
    }

    public record ReasonTotalDto(String reason, String reasonLabel,
                                 long durationSeconds, double estimatedLoss) {
    }

    public record ProductionDto(long equipmentId, String equipmentCode, String equipmentName,
                                int actualOutput, String source) {
    }

    /**
     * 班次正式统计报表。estimate 恒为 true：损失为按标准节拍的估算值，
     * 不能当作实际减产数据。excludedEvents 为未复核、不计入统计的事件。
     */
    public record ShiftReportDto(String shiftKey, String date, String shiftType,
                                 Instant shiftStart, Instant shiftEnd,
                                 boolean estimate,
                                 int eventCount,
                                 long totalDowntimeSeconds,
                                 double totalEstimatedLoss,
                                 List<ReasonTotalDto> reasonTotals,
                                 List<EventDto> events,
                                 List<EventDto> excludedEvents,
                                 List<ProductionDto> production) {
    }
}

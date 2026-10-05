package factory.downtime.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 接口请求/响应视图。时间统一以 ISO-8601(UTC)传输,前端按车间时区展示。
 */
public final class Dto {

    private Dto() {
    }

    // ---------- 请求 ----------

    public record RegisterEventRequest(Long equipmentId, String equipmentCode,
                                       Instant startTime, Instant endTime,
                                       String registeredBy) {
    }

    public record ResolveEventRequest(Instant endTime) {
    }

    public record SegmentInput(String reason, Instant startTime, Instant endTime, String note) {
    }

    public record SaveSegmentsRequest(String confirmedBy, List<SegmentInput> segments) {
    }

    public record ReviewRequest(String reviewedBy, String comment) {
    }

    public record RecalcRequest(String mode, String calculatedBy) {
    }

    public record StandardRequest(Double cycleSeconds, Instant effectiveFrom,
                                  String changedBy, String changeReason) {
    }

    // ---------- 响应 ----------

    public record EquipmentView(Long id, String code, String name, String workshop) {
    }

    public record SegmentView(Long id, int ordinal, String reason, String reasonLabel,
                              Instant startTime, Instant endTime, long durationSeconds,
                              String shiftLabel, String note) {
    }

    public record EventView(Long id, String eventCode, Long equipmentId,
                            String equipmentCode, String equipmentName,
                            Instant startTime, Instant endTime, boolean open,
                            String status, String statusLabel,
                            String registeredBy, Instant registeredAt,
                            String segmentedBy, String reviewedBy, String reviewComment,
                            List<SegmentView> segments,
                            long totalDurationSeconds, long coveredSeconds,
                            boolean conservationValid, List<String> shiftLabels,
                            Long currentCalcId) {
    }

    public record StandardView(Long id, Long equipmentId, int version, double cycleSeconds,
                               Instant effectiveFrom, String status, String changedBy,
                               String changeReason, Integer supersededByVersion,
                               Instant createdAt) {
    }

    public record LineView(int lineNo, String reason, String reasonLabel,
                           Instant startTime, Instant endTime, long durationSeconds,
                           Integer standardVersion, double cycleSecondsUsed,
                           double estimatedLossUnits, String shiftLabel) {
    }

    public record CalcView(Long id, int batchNo, String triggerType, String triggerLabel,
                           String standardMode, String standardModeLabel,
                           Instant calculatedAt, String calculatedBy,
                           String standardVersionsUsed, long totalDurationSeconds,
                           double totalEstimatedLossUnits, String estimateDisclaimer,
                           String basisText, boolean superseded, List<LineView> lines) {
    }

    public record ReportRow(String reason, String reasonLabel, int segmentCount,
                            long durationSeconds, double estimatedLossUnits) {
    }

    public record ReportView(String shiftLabel, Instant from, Instant to,
                             boolean reviewedOnly, int eventCount,
                             long totalDurationSeconds, double totalEstimatedLossUnits,
                             List<ReportRow> byReason, String disclaimer,
                             List<EquipmentActual> simulatedActual) {
    }

    public record EquipmentActual(String equipmentCode, String equipmentName,
                                  int simulatedPieces, String source) {
    }

    public record CountbackView(Instant bucketStart, int pieces, String source) {
    }

    public record ConservationView(boolean valid, long eventDurationSeconds,
                                   long coveredSeconds, long gapSeconds,
                                   List<String> problems) {
    }

    public record ContinuityView(String eventCode, boolean open, boolean crossesShifts,
                                 List<String> shiftLabels, String note) {
    }
}

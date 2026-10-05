package com.factory.downtime.service;

import com.factory.downtime.model.ReasonCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 分段时长守恒校验 */
class SegmentValidatorTest {

    private static final Instant T0 = Instant.parse("2026-01-06T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-06T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-06T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-06T11:00:00Z");

    private static SegmentValidator.SegmentInput seg(Instant s, Instant e, ReasonCode r) {
        return new SegmentValidator.SegmentInput(s, e, r, null);
    }

    @Test
    void validContiguousSegmentsPass() {
        assertDoesNotThrow(() -> SegmentValidator.validate(T0, T3, List.of(
                seg(T0, T1, ReasonCode.FAULT),
                seg(T1, T2, ReasonCode.WAIT_MATERIAL),
                seg(T2, T3, ReasonCode.CHANGEOVER))));
    }

    @Test
    void singleSegmentCoveringWholeDowntimePasses() {
        assertDoesNotThrow(() -> SegmentValidator.validate(T0, T3, List.of(
                seg(T0, T3, ReasonCode.FAULT))));
    }

    @Test
    void overlappingSegmentsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SegmentValidator.validate(T0, T3, List.of(
                        seg(T0, T2, ReasonCode.FAULT),
                        seg(T1, T3, ReasonCode.WAIT_MATERIAL))));
        assertTrue(ex.getMessage().contains("重叠") || ex.getMessage().contains("空白"));
    }

    @Test
    void gapBetweenSegmentsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SegmentValidator.validate(T0, T3, List.of(
                        seg(T0, T1, ReasonCode.FAULT),
                        seg(T2, T3, ReasonCode.WAIT_MATERIAL))));
        assertTrue(ex.getMessage().contains("空白"));
    }

    @Test
    void tailNotCoveredRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SegmentValidator.validate(T0, T3, List.of(
                        seg(T0, T1, ReasonCode.FAULT),
                        seg(T1, T2, ReasonCode.WAIT_MATERIAL))));
        assertTrue(ex.getMessage().contains("空白"));
    }

    @Test
    void segmentOutsideEventRangeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SegmentValidator.validate(T0, T2, List.of(
                        seg(T0, T3, ReasonCode.FAULT))));
    }

    @Test
    void emptySegmentsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SegmentValidator.validate(T0, T3, List.of()));
    }

    @Test
    void openEndedEventRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SegmentValidator.validate(T0, null, List.of(
                        seg(T0, T1, ReasonCode.FAULT))));
    }

    @Test
    void zeroLengthSegmentRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SegmentValidator.validate(T0, T3, List.of(
                        seg(T0, T0, ReasonCode.FAULT),
                        seg(T0, T3, ReasonCode.FAULT))));
    }

    @Test
    void unorderedInputIsSortedBeforeValidation() {
        // 前端可能按任意顺序提交，校验前应按开始时间排序
        assertDoesNotThrow(() -> SegmentValidator.validate(T0, T3, List.of(
                seg(T2, T3, ReasonCode.CHANGEOVER),
                seg(T0, T1, ReasonCode.FAULT),
                seg(T1, T2, ReasonCode.WAIT_MATERIAL))));
    }
}

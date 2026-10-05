package com.factory.downtime.service;

import com.factory.downtime.model.ReasonCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 分段守恒校验：同一时间轴上的分段必须
 * 1) 至少一段；2) 每段 start &lt; end；3) 不重叠；4) 不留空白；
 * 5) 首段起点=事件起点、末段终点=事件终点；6) 合计=整次停机时长。
 */
public final class SegmentValidator {

    /** 待校验的分段输入 */
    public record SegmentInput(Instant start, Instant end, ReasonCode reason, String note) {
    }

    private SegmentValidator() {
    }

    /**
     * 校验分段集合是否完整覆盖 [eventStart, eventEnd]。
     *
     * @throws IllegalArgumentException 校验失败，消息说明具体原因
     */
    public static void validate(Instant eventStart, Instant eventEnd, List<SegmentInput> segments) {
        if (eventEnd == null) {
            throw new IllegalArgumentException("停机尚未结束，请先登记恢复时间再分段");
        }
        if (!eventStart.isBefore(eventEnd)) {
            throw new IllegalArgumentException("停机起止时间无效：开始必须早于结束");
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("至少需要一段原因分段");
        }

        List<SegmentInput> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparing(SegmentInput::start));

        long total = 0;
        Instant cursor = eventStart;
        for (int i = 0; i < sorted.size(); i++) {
            SegmentInput seg = sorted.get(i);
            if (seg.reason() == null) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 段缺少停机原因");
            }
            if (seg.start() == null || seg.end() == null || !seg.start().isBefore(seg.end())) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 段起止时间无效");
            }
            if (seg.start().isBefore(eventStart) || seg.end().isAfter(eventEnd)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 段超出停机起止范围");
            }
            if (seg.start().isBefore(cursor)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 段与上一段重叠");
            }
            if (seg.start().isAfter(cursor)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 段之前存在未分配原因的空白时间");
            }
            total += Duration.between(seg.start(), seg.end()).getSeconds();
            cursor = seg.end();
        }
        if (!cursor.equals(eventEnd)) {
            throw new IllegalArgumentException("分段末尾未覆盖到停机结束时间，存在空白");
        }

        long expected = Duration.between(eventStart, eventEnd).getSeconds();
        if (total != expected) {
            throw new IllegalArgumentException(
                    "分段合计 " + total + " 秒与停机总时长 " + expected + " 秒不一致");
        }
    }
}

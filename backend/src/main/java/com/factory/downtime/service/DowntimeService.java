package com.factory.downtime.service;

import com.factory.downtime.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class DowntimeService {

    @Inject
    EntityManager em;

    @Inject
    LossService lossService;

    /** 操作员登记停机（endTime 可空，表示仍在停机） */
    @Transactional
    public DowntimeEvent createEvent(long equipmentId, Instant start, Instant end,
                                     String operatorName, String note) {
        Equipment equipment = Equipment.findById(equipmentId);
        if (equipment == null) {
            throw new IllegalArgumentException("设备不存在: " + equipmentId);
        }
        if (start == null) {
            throw new IllegalArgumentException("必须填写停机开始时间");
        }
        if (end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("停机开始时间必须早于结束时间");
        }
        DowntimeEvent event = new DowntimeEvent();
        event.equipment = equipment;
        event.startTime = start;
        event.endTime = end;
        event.operatorName = operatorName;
        event.note = note;
        event.status = EventStatus.OPEN;
        event.persist();
        return event;
    }

    /** 操作员补登/修改恢复时间（仅 OPEN 状态） */
    @Transactional
    public DowntimeEvent updateTimes(long eventId, Instant start, Instant end) {
        DowntimeEvent event = requireEvent(eventId);
        requireStatus(event, EventStatus.OPEN, "仅登记中的事件可修改起止时间");
        if (start == null) {
            throw new IllegalArgumentException("必须填写停机开始时间");
        }
        if (end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("停机开始时间必须早于结束时间");
        }
        event.startTime = start;
        event.endTime = end;
        // 起止变化后原有分段不再守恒，清空待班长重新分段
        DowntimeSegment.delete("event", event);
        return event;
    }

    /** 班长在同一时间轴上提交分段（整体替换，服务端做强守恒校验） */
    @Transactional
    public DowntimeEvent saveSegments(long eventId, List<SegmentValidator.SegmentInput> inputs) {
        DowntimeEvent event = requireEvent(eventId);
        if (event.status == EventStatus.REVIEWED) {
            throw new IllegalArgumentException("事件已复核，分段不可再修改");
        }
        SegmentValidator.validate(event.startTime, event.endTime, inputs);

        DowntimeSegment.delete("event", event);
        for (int i = 0; i < inputs.size(); i++) {
            SegmentValidator.SegmentInput in = inputs.get(i);
            DowntimeSegment seg = new DowntimeSegment();
            seg.event = event;
            seg.seq = i;
            seg.startTime = in.start();
            seg.endTime = in.end();
            seg.reason = in.reason();
            seg.note = in.note();
            seg.persist();
        }
        if (event.status == EventStatus.OPEN) {
            event.status = EventStatus.PENDING_REVIEW;
        }
        return event;
    }

    /** 班长复核：分段守恒已在提交时校验，复核后进入正式统计并生成损失核算 */
    @Transactional
    public DowntimeEvent review(long eventId, String reviewerName) {
        DowntimeEvent event = requireEvent(eventId);
        requireStatus(event, EventStatus.PENDING_REVIEW, "仅待复核状态的事件可复核");
        long segCount = DowntimeSegment.count("event", event);
        if (segCount == 0) {
            throw new IllegalArgumentException("缺少原因分段，不能复核");
        }
        event.status = EventStatus.REVIEWED;
        event.reviewerName = reviewerName;
        event.reviewedAt = Instant.now();
        lossService.calculate(event);
        return event;
    }

    /** 退回待复核事件为登记中（允许重新分段） */
    @Transactional
    public DowntimeEvent reopen(long eventId) {
        DowntimeEvent event = requireEvent(eventId);
        requireStatus(event, EventStatus.PENDING_REVIEW, "仅待复核状态的事件可退回");
        event.status = EventStatus.OPEN;
        return event;
    }

    public DowntimeEvent requireEvent(long id) {
        DowntimeEvent event = DowntimeEvent.findById(id);
        if (event == null) {
            throw new IllegalArgumentException("停机事件不存在: " + id);
        }
        return event;
    }

    private static void requireStatus(DowntimeEvent event, EventStatus expected, String message) {
        if (event.status != expected) {
            throw new IllegalArgumentException(message + "（当前状态: " + event.status + "）");
        }
    }

    public List<DowntimeSegment> segmentsOf(long eventId) {
        return DowntimeSegment.list("event.id = ?1 order by seq", eventId);
    }
}

package factory.downtime.service;

import factory.downtime.api.BusinessException;
import factory.downtime.api.dto.Dto;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeReason;
import factory.downtime.model.DowntimeSegment;
import factory.downtime.model.EventStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 班长在同一时间轴上分段确认原因:整段保存、全量替换。
 * 保存即做守恒校验;已复核事件若重新编辑分段则回退到待复核,
 * 原核算批次保留但不再是当前结果(需要重新复核)。
 */
@ApplicationScoped
public class SegmentService {

    @Inject
    ConservationService conservation;

    @Inject
    EventService eventService;

    @Transactional
    public DowntimeEvent saveSegments(Long eventId, Dto.SaveSegmentsRequest req) {
        DowntimeEvent event = eventService.get(eventId);

        if (event.endTime == null) {
            throw new BusinessException("EVENT_STILL_OPEN",
                    "设备尚未恢复,时间轴终点未定。请先登记恢复时间"
                            + "(跨班停机在设备恢复后回到本事件补录,仍为同一事件)");
        }
        if (req.segments() == null || req.segments().isEmpty()) {
            throw BusinessException.of("至少需要划分一个原因分段");
        }

        List<DowntimeSegment> incoming = new ArrayList<>();
        Instant now = Instant.now();
        for (Dto.SegmentInput in : req.segments()) {
            DowntimeReason reason = parseReason(in.reason());
            Instant s = ConservationService.truncateToSecond(in.startTime());
            Instant e = ConservationService.truncateToSecond(in.endTime());
            if (s == null || e == null) {
                throw BusinessException.of("每段都必须填写起止时间");
            }
            DowntimeSegment seg = new DowntimeSegment();
            seg.eventId = event.id;
            seg.reason = reason;
            seg.startTime = s;
            seg.endTime = e;
            seg.note = in.note();
            seg.confirmedBy = req.confirmedBy();
            seg.confirmedAt = now;
            incoming.add(seg);
        }

        // 核心校验:无重叠、无留白、合计 = 整次停机
        conservation.requireConserved(event, incoming);

        incoming.sort(Comparator.comparing(x -> x.startTime));
        DowntimeSegment.delete("eventId", eventId);
        DowntimeSegment.flush();
        for (int i = 0; i < incoming.size(); i++) {
            DowntimeSegment seg = incoming.get(i);
            seg.ordinal = i + 1;
            seg.persist();
        }

        event.segmentedBy = req.confirmedBy();
        event.segmentedAt = now;
        // 已复核事件修改了分段,需要重新复核;旧核算结果仍保留在历史批次中
        boolean wasReviewed = event.status == EventStatus.REVIEWED;
        event.status = EventStatus.SEGMENTED;
        if (wasReviewed) {
            // currentCalcId 指向的批次改为"已被取代",但记录本身保留
            event.currentCalcId = null;
        }
        return event;
    }

    public List<DowntimeSegment> list(Long eventId) {
        return DowntimeSegment.list("eventId = ?1 order by ordinal", eventId);
    }

    public static DowntimeReason parseReason(String raw) {
        if (raw == null) {
            throw BusinessException.of("原因为必填");
        }
        String r = raw.trim();
        for (DowntimeReason v : DowntimeReason.values()) {
            if (v.name().equals(r) || v.label.equals(r)) {
                return v;
            }
        }
        throw BusinessException.of("未知停机原因: " + raw + "(允许: 故障/待料/换线)");
    }
}

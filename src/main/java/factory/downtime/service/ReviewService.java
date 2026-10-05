package factory.downtime.service;

import factory.downtime.api.BusinessException;
import factory.downtime.model.CalcTrigger;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeSegment;
import factory.downtime.model.EventStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * 班长复核门控。
 *
 * <p>复核是进入正式统计的唯一入口:复核时再次强制执行时间轴守恒校验,
 * 通过后生成首版损失核算(HISTORICAL 当班标准),事件置 REVIEWED。
 * 未复核(REGISTERED / SEGMENTED)事件一律不进入任何正式报表。</p>
 */
@ApplicationScoped
public class ReviewService {

    @Inject
    EventService eventService;

    @Inject
    SegmentService segmentService;

    @Inject
    ConservationService conservation;

    @Inject
    LossCalculationService lossCalculationService;

    @Transactional
    public DowntimeEvent review(Long eventId, String reviewer, String comment) {
        DowntimeEvent event = eventService.get(eventId);
        if (event.endTime == null) {
            throw new BusinessException("EVENT_STILL_OPEN",
                    "设备尚未恢复,不能复核;请在同一事件上登记恢复时间后再复核");
        }
        List<DowntimeSegment> segments = segmentService.list(eventId);
        // 复核前最后一道守恒校验(双重保险)
        conservation.requireConserved(event, segments);

        event.status = EventStatus.REVIEWED;
        event.reviewedBy = reviewer;
        event.reviewedAt = Instant.now();
        event.reviewComment = comment;

        // 复核通过即按当班标准做首算
        lossCalculationService.calculate(eventId, CalcTrigger.REVIEW, "HISTORICAL", reviewer);
        return event;
    }

    /**
     * 班长驳回分段:回到待分段状态(不删除已录分段,便于修改)。
     */
    @Transactional
    public DowntimeEvent reject(Long eventId, String reviewer, String comment) {
        DowntimeEvent event = eventService.get(eventId);
        if (event.status == EventStatus.REVIEWED) {
            throw BusinessException.of("已复核事件请先在分段页修改,系统将自动要求重新复核");
        }
        event.status = EventStatus.REGISTERED;
        event.reviewedBy = reviewer;
        event.reviewComment = "驳回: " + comment;
        return event;
    }
}

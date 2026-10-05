package factory.downtime.service;

import factory.downtime.api.BusinessException;
import factory.downtime.api.dto.Dto;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeSegment;
import factory.downtime.model.Equipment;
import factory.downtime.model.EventStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 停机事件登记 / 恢复 / 查询。
 *
 * <p>跨班连续性保证:同一设备只允许存在一条 endTime 为空的事件;
 * 跨班未恢复时操作员只能在原事件上回填恢复时间,无法新建第二条 ——
 * 因此一次停机不会在班次边界被拆成两次独立故障重复统计。</p>
 */
@ApplicationScoped
public class EventService {

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicInteger seq = new AtomicInteger(0);

    @Inject
    ShiftService shiftService;

    @Inject
    ConservationService conservation;

    @Transactional
    public DowntimeEvent register(Dto.RegisterEventRequest req) {
        Equipment equipment = resolveEquipment(req.equipmentId(), req.equipmentCode());

        // 跨班/未恢复事件唯一性:同一设备有开放事件时禁止再次登记
        DowntimeEvent open = DowntimeEvent.findOpenByEquipment(equipment.id);
        if (open != null) {
            throw new BusinessException("EVENT_STILL_OPEN",
                    "设备 " + equipment.code + " 的停机事件 " + open.eventCode
                            + " 尚未恢复,必须沿用同一事件(跨班连续),不能重复登记为独立故障");
        }

        Instant now = Instant.now();
        Instant start = ConservationService.truncateToSecond(
                req.startTime() != null ? req.startTime() : now);
        Instant end = ConservationService.truncateToSecond(req.endTime());
        if (end != null && !end.isAfter(start)) {
            throw BusinessException.of("恢复时间必须晚于停机开始时间");
        }

        DowntimeEvent event = new DowntimeEvent();
        event.eventCode = generateCode(start);
        event.equipmentId = equipment.id;
        event.startTime = start;
        event.endTime = end;
        event.registeredBy = req.registeredBy();
        event.registeredAt = now;
        event.status = EventStatus.REGISTERED;
        event.persist();
        return event;
    }

    /**
     * 设备恢复:回填同一事件的恢复时间(可跨班)。
     */
    @Transactional
    public DowntimeEvent resolve(Long eventId, Dto.ResolveEventRequest req) {
        DowntimeEvent event = get(eventId);
        if (event.endTime != null) {
            throw BusinessException.of("事件 " + event.eventCode + " 已恢复,无需重复操作");
        }
        Instant end = ConservationService.truncateToSecond(
                req.endTime() != null ? req.endTime() : Instant.now());
        if (!end.isAfter(event.startTime)) {
            throw BusinessException.of("恢复时间必须晚于停机开始时间");
        }
        event.endTime = end;
        // 已保存的分段若不再守恒(理论上不会),复核时还会再次拦截
        return event;
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        DowntimeEvent event = get(eventId);
        if (event.status == EventStatus.REVIEWED) {
            throw BusinessException.of("已复核事件不可删除");
        }
        DowntimeSegment.delete("eventId", eventId);
        event.delete();
    }

    public DowntimeEvent get(Long id) {
        return (DowntimeEvent) DowntimeEvent.findByIdOptional(id)
                .orElseThrow(() -> BusinessException.of("停机事件不存在: #" + id));
    }

    public List<DowntimeEvent> listAllEvents() {
        return DowntimeEvent.listAll();
    }

    public Equipment resolveEquipment(Long id, String code) {
        if (id != null) {
            return (Equipment) Equipment.findByIdOptional(id)
                    .orElseThrow(() -> BusinessException.of("设备不存在: #" + id));
        }
        if (code != null && !code.isBlank()) {
            Equipment e = (Equipment) Equipment.find("code", code.trim()).firstResultOptional().orElse(null);
            if (e == null) {
                throw BusinessException.of("设备不存在: " + code);
            }
            return e;
        }
        throw BusinessException.of("必须指定设备");
    }

    /**
     * 判断事件是否跨越班次边界(经过的不同班次标签数 > 1)。
     * 不依赖分段是否已录入:至少对停机起点、恢复时刻采样;
     * 已分段时再叠加各分段起点,保证跨班在分段前也能被识别。
     */
    public Dto.ContinuityView continuity(DowntimeEvent event) {
        List<DowntimeSegment> segments = DowntimeSegment
                .list("eventId = ?1 order by ordinal", event.id);
        List<String> labels = new java.util.ArrayList<>();
        labels.add(shiftService.shiftLabel(event.startTime));
        for (DowntimeSegment s : segments) {
            String l = shiftService.shiftLabel(s.startTime);
            if (!labels.contains(l)) labels.add(l);
        }
        if (event.endTime != null) {
            String endLabel = shiftService.shiftLabel(event.endTime);
            if (!labels.contains(endLabel)) labels.add(endLabel);
        }
        boolean crosses = labels.size() > 1;
        String note = event.endTime == null
                ? "设备尚未恢复,事件保持开放,跨班沿用 " + event.eventCode + " 不拆分"
                : (crosses ? "事件跨越班次: " + String.join(" → ", labels) + ",全程为同一事件"
                           : "事件位于单个班次内");
        return new Dto.ContinuityView(event.eventCode, event.endTime == null, crosses, labels, note);
    }

    private synchronized String generateCode(Instant start) {
        // 同日序号取当天已有事件数 + 当日进程内序号,保证唯一
        ZoneId zone = shiftService.zone();
        String day = CODE_FMT.format(LocalDate.ofInstant(start, zone));
        long countToday = DowntimeEvent.count("eventCode like ?1", "DT-" + day + "-%");
        int n = (int) Math.max(countToday + 1, seq.incrementAndGet() % 1000 + 1);
        // seq 只做进程内防碰撞;若与库内重号则递增
        String code = String.format("DT-%s-%03d", day, countToday + 1);
        while (DowntimeEvent.count("eventCode", code) > 0) {
            n++;
            code = String.format("DT-%s-%03d", day, n);
        }
        return code;
    }
}

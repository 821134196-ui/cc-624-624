package com.factory.downtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.*;

/**
 * 班次模型：早班 08:00-20:00，夜班 20:00-次日 08:00（时间可配置）。
 * 停机事件归属于其开始时刻所在的班次；跨班事件仍只归属起始班次，
 * 保证同一事件不会被两个班次各计一次。
 */
@ApplicationScoped
public class ShiftService {

    public static final String DAY = "DAY";
    public static final String NIGHT = "NIGHT";

    /** 业务统一使用车间时区 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @ConfigProperty(name = "downtime.shift.day-start", defaultValue = "08:00")
    String dayStart;

    @ConfigProperty(name = "downtime.shift.night-start", defaultValue = "20:00")
    String nightStart;

    public record Shift(String type, LocalDate date, Instant start, Instant end) {
        public String key() {
            return date + "-" + type;
        }
    }

    /** 某时刻所属的班次 */
    public Shift shiftOf(Instant instant) {
        ZonedDateTime zdt = instant.atZone(ZONE);
        LocalTime dayT = LocalTime.parse(dayStart);
        LocalTime nightT = LocalTime.parse(nightStart);
        LocalDate d = zdt.toLocalDate();
        LocalTime t = zdt.toLocalTime();

        if (!t.isBefore(dayT) && t.isBefore(nightT)) {
            return build(DAY, d, dayT, nightT);
        }
        // 夜班：20:00 之后属于当日晚班；00:00-08:00 属于前一日晚班
        LocalDate shiftDate = t.isBefore(dayT) ? d.minusDays(1) : d;
        return build(NIGHT, shiftDate, nightT, dayT);
    }

    /** 按日期与班次类型构造班次 */
    public Shift shiftOf(String date, String type) {
        LocalDate d = LocalDate.parse(date);
        LocalTime dayT = LocalTime.parse(dayStart);
        LocalTime nightT = LocalTime.parse(nightStart);
        if (DAY.equals(type)) {
            return build(DAY, d, dayT, nightT);
        }
        if (NIGHT.equals(type)) {
            return build(NIGHT, d, nightT, dayT);
        }
        throw new IllegalArgumentException("未知班次类型: " + type + "（应为 DAY 或 NIGHT）");
    }

    private Shift build(String type, LocalDate date, LocalTime startT, LocalTime endT) {
        LocalDateTime start = LocalDateTime.of(date, startT);
        LocalDateTime end = DAY.equals(type)
                ? LocalDateTime.of(date, endT)
                : LocalDateTime.of(date.plusDays(1), endT);
        return new Shift(type, date, start.atZone(ZONE).toInstant(), end.atZone(ZONE).toInstant());
    }

    /** 事件是否跨班（结束时间超出起始班次，或尚未恢复） */
    public boolean isCrossShift(Instant start, Instant end) {
        Shift s = shiftOf(start);
        return end == null || end.isAfter(s.end());
    }
}

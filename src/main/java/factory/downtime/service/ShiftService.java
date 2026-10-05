package factory.downtime.service;

import factory.downtime.api.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 班次解析:根据 app.shifts 配置判断某时刻归属哪个班次。
 * 跨零点班次(结束 HH:mm 小于开始 HH:mm)按开始日命名,
 * 例如 20:00-08:00 的夜班,凌晨 02:00 归属前一天的夜班。
 */
@ApplicationScoped
public class ShiftService {

    public record ShiftDef(String name, LocalTime start, LocalTime end) {
        boolean crossesMidnight() {
            return end.isBefore(start) || end.equals(start);
        }
    }

    @Inject
    @ConfigProperty(name = "app.zone", defaultValue = "Asia/Shanghai")
    String zoneId;

    private ZoneId zoneCache;

    public ZoneId zone() {
        if (zoneCache == null) {
            zoneCache = ZoneId.of(zoneId);
        }
        return zoneCache;
    }

    private final List<ShiftDef> shifts = new ArrayList<>();

    @Inject
    public ShiftService(@ConfigProperty(name = "app.shifts") String shiftConfig) {
        // 形如:早班=08:00-20:00,夜班=20:00-08:00
        for (String part : shiftConfig.split(",")) {
            String[] kv = part.trim().split("=");
            String[] se = kv[1].split("-");
            shifts.add(new ShiftDef(kv[0].trim(),
                    LocalTime.parse(se[0].trim()), LocalTime.parse(se[1].trim())));
        }
    }

    /**
     * 返回班次标签,如 "2026-10-04 夜班";日期为该班次的开始日。
     */
    public String shiftLabel(Instant at) {
        LocalDateTime ldt = LocalDateTime.ofInstant(at, zone());
        LocalTime t = ldt.toLocalTime();
        for (ShiftDef s : shifts) {
            boolean belongs = s.crossesMidnight()
                    ? !t.isBefore(s.start) || t.isBefore(s.end)
                    : (!t.isBefore(s.start) && t.isBefore(s.end));
            if (belongs) {
                LocalDate shiftDate = ldt.toLocalDate();
                if (s.crossesMidnight() && t.isBefore(s.end())) {
                    shiftDate = shiftDate.minusDays(1); // 凌晨归属前一晚开始的班次
                }
                return shiftDate + " " + s.name;
            }
        }
        // 未落在任何定义区间时归到第一个班次名
        ShiftDef first = shifts.get(0);
        return ldt.toLocalDate() + " " + first.name;
    }

    /**
     * 某班次标签的起止时刻(用于报表过滤)。
     */
    public Instant[] shiftRange(String dateLabel, String shiftName) {
        LocalDate date = LocalDate.parse(dateLabel);
        ShiftDef def = shifts.stream()
                .filter(s -> s.name.equals(shiftName))
                .findFirst()
                .orElseThrow(() -> BusinessException.of("未知班次: " + shiftName));
        Instant start = LocalDateTime.of(date, def.start).atZone(zone()).toInstant();
        LocalDate endDate = def.crossesMidnight() ? date.plusDays(1) : date;
        Instant end = LocalDateTime.of(endDate, def.end).atZone(zone()).toInstant();
        return new Instant[]{start, end};
    }

    public List<String> shiftNames() {
        return shifts.stream().map(s -> s.name).toList();
    }

    public long secondsBetween(Instant a, Instant b) {
        return Duration.between(a, b).getSeconds();
    }
}

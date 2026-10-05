package factory.downtime.service;

import factory.downtime.api.BusinessException;
import factory.downtime.api.dto.Dto;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeSegment;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时间轴分段守恒校验(本系统的核心不变量):
 * <ol>
 *   <li>每段结束必须晚于开始;</li>
 *   <li>按开始时间排序后,第一段起点必须等于停机起点(无前留白);</li>
 *   <li>相邻段必须首尾严格相接:上一段结束 = 下一段开始(不重叠、不留白);</li>
 *   <li>最后一段终点必须等于停机恢复时间(无后留白);</li>
 *   <li>各段时长合计必须等于整次停机时长。</li>
 * </ol>
 * 秒级精度比较;输入时间统一截断到秒。
 */
@ApplicationScoped
public class ConservationService {

    public static Instant truncateToSecond(Instant t) {
        return t == null ? null : Instant.ofEpochSecond(t.getEpochSecond());
    }

    /**
     * 校验分段是否守恒,返回结构化结果(不抛异常),供前端时间轴实时提示。
     */
    public Dto.ConservationView inspect(DowntimeEvent event, List<DowntimeSegment> raw) {
        List<String> problems = new ArrayList<>();
        Instant eventStart = truncateToSecond(event.startTime);
        Instant eventEnd = truncateToSecond(event.endTime);

        if (eventEnd == null) {
            problems.add("事件尚未恢复(跨班停机仍在继续),无法做完整分段;待设备恢复后补齐时间轴");
        }

        List<DowntimeSegment> segments = new ArrayList<>(raw);
        segments.sort(Comparator.comparing(s -> s.startTime));

        long covered = 0;
        for (int i = 0; i < segments.size(); i++) {
            DowntimeSegment s = segments.get(i);
            if (!s.endTime.isAfter(s.startTime)) {
                problems.add("第 " + (i + 1) + " 段结束时间必须晚于开始时间");
                continue;
            }
            if (s.startTime.isBefore(eventStart)) {
                problems.add("第 " + (i + 1) + " 段早于停机开始时间");
            }
            if (eventEnd != null && s.endTime.isAfter(eventEnd)) {
                problems.add("第 " + (i + 1) + " 段晚于停机恢复时间");
            }
            covered += Duration.between(s.startTime, s.endTime).getSeconds();

            if (i == 0) {
                if (!s.startTime.equals(eventStart)) {
                    problems.add("开头留白:第 1 段起点 " + s.startTime
                            + " 必须等于停机开始 " + eventStart);
                }
            } else {
                DowntimeSegment prev = segments.get(i - 1);
                int cmp = prev.endTime.compareTo(s.startTime);
                if (cmp < 0) {
                    problems.add("第 " + i + " 段与第 " + (i + 1) + " 段之间留白 "
                            + Duration.between(prev.endTime, s.startTime).getSeconds() + " 秒未划分原因");
                } else if (cmp > 0) {
                    problems.add("第 " + i + " 段与第 " + (i + 1) + " 段重叠 "
                            + Duration.between(s.startTime, prev.endTime).getSeconds() + " 秒");
                }
            }

            if (i == segments.size() - 1 && eventEnd != null && !s.endTime.equals(eventEnd)) {
                if (s.endTime.isBefore(eventEnd)) {
                    problems.add("结尾留白:最后一段终点 " + s.endTime
                            + " 必须等于恢复时间 " + eventEnd);
                }
            }
        }

        long eventDuration = eventEnd == null
                ? Duration.between(eventStart, Instant.now()).getSeconds()
                : Duration.between(eventStart, eventEnd).getSeconds();
        long gap = eventEnd == null ? -1 : Math.max(0, eventDuration - covered);
        if (eventEnd != null && segments.size() >= 1 && covered != eventDuration) {
            long diff = covered - eventDuration;
            problems.add("分段合计 " + covered + " 秒 ≠ 整次停机 " + eventDuration
                    + " 秒(差值 " + (diff > 0 ? "+" : "") + diff + " 秒)");
        }

        return new Dto.ConservationView(problems.isEmpty(), eventDuration, covered, gap, problems);
    }

    /**
     * 严格校验,不通过直接抛业务异常(用于保存/复核前)。
     */
    public void requireConserved(DowntimeEvent event, List<DowntimeSegment> segments) {
        if (event.endTime == null) {
            throw BusinessException.of("设备尚未恢复,无法完成分段与复核;请先登记恢复时间(跨班停机沿用同一事件)");
        }
        if (segments.isEmpty()) {
            throw BusinessException.of("时间轴为空:至少需要划分一个原因分段");
        }
        Dto.ConservationView view = inspect(event, segments);
        if (!view.valid()) {
            throw new BusinessException("CONSERVATION_VIOLATION",
                    "分段时长守恒校验未通过:\n- " + String.join("\n- ", view.problems()));
        }
    }
}

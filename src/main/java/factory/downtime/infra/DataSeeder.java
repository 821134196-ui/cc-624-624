package factory.downtime.infra;

import factory.downtime.api.dto.Dto;
import factory.downtime.model.CalcTrigger;
import factory.downtime.model.CycleTimeStandard;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.Equipment;
import factory.downtime.model.EventStatus;
import factory.downtime.service.LossCalculationService;
import factory.downtime.service.ProductionSimulator;
import factory.downtime.service.ReviewService;
import factory.downtime.service.SegmentService;
import factory.downtime.service.StandardService;
import factory.downtime.service.EventService;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 空库时播种演示数据,覆盖验收关注的全部场景:
 * <ul>
 *   <li>班内已复核事件:故障/待料/换线三段守恒,已进正式统计;</li>
 *   <li>跨班次边界已复核事件:同一事件横跨 早班→夜班,报表中仍为一次;</li>
 *   <li>跨班未恢复的开放事件:沿用同一单号,不可重复登记;</li>
 *   <li>已分段未复核事件:不得混入正式统计;</li>
 *   <li>节拍标准 v1→v2,事件 1 已用 v2 重算,首算(v1)批次保留可查;</li>
 *   <li>本地模拟产量回传,停机时段产出为 0。</li>
 * </ul>
 */
@ApplicationScoped
public class DataSeeder {

    private static final Logger LOG = Logger.getLogger(DataSeeder.class);

    @Inject
    EventService eventService;

    @Inject
    SegmentService segmentService;

    @Inject
    ReviewService reviewService;

    @Inject
    StandardService standardService;

    @Inject
    LossCalculationService calcService;

    @Inject
    ProductionSimulator simulator;

    @Inject
    @ConfigProperty(name = "app.demo-seed", defaultValue = "true")
    boolean demoSeed;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (!demoSeed || Equipment.count() > 0) {
            return;
        }
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);

        LOG.infof("播种演示数据(时区 %s,今天 %s)...", zone, today);

        // ---- 设备 ----
        Equipment e1 = new Equipment("EQ-01", "注塑机A", "一车间");
        Equipment e2 = new Equipment("EQ-02", "冲压线B", "一车间");
        Equipment e3 = new Equipment("EQ-03", "包装机C", "二车间");
        e1.persist();
        e2.persist();
        e3.persist();
        Equipment.flush();

        // ---- 节拍标准版本 ----
        Instant longAgo = LocalDateTime.of(2026, 1, 1, 0, 0).atZone(zone).toInstant();
        Instant v2Effective = today.atStartOfDay().atZone(zone).toInstant();
        publishStd(e1.id, 60, longAgo, "工艺初始标准", "工程师赵");
        publishStd(e1.id, 50, v2Effective, "模具改善后提速(60s→50s/件),今日零点生效", "工程师赵");
        publishStd(e2.id, 30, longAgo, "初始标准", "工程师赵");
        publishStd(e3.id, 20, longAgo, "初始标准", "工程师赵");

        // ============ 事件 1:班内停机,故障/待料/换线,已复核 ============
        Instant e1Start = at(zone, yesterday, 9, 0);
        Instant e1End = at(zone, yesterday, 10, 40); // 100 分钟
        DowntimeEvent ev1 = eventService.register(new Dto.RegisterEventRequest(
                e1.id, null, e1Start, e1End, "操作员李"));
        segmentService.saveSegments(ev1.id, new Dto.SaveSegmentsRequest("班长王", List.of(
                new Dto.SegmentInput("故障", at(zone, yesterday, 9, 0), at(zone, yesterday, 9, 40),
                        "加热圈故障,等维修"),
                new Dto.SegmentInput("待料", at(zone, yesterday, 9, 40), at(zone, yesterday, 10, 20),
                        "原料未到齐"),
                new Dto.SegmentInput("换线", at(zone, yesterday, 10, 20), at(zone, yesterday, 10, 40),
                        "切换 P-22 产品并首件确认")
        )));
        reviewService.review(ev1.id, "班长王", "分段与维修/物料记录一致");
        // 用今日生效的 v2 标准重算,首算(v1)批次保留为历史
        calcService.calculate(ev1.id, CalcTrigger.RECALC, "CURRENT", "计划员周");

        // ============ 事件 2:跨 早班→夜班,已复核,仍为同一事件 ============
        Instant e2Start = at(zone, yesterday, 19, 0);
        Instant e2End = at(zone, yesterday, 21, 30); // 150 分钟,跨过 20:00 班次边界
        DowntimeEvent ev2 = eventService.register(new Dto.RegisterEventRequest(
                e1.id, null, e2Start, e2End, "操作员李"));
        segmentService.saveSegments(ev2.id, new Dto.SaveSegmentsRequest("班长王", List.of(
                // 故障段本身跨越班次交接点,归属按段起点(早班)
                new Dto.SegmentInput("故障", at(zone, yesterday, 19, 0), at(zone, yesterday, 20, 30),
                        "液压泵异响,跨班抢修"),
                new Dto.SegmentInput("待料", at(zone, yesterday, 20, 30), at(zone, yesterday, 21, 30),
                        "夜班待料")
        )));
        reviewService.review(ev2.id, "夜班班长陈", "跨班连续,双班共同确认");

        // ============ 事件 3:跨班未恢复的开放事件(沿用同一单号) ============
        Instant openStart = at(zone, today, 1, 0); // 今日凌晨夜班开始
        eventService.register(new Dto.RegisterEventRequest(
                e2.id, null, openStart, null, "夜班操作员孙"));

        // ============ 事件 4:已分段但未复核 —— 不得进入正式统计 ============
        Instant e4Start = at(zone, yesterday, 14, 0);
        Instant e4End = at(zone, yesterday, 15, 0);
        DowntimeEvent ev4 = eventService.register(new Dto.RegisterEventRequest(
                e3.id, null, e4Start, e4End, "操作员吴"));
        segmentService.saveSegments(ev4.id, new Dto.SaveSegmentsRequest("班长王", List.of(
                new Dto.SegmentInput("故障", e4Start, at(zone, yesterday, 14, 40), "待复核:故障描述待补"),
                new Dto.SegmentInput("换线", at(zone, yesterday, 14, 40), e4End, "换型")
        )));
        // 故意停在 SEGMENTED,不复核

        // ============ 模拟产量回传(昨日全天) ============
        Instant dayFrom = yesterday.atStartOfDay(zone).toInstant();
        Instant dayTo = today.atStartOfDay(zone).toInstant();
        int n1 = simulator.backfill(e1.id, dayFrom, dayTo);
        int n3 = simulator.backfill(e3.id, dayFrom, dayTo);
        // 今天也回填到当前时刻,便于看到开放停机期间产出为 0
        simulator.backfill(e2.id, dayTo, Instant.now());

        LOG.infof("演示数据就绪:设备 3 台、停机事件 4 起(已复核 2、待复核 1、跨班开放 1),模拟产量桶 %d+%d",
                n1, n3);
    }

    private void publishStd(Long equipId, double seconds, Instant from, String reason, String by) {
        standardService.publish(equipId, new Dto.StandardRequest(seconds, from, by, reason));
    }

    private static Instant at(ZoneId zone, LocalDate d, int h, int m) {
        return LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant();
    }
}

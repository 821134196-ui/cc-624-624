package com.factory.downtime;

import com.factory.downtime.model.*;
import com.factory.downtime.service.DowntimeService;
import com.factory.downtime.service.SegmentValidator;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/** 启动种子数据：设备、节拍标准版本、示例停机事件（含跨班事件） */
@ApplicationScoped
public class SeedData {

    @Inject
    DowntimeService downtimeService;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (Equipment.count() > 0) {
            return;
        }
        Equipment press = new Equipment("PRS-01", "1号冲压机");
        press.persist();
        Equipment cnc = new Equipment("CNC-02", "2号加工中心");
        cnc.persist();
        Equipment weld = new Equipment("WLD-03", "3号焊接线");
        weld.persist();

        // 节拍标准：v1 早期生效，v2 近期调整（用于演示重算留痕）
        seedStandard(press, "v1", 12.0, "2026-09-01T00:00:00Z", "初始节拍");
        seedStandard(press, "v2", 10.0, "2026-10-01T00:00:00Z", "工艺优化后节拍");
        seedStandard(cnc, "v1", 25.0, "2026-09-01T00:00:00Z", "初始节拍");
        seedStandard(weld, "v1", 18.0, "2026-09-01T00:00:00Z", "初始节拍");

        // 示例 1：本班内停机，已分段待复核
        Instant base = Instant.now().minusSeconds(6 * 3600);
        DowntimeEvent e1 = downtimeService.createEvent(press.id,
                base, base.plusSeconds(75 * 60), "张伟", "冲压机异响停机");
        downtimeService.saveSegments(e1.id, List.of(
                new SegmentValidator.SegmentInput(base, base.plusSeconds(40 * 60), ReasonCode.FAULT, "更换模具定位销"),
                new SegmentValidator.SegmentInput(base.plusSeconds(40 * 60), base.plusSeconds(55 * 60), ReasonCode.WAIT_MATERIAL, "等钢卷上料"),
                new SegmentValidator.SegmentInput(base.plusSeconds(55 * 60), base.plusSeconds(75 * 60), ReasonCode.CHANGEOVER, "换型试模")));

        // 示例 2：跨班停机（夜班开始、次日早班仍未恢复后结束），已复核进入统计
        Instant s2 = Instant.now().minusSeconds(20 * 3600);
        DowntimeEvent e2 = downtimeService.createEvent(cnc.id,
                s2, s2.plusSeconds(5 * 3600), "李强", "主轴温度报警");
        downtimeService.saveSegments(e2.id, List.of(
                new SegmentValidator.SegmentInput(s2, s2.plusSeconds(3 * 3600), ReasonCode.FAULT, "主轴冷却泵维修"),
                new SegmentValidator.SegmentInput(s2.plusSeconds(3 * 3600), s2.plusSeconds(5 * 3600), ReasonCode.WAIT_MATERIAL, "等备件到货")));
        downtimeService.review(e2.id, "班长-王芳");
    }

    private static void seedStandard(Equipment equipment, String version, double takt,
                                     String effectiveFrom, String note) {
        CycleTimeStandard s = new CycleTimeStandard();
        s.equipment = equipment;
        s.version = version;
        s.taktSeconds = takt;
        s.effectiveFrom = Instant.parse(effectiveFrom);
        s.note = note;
        s.persist();
    }
}

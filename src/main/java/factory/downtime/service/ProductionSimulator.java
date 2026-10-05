package factory.downtime.service;

import factory.downtime.api.BusinessException;
import factory.downtime.model.CycleTimeStandard;
import factory.downtime.model.Equipment;
import factory.downtime.model.ProductionCountback;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地模拟产量回传接口。
 *
 * <p>真实场景由设备数采/MES 推送每分钟产量;这里以<b>确定性</b>算法生成:
 * 正常生产时按设备当前节拍略有波动地产出,停机时段为 0。
 * 所有记录 source=SIMULATED,报表不得把它当作真实数采数据。</p>
 */
@ApplicationScoped
public class ProductionSimulator {

    /**
     * 为设备回填 [from, to) 内逐分钟模拟产量。
     */
    @Transactional
    public int backfill(Long equipmentId, Instant from, Instant to) {
        Equipment equipment = (Equipment) Equipment.findByIdOptional(equipmentId)
                .orElseThrow(() -> BusinessException.of("设备不存在: #" + equipmentId));
        CycleTimeStandard std = CycleTimeStandard.findActive(equipmentId);
        if (std == null) {
            throw new BusinessException("NO_STANDARD", "请先为设备 " + equipment.code + " 建立节拍标准");
        }

        // 停机区间(模拟停机时段产出为 0)
        List<factory.downtime.model.DowntimeEvent> events =
                factory.downtime.model.DowntimeEvent.list(
                        "equipmentId = ?1 and startTime < ?2 and (endTime is null or endTime > ?3)",
                        equipmentId, to, from);

        Instant cursor = truncatedMinute(from);
        Instant end = truncatedMinute(to);
        int created = 0;
        while (cursor.isBefore(end)) {
            boolean exists = ProductionCountback
                    .count("equipmentId = ?1 and bucketStart = ?2", equipmentId, cursor) > 0;
            if (!exists) {
                boolean down = false;
                for (var ev : events) {
                    Instant evEnd = ev.endTime != null ? ev.endTime : Instant.now();
                    if (cursor.isBefore(evEnd) && cursor.plusSeconds(60).isAfter(ev.startTime)) {
                        down = true;
                        break;
                    }
                }
                ProductionCountback c = new ProductionCountback();
                c.equipmentId = equipmentId;
                c.bucketStart = cursor;
                c.pieces = down ? 0 : simulatedPieces(cursor, std.cycleSeconds);
                c.source = "SIMULATED";
                c.persist();
                created++;
            }
            cursor = cursor.plusSeconds(60);
        }
        return created;
    }

    public List<ProductionCountback> list(Long equipmentId, Instant from, Instant to) {
        return ProductionCountback
                .list("equipmentId = ?1 and bucketStart >= ?2 and bucketStart < ?3 order by bucketStart",
                        equipmentId, from, to);
    }

    /**
     * 确定性"波动":用分钟时间戳做伪随机,避免随机数导致重算结果不一致。
     * 每分钟产量 ≈ 60/cycle,叠加 ±10% 固定波动。
     */
    private int simulatedPieces(Instant bucket, double cycleSeconds) {
        long minute = bucket.getEpochSecond() / 60;
        double factor = 0.9 + ((minute * 37L) % 11) / 50.0; // 0.90 ~ 1.10
        double pieces = 60.0 / cycleSeconds * factor;
        return (int) Math.max(0, Math.round(pieces));
    }

    static Instant truncatedMinute(Instant t) {
        long sec = t.getEpochSecond();
        return Instant.ofEpochSecond(sec - sec % 60);
    }

    public static LocalDateTime local(Instant t, ZoneId zone) {
        return LocalDateTime.ofInstant(t, zone);
    }

    public static List<String> bucketLabels(List<ProductionCountback> rows) {
        List<String> out = new ArrayList<>();
        for (ProductionCountback r : rows) {
            out.add(r.bucketStart.toString());
        }
        return out;
    }
}

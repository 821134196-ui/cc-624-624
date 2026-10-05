package factory.downtime.service;

import factory.downtime.api.BusinessException;
import factory.downtime.api.dto.Dto;
import factory.downtime.model.CycleTimeStandard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 节拍标准版本管理。
 *
 * <p>每次"调整标准"都产生新版本,旧版本置为 SUPERSEDED 但永不删除。
 * 历史核算引用的是具体标准行(standardId + version 快照),
 * 因此标准调整后:(1) 历史结果仍按旧标准展示;(2) 可用新标准重算,
 * 重算结果另存为新批次,与原结果并列可查。</p>
 */
@ApplicationScoped
public class StandardService {

    @Transactional
    public CycleTimeStandard publish(Long equipmentId, Dto.StandardRequest req) {
        if (req.cycleSeconds() == null || req.cycleSeconds() <= 0) {
            throw BusinessException.of("标准节拍必须为正数(秒/件)");
        }
        Instant effective = ConservationService.truncateToSecond(
                req.effectiveFrom() != null ? req.effectiveFrom() : Instant.now());

        List<CycleTimeStandard> all = list(equipmentId);
        int maxVersion = all.stream().mapToInt(s -> s.version).max().orElse(0);

        CycleTimeStandard current = CycleTimeStandard.findActive(equipmentId);
        int newVersion;
        if (current == null) {
            newVersion = 1;
        } else {
            newVersion = current.version + 1;
            current.status = "SUPERSEDED";
            current.supersededAt = Instant.now();
            current.supersededByVersion = newVersion;
        }

        CycleTimeStandard std = new CycleTimeStandard();
        std.equipmentId = equipmentId;
        std.version = newVersion;
        std.cycleSeconds = req.cycleSeconds();
        std.effectiveFrom = effective;
        std.status = "ACTIVE";
        std.changedBy = req.changedBy();
        std.changeReason = req.changeReason();
        std.persist();
        return std;
    }

    public List<CycleTimeStandard> list(Long equipmentId) {
        return CycleTimeStandard
                .list("equipmentId = ?1 order by version desc", equipmentId);
    }

    public CycleTimeStandard get(Long id) {
        return (CycleTimeStandard) CycleTimeStandard.findByIdOptional(id)
                .orElseThrow(() -> BusinessException.of("节拍标准不存在: #" + id));
    }

    /**
     * 为某分段选择节拍标准。
     *
     * @param mode HISTORICAL -> 分段开始时刻当班生效的标准;
     *             CURRENT    -> 设备当前 ACTIVE 标准
     */
    public CycleTimeStandard choose(Long equipmentId, Instant at, String mode) {
        CycleTimeStandard std;
        if ("CURRENT".equalsIgnoreCase(mode)) {
            std = CycleTimeStandard.findActive(equipmentId);
        } else {
            std = CycleTimeStandard.findEffectiveAt(equipmentId, at);
        }
        if (std == null) {
            throw new BusinessException("NO_STANDARD",
                    "设备 #" + equipmentId + " 在 " + at + " 没有可用的节拍标准,请先建立标准版本");
        }
        return std;
    }
}

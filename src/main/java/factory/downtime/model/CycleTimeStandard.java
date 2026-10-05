package factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 标准节拍(cycle time)标准。同一设备按版本号递增,
 * 新版本生效后旧版本置为 SUPERSEDED —— 历史计算永远引用当时版本,
 * 标准调整后重算同时保留旧计算结果及其依据。
 */
@Entity
@Table(name = "cycle_time_standard", uniqueConstraints =
        @UniqueConstraint(columnNames = {"equipment_id", "version"}))
public class CycleTimeStandard extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "equipment_id", nullable = false)
    public Long equipmentId;

    /** 设备内递增的版本号,从 1 开始。 */
    @Column(nullable = false)
    public int version;

    /** 标准节拍:每件耗时(秒),用于估算产能损失。 */
    @Column(name = "cycle_seconds", nullable = false)
    public double cycleSeconds;

    @Column(name = "effective_from", nullable = false)
    public Instant effectiveFrom;

    /** ACTIVE / SUPERSEDED */
    @Column(nullable = false, length = 16)
    public String status = "ACTIVE";

    @Column(name = "superseded_at")
    public Instant supersededAt;

    @Column(name = "superseded_by_version")
    public Integer supersededByVersion;

    @Column(name = "changed_by", length = 32)
    public String changedBy;

    @Column(name = "change_reason", length = 300)
    public String changeReason;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public static CycleTimeStandard findActive(Long equipmentId) {
        return (CycleTimeStandard) find("equipmentId = ?1 and status = 'ACTIVE' order by version desc",
                equipmentId).firstResultOptional().orElse(null);
    }

    /**
     * 取某一时刻适用的标准版本:已生效的最新版本(含后来被替代的版本,
     * 因为历史时点上它仍是当班采用的标准)。
     */
    public static CycleTimeStandard findEffectiveAt(Long equipmentId, Instant at) {
        return (CycleTimeStandard) find("equipmentId = ?1 and effectiveFrom <= ?2 order by version desc",
                equipmentId, at).firstResultOptional().orElse(null);
    }
}

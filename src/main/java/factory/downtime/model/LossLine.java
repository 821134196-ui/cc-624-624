package factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一版核算结果内、按时间轴分段的损失明细。
 * 分段时长、原因、当时/当前节拍标准版本在此一并快照。
 */
@Entity
@Table(name = "loss_line")
public class LossLine extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "calculation_id", nullable = false)
    public Long calculationId;

    /** 反向关联(只读),外键以 calculationId 为准。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_id", insertable = false, updatable = false)
    public LossCalculation calculation;

    @Column(name = "line_no", nullable = false)
    public int lineNo;

    @Column(name = "segment_id")
    public Long segmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public DowntimeReason reason;

    @Column(name = "start_time", nullable = false)
    public Instant startTime;

    @Column(name = "end_time", nullable = false)
    public Instant endTime;

    @Column(name = "duration_seconds", nullable = false)
    public long durationSeconds;

    /** 本行采用的节拍标准快照。 */
    @Column(name = "standard_id")
    public Long standardId;

    @Column(name = "standard_version")
    public Integer standardVersion;

    @Column(name = "cycle_seconds_used", nullable = false)
    public double cycleSecondsUsed;

    /** 估算损失件数 = 分段秒数 / 节拍秒数(估算,非实际减产)。 */
    @Column(name = "estimated_loss_units", nullable = false)
    public double estimatedLossUnits;

    /** 计算时该分段归属的班次标签,如 "2026-10-04 夜班"。 */
    @Column(name = "shift_label", length = 32)
    public String shiftLabel;
}

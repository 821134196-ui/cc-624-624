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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次停机事件的一版产能损失核算结果。
 *
 * <p>复核通过时生成首算(REVIEW);节拍标准调整后可重新计算(RECALC),
 * 重新计算只<b>新增</b>批次、绝不覆盖历史批次 —— 每个批次都完整保留
 * 所采用的节拍标准版本及计算依据,因此原结果永远可查、可追溯。</p>
 *
 * <p>所有损失件数均为按标准节拍推算的<b>估算值</b>,不是实际减产数据。</p>
 */
@Entity
@Table(name = "loss_calculation")
public class LossCalculation extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(nullable = false)
    public int batchNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 16)
    public CalcTrigger triggerType;

    /** HISTORICAL = 按当班当时生效标准;CURRENT = 按当前最新标准重算。 */
    @Column(name = "standard_mode", nullable = false, length = 16)
    public String standardMode;

    @Column(name = "calculated_at", nullable = false)
    public Instant calculatedAt = Instant.now();

    @Column(name = "calculated_by", length = 32)
    public String calculatedBy;

    /** 涉及的节拍标准版本清单,如 "v1=60.0s; v2=45.0s"。 */
    @Column(name = "standard_versions_used", length = 500)
    public String standardVersionsUsed;

    @Column(name = "total_duration_seconds", nullable = false)
    public long totalDurationSeconds;

    @Column(name = "total_estimated_loss_units", nullable = false)
    public double totalEstimatedLossUnits;

    /** 估算固定声明,防止报表误读为实际减产。 */
    @Column(name = "estimate_disclaimer", length = 200)
    public String estimateDisclaimer;

    /** 人读计算依据,如 "故障 40min / 60.0s每件 ≈ 40.00件"。 */
    @Column(name = "basis_text", length = 2000)
    public String basisText;

    /** 该事件后续是否已有更新批次。 */
    @Column(name = "superseded", nullable = false)
    public boolean superseded = false;

    @OneToMany(mappedBy = "calculation", fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    public List<LossLine> lines = new ArrayList<>();
}

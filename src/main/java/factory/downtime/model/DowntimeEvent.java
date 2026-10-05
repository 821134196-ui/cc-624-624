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
 * 一次完整的停机事件:从操作员登记停机开始,到设备恢复生产结束。
 *
 * <p>跨班连续:跨班未恢复也沿用本行记录(同一 eventCode),恢复时回填 endTime,
 * 不允许为同一设备重复登记未结束的停机事件 —— 从数据入口上防止把一次停机
 * 拆成两次独立故障重复统计。</p>
 */
@Entity
@Table(name = "downtime_event")
public class DowntimeEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 业务单号,如 DT-20261005-001,跨班沿用。 */
    @Column(nullable = false, unique = true, length = 40)
    public String eventCode;

    @Column(name = "equipment_id", nullable = false)
    public Long equipmentId;

    @Column(name = "start_time", nullable = false)
    public Instant startTime;

    /** 未恢复时为 null(事件开放中)。 */
    @Column(name = "end_time")
    public Instant endTime;

    @Column(name = "registered_by", length = 32)
    public String registeredBy;

    @Column(name = "registered_at", nullable = false)
    public Instant registeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public EventStatus status = EventStatus.REGISTERED;

    @Column(name = "segmented_by", length = 32)
    public String segmentedBy;

    @Column(name = "segmented_at")
    public Instant segmentedAt;

    @Column(name = "reviewed_by", length = 32)
    public String reviewedBy;

    @Column(name = "reviewed_at")
    public Instant reviewedAt;

    @Column(name = "review_comment", length = 500)
    public String reviewComment;

    /** 当前生效的损失计算批次(复核首算或最近一次重算)。 */
    @Column(name = "current_calc_id")
    public Long currentCalcId;

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    @OrderBy("ordinal ASC")
    public List<DowntimeSegment> segments = new ArrayList<>();

    // ---- 查询辅助 ----

    public static DowntimeEvent findOpenByEquipment(Long equipmentId) {
        return (DowntimeEvent) find("equipmentId = ?1 and endTime is null", equipmentId)
                .firstResultOptional().orElse(null);
    }

    public boolean isOpen() {
        return endTime == null;
    }

    public long durationSeconds() {
        if (endTime == null) {
            throw new IllegalStateException("事件 " + eventCode + " 尚未恢复,无完整时长");
        }
        return java.time.Duration.between(startTime, endTime).getSeconds();
    }
}

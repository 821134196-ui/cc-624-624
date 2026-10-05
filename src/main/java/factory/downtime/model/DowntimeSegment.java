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
 * 停机时间轴上的一个分段:同一事件内各段首尾相接,
 * 不允许重叠或留白,合计必须等于整次停机时长(见 SegmentService 校验)。
 */
@Entity
@Table(name = "downtime_segment")
public class DowntimeSegment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    /** 反向关联(只读),供事件侧 mappedBy 使用;外键以 eventId 为准。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    public DowntimeEvent event;

    /** 时间轴上的序号,从 1 开始。 */
    @Column(nullable = false)
    public int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public DowntimeReason reason;

    @Column(name = "start_time", nullable = false)
    public Instant startTime;

    @Column(name = "end_time", nullable = false)
    public Instant endTime;

    @Column(name = "confirmed_by", length = 32)
    public String confirmedBy;

    @Column(name = "confirmed_at")
    public Instant confirmedAt;

    @Column(length = 300)
    public String note;

    public long durationSeconds() {
        return java.time.Duration.between(startTime, endTime).getSeconds();
    }
}

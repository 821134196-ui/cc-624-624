package com.factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * 停机时间轴上的一个原因分段。同一事件的所有分段必须：
 * 不重叠、不留空白、首尾对齐事件起止，合计等于整次停机时长。
 */
@Entity
@Table(name = "downtime_segment")
public class DowntimeSegment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    public DowntimeEvent event;

    /** 时间轴顺序号，从 0 开始 */
    @Column(nullable = false)
    public int seq;

    @Column(nullable = false)
    public Instant startTime;

    @Column(nullable = false)
    public Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ReasonCode reason;

    @Column(length = 512)
    public String note;
}

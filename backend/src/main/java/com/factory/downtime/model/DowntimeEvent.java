package com.factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * 停机事件。设备跨班未恢复时沿用同一事件（endTime 可晚于起始班次结束，
 * 甚至暂时为 null），统计时只计入起始班次一次，绝不拆成两个事件。
 */
@Entity
@Table(name = "downtime_event")
public class DowntimeEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id")
    public Equipment equipment;

    @Column(nullable = false)
    public Instant startTime;

    /** 恢复时间；仍在停机时为空 */
    public Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public EventStatus status = EventStatus.OPEN;

    /** 登记人（操作员） */
    @Column(length = 64)
    public String operatorName;

    /** 复核人（班长） */
    @Column(length = 64)
    public String reviewerName;

    public Instant reviewedAt;

    @Column(length = 512)
    public String note;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}

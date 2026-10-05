package com.factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * 标准节拍（版本化）。每次调整生成新行，旧版本保留，
 * 以便历史损失核算仍能追溯到当时采用的节拍依据。
 */
@Entity
@Table(name = "cycle_time_standard")
public class CycleTimeStandard extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id")
    public Equipment equipment;

    /** 版本号，如 "v1"、"v2"，同一设备下唯一 */
    @Column(nullable = false, length = 32)
    public String version;

    /** 标准节拍：每件多少秒 */
    @Column(nullable = false)
    public double taktSeconds;

    /** 该版本生效时间，按事件起始时刻匹配适用版本 */
    @Column(nullable = false)
    public Instant effectiveFrom;

    @Column(length = 512)
    public String note;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}

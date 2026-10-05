package com.factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次损失核算的结果快照。每次核算（含标准变更后的重算）都新增记录，
 * 旧记录保留（active=false），可查询原结果及其采用的节拍标准与版本。
 */
@Entity
@Table(name = "loss_calculation")
public class LossCalculation extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    public DowntimeEvent event;

    /** 核算时采用的节拍标准（外键，可追溯） */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "standard_id")
    public CycleTimeStandard standard;

    /** 冗余留存版本号与节拍值，即使标准记录被清理结果仍有依据 */
    @Column(nullable = false, length = 32)
    public String standardVersion;

    @Column(nullable = false)
    public double taktSeconds;

    @Column(nullable = false)
    public Instant calculatedAt = Instant.now();

    /** 估算损失（件）。明确为估算值，非实际减产数据 */
    @Column(nullable = false)
    public double totalEstimatedLoss;

    /** 是否当前生效的核算结果（重算后旧结果置为 false） */
    @Column(nullable = false)
    public boolean active = true;

    @OneToMany(mappedBy = "calculation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("seq ASC")
    public List<LossCalculationSegment> segments = new ArrayList<>();
}

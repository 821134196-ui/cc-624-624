package com.factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/** 损失核算中每个原因分段的明细 */
@Entity
@Table(name = "loss_calculation_segment")
public class LossCalculationSegment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_id")
    public LossCalculation calculation;

    @Column(nullable = false)
    public int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ReasonCode reason;

    @Column(nullable = false)
    public long durationSeconds;

    /** 该分段估算损失（件）= durationSeconds / taktSeconds */
    @Column(nullable = false)
    public double estimatedLoss;
}

package factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 模拟产量回传记录(本地模拟接口写入)。
 * 真实系统中由 MES/设备数采回传;本项目用确定性模拟数据代替,
 * 仅供与"估算损失"对照参考,报表中明确标注数据来源为模拟。
 */
@Entity
@Table(name = "production_countback")
public class ProductionCountback extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "equipment_id", nullable = false)
    public Long equipmentId;

    @Column(name = "bucket_start", nullable = false)
    public Instant bucketStart;

    /** 该时间桶(1 分钟)内实际产出件数(模拟)。 */
    @Column(name = "pieces", nullable = false)
    public int pieces;

    /** SIMULATED 标记,防止与真实回传混淆。 */
    @Column(nullable = false, length = 16)
    public String source = "SIMULATED";
}

package factory.downtime.model;

/**
 * 损失计算批次的产生方式。
 */
public enum CalcTrigger {
    REVIEW("复核通过首次计算"),
    RECALC("标准变更后重新计算");

    public final String label;

    CalcTrigger(String label) {
        this.label = label;
    }
}

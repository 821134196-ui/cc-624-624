package factory.downtime.model;

/**
 * 一次停机内部的分段原因。
 */
public enum DowntimeReason {
    FAILURE("故障"),
    WAIT_MATERIAL("待料"),
    CHANGEOVER("换线");

    public final String label;

    DowntimeReason(String label) {
        this.label = label;
    }
}

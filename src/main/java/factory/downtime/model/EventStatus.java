package factory.downtime.model;

/**
 * 停机事件工作流状态:
 * REGISTERED  操作员已登记,等待班长分段
 * SEGMENTED   班长已在时间轴上分段,等待复核
 * REVIEWED    班长复核通过,进入正式统计
 */
public enum EventStatus {
    REGISTERED("待分段"),
    SEGMENTED("待复核"),
    REVIEWED("已复核");

    public final String label;

    EventStatus(String label) {
        this.label = label;
    }
}

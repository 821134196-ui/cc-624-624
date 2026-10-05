package com.factory.downtime.model;

/**
 * 停机事件状态机：
 * OPEN          操作员已登记（可能仍在停机中，endTime 可空）
 * PENDING_REVIEW 班长已完成时间轴分段，等待复核
 * REVIEWED      班长复核通过，进入正式统计
 */
public enum EventStatus {
    OPEN,
    PENDING_REVIEW,
    REVIEWED
}

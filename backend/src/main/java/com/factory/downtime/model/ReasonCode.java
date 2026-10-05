package com.factory.downtime.model;

/** 停机原因分类 */
public enum ReasonCode {
    FAULT("故障"),
    WAIT_MATERIAL("待料"),
    CHANGEOVER("换线"),
    OTHER("其他");

    private final String label;

    ReasonCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

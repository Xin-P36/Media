package org.xinp.constant;

public enum ScanStatus {
    IDLE("空闲"),
    RUNNING("正在扫描"),
    COMPLETED("扫描完成"),
    CANCELED("已取消"),
    FAILED("扫描失败");

    private final String description;

    ScanStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
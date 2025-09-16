package org.xinp.constant;

/**
 * 日志状态枚举类
 */
public enum OperationLogStatus {
    /**
     * 待处理 - 任务已创建，等待后台调度器拾取。
     * 这是任务的初始状态。
     */
    PENDING("待处理"),

    /**
     * 处理中 - 任务已被后台工作线程认领并正在执行。
     */
    PROCESSING("处理中"),

    /**
     * 已完成 - 任务成功执行完毕。
     */
    COMPLETED("已完成"),

    /**
     * 已失败 - 任务在执行过程中发生错误，已被中断。
     * 错误详情应记录在 OperationLogs 的 errorMessage 字段。
     */
    FAILED("已失败"),

    /**
     * 已取消 - 任务在被执行前，被用户或系统取消。
     */
    CANCELLED("已取消");
    
    private final String description;

    OperationLogStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
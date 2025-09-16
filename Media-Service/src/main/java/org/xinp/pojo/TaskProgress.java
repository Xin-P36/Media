package org.xinp.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.xinp.constant.ScanStatus;

/**
 * 日志任务进度信息
 */
@Data
@NoArgsConstructor
public class TaskProgress {
    private String taskType; // 任务类型 (MOVE, DELETE, TRANSCODE)
    private ScanStatus status = ScanStatus.IDLE; // 任务状态
    private long totalTasks = 0; // 任务总数
    private long processedTasks = 0; // 已处理的任务数
    private int percentage = 0; // 进度百分比
    private String currentStep = ""; // 当前正在处理的步骤/文件
    private String message = "任务服务已就绪";

    public TaskProgress(String taskType) {
        this.taskType = taskType;
    }
}
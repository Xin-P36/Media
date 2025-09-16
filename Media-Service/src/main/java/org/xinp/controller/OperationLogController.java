package org.xinp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.xinp.constant.Code;
import org.xinp.pojo.Result;
import org.xinp.pojo.TaskProgress;
import org.xinp.service.impl.OperationLogTaskManager;

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class OperationLogController {
    private final OperationLogTaskManager taskManager;

    /**
     * 启动任务 OperationLog标记的删除/移动任务
     * @param type 任务类型
     *             1. MOVE 移动任务
     *             2. DELETE 删除任务
     *             3. TRANSCODE 转码任务
     *             4. TRANSCODE 生成缩略图任务
     *             5. THUMBNAIL 生成封面缩略图
     * @return ok
     */
    @PostMapping("/start")
    public Result<String> startTask(@RequestParam String type) {
        try {
            taskManager.startTask(type.toUpperCase());
            return Result.okResult("任务已启动。");
        } catch (IllegalStateException e) {
            return Result.errorResult(Code.TASK_IN_EXECUTION.getCode(), e.getMessage());
        }
    }

    /**
     * 获取任务进度
     * @return 进度
     */
    @GetMapping("/progress")
    public Result<TaskProgress> getProgress() {
        return Result.okResult(taskManager.getProgress());
    }

    /**
     * 取消任务
     * @return OK
     */
    @PostMapping("/cancel")
    public Result<String> cancelTask() {
        taskManager.cancelTask();
        return Result.okResult("取消请求已发送。");
    }
}
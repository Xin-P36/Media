package org.xinp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xinp.constant.OperationLogStatus;
import org.xinp.constant.ScanStatus;
import org.xinp.entity.OperationLogs;
import org.xinp.mapper.OperationLogsMapper;
import org.xinp.pojo.TaskProgress;
import org.xinp.service.OperationLogProcessor;
import org.xinp.util.VideoNormalizationProcessor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
public class OperationLogTaskManager {

    private final OperationLogsMapper logsMapper;
    private final Map<String, OperationLogProcessor> processors; // 自动注入所有基于日志的处理器
    private final ThumbnailTaskProcessor thumbnailTaskProcessor; // 单独注入缩略图处理器
    private final VideoNormalizationProcessor normalizationProcessor; // 注入新处理器

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<TaskProgress> progressRef = new AtomicReference<>(new TaskProgress());
    private volatile Future<?> taskFuture;

    /**
     * 构造函数，由Spring负责注入所有需要的Bean。
     * @param logsMapper 操作日志的Mapper
     * @param processors 一个Map，键是Bean的名称(如"MOVE_Processor")，值是实现了OperationLogProcessor接口的Bean实例
     * @param thumbnailTaskProcessor 缩略图生成的专用处理器
     */
    public OperationLogTaskManager(
            OperationLogsMapper logsMapper,
            Map<String, OperationLogProcessor> processors,
            ThumbnailTaskProcessor thumbnailTaskProcessor,
            VideoNormalizationProcessor normalizationProcessor
    ) {
        this.logsMapper = logsMapper;
        this.processors = processors;
        this.thumbnailTaskProcessor = thumbnailTaskProcessor;
        this.normalizationProcessor = normalizationProcessor;
    }

    /**
     * 启动一个后台任务。
     * @param taskType 任务类型 (MOVE, DELETE, TRANSCODE, THUMBNAIL)
     * @throws IllegalStateException 如果已有任务在运行
     */
    public void startTask(String taskType) {
        if (progressRef.get().getStatus() == ScanStatus.RUNNING) {
            throw new IllegalStateException("已有任务正在执行中，请稍后再试！");
        }

        // 初始化任务进度对象
        progressRef.set(new TaskProgress(taskType));
        progressRef.get().setStatus(ScanStatus.RUNNING);

        // 根据任务类型，分发到不同的执行逻辑
        if ("THUMBNAIL".equals(taskType)) {
            startThumbnailGenerationTask();
        } else if ("NORMALIZE_VIDEO".equals(taskType)) { // 新增分支
            startVideoNormalizationTask(); //处理视频格式转换任务
        } else {
            startLogBasedTask(taskType);
        }
    }

    /**
     * 将非MP4格式的视频转换为标准的、Web友好的MP4格式,任务处理器
     */
    // 新增方法：处理视频格式转换任务
    private void startVideoNormalizationTask() {
        progressRef.get().setMessage("开始视频格式规范化任务...");
        taskFuture = executor.submit(() -> {
            try {
                Consumer<TaskProgress> progressCallback = progress -> {
                    TaskProgress currentProgress = progressRef.get();
                    currentProgress.setTotalTasks(progress.getTotalTasks());
                    currentProgress.setProcessedTasks(progress.getProcessedTasks());
                    currentProgress.setPercentage(progress.getPercentage());
                    currentProgress.setCurrentStep(progress.getCurrentStep());
                };

                normalizationProcessor.execute(progressCallback);

                TaskProgress finalProgress = progressRef.get();
                finalProgress.setStatus(ScanStatus.COMPLETED);
                finalProgress.setPercentage(100);
                finalProgress.setMessage("视频格式规范化任务已完成。");

            } catch (InterruptedException e) {
                // ... (处理取消)
            } catch (Exception e) {
                // ... (处理失败)
            }
        });
    }

    /**
     * 执行基于OperationLogs表的任务 (MOVE, DELETE, TRANSCODE)。
     * @param taskType 任务类型
     */
    private void startLogBasedTask(String taskType) {
        progressRef.get().setMessage("正在查询任务列表...");

        taskFuture = executor.submit(() -> {
            try {
                // 1. 查询待处理的任务
                LambdaQueryWrapper<OperationLogs> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(OperationLogs::getOperationType, taskType)
                        .eq(OperationLogs::getStatus, OperationLogStatus.PENDING);
                List<OperationLogs> tasks = logsMapper.selectList(wrapper);

                long total = tasks.size();
                long processed = 0;
                progressRef.get().setTotalTasks(total);

                if (total == 0) {
                    progressRef.get().setStatus(ScanStatus.COMPLETED);
                    progressRef.get().setMessage("没有需要执行的 '" + taskType + "' 任务。");
                    return;
                }

                // 2. 获取对应的处理器
                OperationLogProcessor processor = processors.get(taskType + "_Processor");
                if (processor == null) {
                    throw new IllegalArgumentException("未找到类型为 " + taskType + " 的任务处理器。");
                }

                // 3. 逐个执行任务
                for (OperationLogs task : tasks) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("任务被用户取消。");
                    }

                    updateProgress(total, ++processed, "正在处理任务ID: " + task.getOperationId());

                    try {
                        task.setStatus(OperationLogStatus.PROCESSING);
                        logsMapper.updateById(task);

                        processor.process(task);

                        task.setStatus(OperationLogStatus.COMPLETED);
                        logsMapper.updateById(task);
                        log.info("任务 {} (ID:{}) 执行成功。", taskType, task.getOperationId());
                    } catch (Exception e) {
                        log.error("执行任务 {} (ID:{}) 失败。", taskType, task.getOperationId());
                        //log.error("执行任务 {} (ID:{}) 失败。", taskType, task.getOperationId(), e);
                        task.setStatus(OperationLogStatus.FAILED);
                        task.setErrorMessage(e.getMessage());
                        logsMapper.updateById(task);
                    }
                }

                // 任务正常结束
                progressRef.get().setStatus(ScanStatus.COMPLETED);
                progressRef.get().setMessage("所有 '" + taskType + "' 任务执行完毕。");

            } catch (InterruptedException e) {
                log.warn("任务 {} 被取消。", taskType);
                progressRef.get().setStatus(ScanStatus.CANCELED);
                progressRef.get().setMessage("任务已被用户取消。");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("执行任务 {} 时发生意外错误。", taskType);
                //log.error("执行任务 {} 时发生意外错误。", taskType, e);
                progressRef.get().setStatus(ScanStatus.FAILED);
                progressRef.get().setMessage("任务失败: " + e.getMessage());
            }
        });
    }

    /**
     * 执行全局扫描型任务 (THUMBNAIL)。
     */
    private void startThumbnailGenerationTask() {
        progressRef.get().setMessage("开始全局缩略图/封面生成任务...");

        taskFuture = executor.submit(() -> {
            try {
                // 定义一个回调函数，将ThumbnailTaskProcessor的进度更新到本管理器的progressRef中
                Consumer<TaskProgress> progressCallback = progress -> {
                    // 我们只更新核心进度字段，不改变外层的status和taskType
                    TaskProgress currentProgress = progressRef.get();
                    currentProgress.setTotalTasks(progress.getTotalTasks());
                    currentProgress.setProcessedTasks(progress.getProcessedTasks());
                    currentProgress.setPercentage(progress.getPercentage());
                    currentProgress.setCurrentStep(progress.getCurrentStep());
                };

                // 核心执行逻辑
                thumbnailTaskProcessor.execute(progressCallback);

                // 任务正常结束
                TaskProgress finalProgress = progressRef.get();
                finalProgress.setStatus(ScanStatus.COMPLETED);
                finalProgress.setPercentage(100);
                finalProgress.setMessage("缩略图/封面生成任务已完成。");

            } catch (InterruptedException e) {
                log.warn("缩略图生成任务被取消。");
                progressRef.get().setStatus(ScanStatus.CANCELED);
                progressRef.get().setMessage("任务已被用户取消。");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("执行缩略图生成任务时发生意外错误。");
                //log.error("执行缩略图生成任务时发生意外错误。", e);
                progressRef.get().setStatus(ScanStatus.FAILED);
                progressRef.get().setMessage("任务失败: " + e.getMessage());
            }
        });
    }

    /**
     * 取消当前正在执行的任务。
     */
    public void cancelTask() {
        if (taskFuture != null && !taskFuture.isDone()) {
            log.info("收到取消任务的请求...");
            taskFuture.cancel(true); // true 会尝试中断正在执行的线程
        } else {
            log.warn("没有正在运行的任务可以取消。");
        }
    }

    /**
     * 获取当前的扫描进度。
     * @return TaskProgress 包含当前所有进度信息的对象
     */
    public TaskProgress getProgress() {
        return progressRef.get();
    }

    /**
     * 辅助方法：更新进度信息
     */
    private void updateProgress(long total, long processed, String step) {
        TaskProgress currentProgress = progressRef.get();
        currentProgress.setTotalTasks(total);
        currentProgress.setProcessedTasks(processed);
        currentProgress.setCurrentStep(step);
        currentProgress.setPercentage(total > 0 ? (int)(100.0 * processed / total) : 0);
    }
}
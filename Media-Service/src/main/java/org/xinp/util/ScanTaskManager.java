package org.xinp.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xinp.pojo.ScanProgress;
import org.xinp.constant.ScanStatus;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 扫描任务管理器
 */
@Service
@Slf4j
public class ScanTaskManager {

    private final MediaScannerService mediaScannerService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(); // 确保一次只有一个扫描任务

    // 使用AtomicReference来保证对ScanProgress对象的读写是线程安全的
    private final AtomicReference<ScanProgress> progressRef = new AtomicReference<>(new ScanProgress(ScanStatus.IDLE, 0, 0, 0, "", "扫描服务已就绪"));
    
    // 用于持有正在运行的任务，以便可以取消它
    private volatile Future<?> scanTaskFuture;

    public ScanTaskManager(MediaScannerService mediaScannerService) {
        this.mediaScannerService = mediaScannerService;
    }

    /**
     * 启动一个新的扫描任务。
     * @param directoryPath 要扫描的目录
     * @throws IllegalStateException 如果当前已有任务正在运行
     */
    public void startScan(Path directoryPath) {
        // 使用compareAndSet确保只有一个线程可以启动任务
        if (!progressRef.get().getStatus().equals(ScanStatus.IDLE) &&
            !progressRef.get().getStatus().equals(ScanStatus.COMPLETED) &&
            !progressRef.get().getStatus().equals(ScanStatus.FAILED) &&
            !progressRef.get().getStatus().equals(ScanStatus.CANCELED)) {
            throw new IllegalStateException("扫描任务正在运行中，请勿重复启动！");
        }
        
        // 重置进度并设置为RUNNING状态
        progressRef.set(new ScanProgress(ScanStatus.RUNNING, 0, 0, 0, "", "正在初始化扫描..."));

        // 将任务提交到线程池执行
        scanTaskFuture = executor.submit(() -> {
            try {
                // 定义进度回调，用于更新AtomicReference中的progress对象
                MediaScannerService.ProgressCallback callback = (total, processed, currentFile, percentage) -> {
                    ScanProgress currentProgress = new ScanProgress(
                            ScanStatus.RUNNING, total, processed, percentage, currentFile, "正在扫描: " + currentFile
                    );
                    progressRef.set(currentProgress);
                };
                
                // 执行扫描
                mediaScannerService.scanAndIndexDirectory(directoryPath, callback);
                
                // 检查任务是否被中途取消
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                // 正常完成
                progressRef.set(new ScanProgress(ScanStatus.COMPLETED, 
                        progressRef.get().getTotalFiles(), progressRef.get().getTotalFiles(), 100, "", "全部文件扫描完成。"));
                log.info("扫描任务成功完成。");

            } catch (InterruptedException e) {
                // 任务被取消
                progressRef.set(new ScanProgress(ScanStatus.CANCELED, 
                        progressRef.get().getTotalFiles(), progressRef.get().getProcessedFiles(), progressRef.get().getPercentage(), "", "扫描任务已被用户取消。"));
                log.warn("扫描任务被取消。");
                Thread.currentThread().interrupt(); // 保持中断状态
            } catch (Exception e) {
                // 发生其他错误
                progressRef.set(new ScanProgress(ScanStatus.FAILED, 
                        progressRef.get().getTotalFiles(), progressRef.get().getProcessedFiles(), progressRef.get().getPercentage(), "", "扫描失败: " + e.getMessage()));
                log.error("扫描任务执行失败。", e);
            }
        });
    }

    /**
     * 取消正在进行的扫描任务。
     */
    public void cancelScan() {
        if (scanTaskFuture != null && !scanTaskFuture.isDone()) {
            log.info("收到取消扫描任务的请求...");
            // true参数会尝试中断正在执行的线程
            scanTaskFuture.cancel(true);
        } else {
            log.warn("没有正在运行的扫描任务可以取消。");
        }
    }

    /**
     * 获取当前的扫描进度。
     * @return ScanProgress 包含当前所有进度信息的对象
     */
    public ScanProgress getCurrentProgress() {
        return progressRef.get();
    }
}
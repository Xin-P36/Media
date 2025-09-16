package org.xinp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.xinp.constant.ScanStatus;
import org.xinp.entity.MediaFiles;
import org.xinp.entity.UserSettings;
import org.xinp.mapper.MediaFilesMapper;
import org.xinp.mapper.UserSettingsMapper;
import org.xinp.pojo.TaskProgress;
import org.xinp.util.FfmpegService;
import org.xinp.util.FileManagementUtil;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

@Service("THUMBNAIL_Processor") // 注册为Bean
@RequiredArgsConstructor
@Slf4j
public class ThumbnailTaskProcessor { // 注意：这个不是实现OperationLogProcessor, 因为它不是处理日志，而是批量生成

    private final MediaFilesMapper mediaFilesMapper;
    private final UserSettingsMapper userSettingsMapper;
    private final FileManagementUtil fileManagementUtil;
    private final FfmpegService ffmpegService;
    @Qualifier("projectPath")
    private final Path rootPath;

    /**
     * 主执行方法，由任务管理器调用。
     * @param progressCallback 用于向任务管理器报告进度的回调
     * @throws InterruptedException 如果任务被取消
     */
    public void execute(Consumer<TaskProgress> progressCallback) throws InterruptedException {
        // 1. 获取用户设置 (假设只有一个用户或使用默认用户，ID为1)
        // TODO: 在多用户系统中，需要明确指定为哪个用户生成
        //这里应该获取当前用户的ID在JWT中设置了用户ID，但是这个是个后台程序拿不到当前用户的ID
        //当前应用只有一个唯一用户，所以这里直接使用ID为0
        UserSettings settings = userSettingsMapper.selectById(0L);
        if (settings == null) {
            throw new RuntimeException("无法找到用户设置，任务终止。");
        }

        Integer qualityPercent = settings.getWidth(); // 质量百分比 (1-100)
        long sizeThreshold = settings.getThumbnailThreshold(); // 文件大小阈值 (bytes)
        int videoFrameSecond = settings.getHeight(); // 截取视频第几秒

        if (qualityPercent == null || sizeThreshold <= 0 || videoFrameSecond < 0) {
            throw new RuntimeException("用户缩略图设置无效，请检查配置。");
        }

        // 2. 查询所有需要处理的媒体文件 (图片和视频)
        LambdaQueryWrapper<MediaFiles> query = new LambdaQueryWrapper<>();
        query.isNull(MediaFiles::getThumbnail) // 只找还没有缩略图的
             .and(wq -> wq.like(MediaFiles::getMimeType, "image/%")
                          .or()
                          .like(MediaFiles::getMimeType, "video/%"));
        List<MediaFiles> filesToProcess = mediaFilesMapper.selectList(query);
        
        long total = filesToProcess.size();
        long processed = 0;
        
        // 初始化进度
        updateProgress(progressCallback, total, processed, "任务初始化...");

        // 3. 遍历并处理每个文件
        for (MediaFiles file : filesToProcess) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("任务被用户取消。");
            }
            
            updateProgress(progressCallback, total, processed, "正在处理: " + file.getFileName());

            try {
                if (file.getMimeType().startsWith("image/")) {
                    // 只为大于阈值大小的图片生成
                    if (file.getFileSize() > sizeThreshold) {
                        processImage(file, qualityPercent / 100.0f); // 转换为0.0-1.0的小数
                    }
                } else if (file.getMimeType().startsWith("video/")) {
                    processVideo(file, videoFrameSecond);
                }
            } catch (Exception e) {
                log.error("为文件 {} (ID:{}) 生成缩略图/封面失败。", file.getFileName(), file.getFileId());
                log.error("为文件 {} (ID:{}) 生成缩略图/封面失败。", file.getFileName(), file.getFileId(), e);
                // 可以选择跳过失败的，继续处理下一个
            } finally {
                processed++;
            }
        }
        
        updateProgress(progressCallback, total, processed, "所有任务处理完毕。");
    }

//    private void processImage(MediaFiles imageFile, float quality) throws Exception {
//        Path sourcePath = rootPath.resolve(imageFile.getFilePath());
//
//        // 准备目标目录和路径
//        Path targetDir = fileManagementUtil.createDirectory("Thumbnail/image");
//        Path targetPath = targetDir.resolve(imageFile.getFileName());
//        String thumbnailRelativePath = rootPath.relativize(targetPath).toString().replace('\\', '/');
//
//        // 使用 Thumbnailator 生成低质量图片
//        Thumbnails.of(sourcePath.toFile())
//                  .scale(1.0) // 保持原始尺寸
//                  .outputQuality(quality) // 控制输出质量
//                  .toFile(targetPath.toFile());
//
//        // 更新数据库
//        imageFile.setThumbnail(thumbnailRelativePath);
//        mediaFilesMapper.updateById(imageFile);
//        log.info("成功为图片 {} 生成缩略图。", imageFile.getFileName());
//    }
private void processImage(MediaFiles imageFile, float quality) throws Exception {
    Path sourcePath = rootPath.resolve(imageFile.getFilePath());

    Path targetDir = fileManagementUtil.createDirectory("Thumbnail/image");
    Path targetPath = targetDir.resolve(imageFile.getFileName());
    String thumbnailRelativePath = rootPath.relativize(targetPath).toString().replace('\\', '/');

    // --- 关键修改 ---
    // 不再使用.scale(1.0)，因为对于缩略图我们总是希望减小尺寸。
    // 我们定义一个目标尺寸，例如宽度为800像素。
    int targetWidth = 800;

    try {
        Thumbnails.of(sourcePath.toFile())
                // 使用 size() 并配合 keepAspectRatio() 是最常见的缩略图生成方式
                // Thumbnailator内部对大图有优化，但我们仍需确保JVM内存充足
                .size(targetWidth, targetWidth) // 限制最大宽/高为800，保持比例
                .keepAspectRatio(true)
                .outputQuality(quality)
                .toFile(targetPath.toFile());

    } catch (javax.imageio.IIOException e) {
        // 有些损坏的或特殊的CMYK格式的JPG文件可能会导致ImageIO失败
        log.error("Thumbnailator/ImageIO无法处理图片: {}, 尝试使用备用方案。", imageFile.getFileName(), e);
        // 这里可以添加一个备用方案，例如调用ImageMagick等外部工具
        // 或者直接放弃这张图片的缩略图生成
        throw e; // 暂时向上抛出异常
    }


    // 更新数据库
    imageFile.setThumbnail(thumbnailRelativePath);
    mediaFilesMapper.updateById(imageFile);
    log.info("成功为图片 {} 生成缩略图。", imageFile.getFileName());
}

    private void processVideo(MediaFiles videoFile, int second) throws Exception {
        Path sourcePath = rootPath.resolve(videoFile.getFilePath());

        // 准备目标目录和路径
        Path targetDir = fileManagementUtil.createDirectory("Thumbnail/video");
        String outputFileName = getNameWithoutExtension(videoFile.getFileName()) + ".jpg"; // 确保你有这个辅助方法
        Path targetPath = targetDir.resolve(outputFileName);
        String thumbnailRelativePath = rootPath.relativize(targetPath).toString().replace('\\', '/');

        // 拼接FFmpeg命令参数列表
        String timeStamp = String.format("%02d:%02d:%02d", second / 3600, (second % 3600) / 60, second % 60);
        List<String> commandList = Arrays.asList(
                "ffmpeg",
                "-y",
                "-ss", timeStamp,
                "-i", sourcePath.toAbsolutePath().toString(),
                "-vframes", "1",
                "-q:v", "2",
                targetPath.toAbsolutePath().toString()
        );

        // 使用 FfmpegService 执行命令
        ffmpegService.execute(commandList);

        // 更新数据库
        videoFile.setThumbnail(thumbnailRelativePath);
        mediaFilesMapper.updateById(videoFile);
        log.info("成功为视频 {} 生成封面。", videoFile.getFileName());
    }

    private void updateProgress(Consumer<TaskProgress> callback, long total, long processed, String step) {
        if (callback != null) {
            TaskProgress progress = new TaskProgress("THUMBNAIL");
            progress.setTotalTasks(total);
            progress.setProcessedTasks(processed);
            progress.setCurrentStep(step);
            progress.setPercentage(total > 0 ? (int)(100.0 * processed / total) : 0);
            progress.setStatus(ScanStatus.RUNNING);
            callback.accept(progress);
        }
    }
    /**
     * 手动实现：获取文件名但不包含其扩展名。
     * @param fileName 完整的文件名，例如 "my_video.mp4"
     * @return 不带扩展名的文件名，例如 "my_video"
     */
    private String getNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        // 如果没有找到 '.' 或者 '.' 是第一个字符 (例如 ".bashrc")，则返回完整文件名
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }
}
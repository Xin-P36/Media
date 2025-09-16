package org.xinp.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.xinp.entity.MediaFiles;
import org.xinp.mapper.MediaFilesMapper;
import org.xinp.pojo.TaskProgress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 任务处理器
 * 负责扫描目录、识别文件、调用FFmpeg进行转换和替换
 * 将非MP4格式的视频转换为标准的、Web友好的MP4格式。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoNormalizationProcessor {

    private final FfmpegService ffmpegService;
    private final FileManagementUtil fileManagementUtil;
    private final MediaFilesMapper mediaFilesMapper;
    @Qualifier("projectPath")
    private final Path rootPath;

    /**
     * 主执行方法
     * @param progressCallback 用于报告进度的回调
     * @throws Exception 如果任务失败
     */
    public void execute(Consumer<TaskProgress> progressCallback) throws Exception {
        // 1. 扫描 TemporaryMedia 目录
        Path tempMediaDir = fileManagementUtil.resolveSafely("TemporaryMedia");
        List<Path> allFiles;
        try (Stream<Path> walk = Files.walk(tempMediaDir)) {
            allFiles = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }

        // --- 2. 核心修改：使用 ffprobe 过滤出需要转换的视频文件 ---
        List<Path> videosToNormalize = new ArrayList<>();
        for (Path filePath : allFiles) {
            if (isNormalizationNeeded(filePath)) {
                videosToNormalize.add(filePath);
            }
        }

        long total = videosToNormalize.size();
        long processed = 0;
        updateProgress(progressCallback, total, processed, "任务初始化，找到 " + total + " 个待转换视频...");

        // 3. 遍历并转换每个视频
        for (Path sourcePath : videosToNormalize) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("任务被用户取消。");
            }

            updateProgress(progressCallback, total, processed, "正在转换: " + sourcePath.getFileName());

            try {
                normalizeVideo(sourcePath);
            } catch (Exception e) {
                log.error("转换文件 {} 失败。", sourcePath.getFileName(), e);
                // 选择跳过失败的，继续下一个
            } finally {
                processed++;
            }
        }
        updateProgress(progressCallback, total, processed, "所有视频转换完成。");
    }
    /**
     * 使用 ffprobe 判断一个文件是否需要被规范化为MP4。
     * @param videoPath 待检查的视频文件路径
     * @return 如果需要转换，返回 true
     */
    private boolean isNormalizationNeeded(Path videoPath) {
        try {
            // 首先进行简单的MIME类型判断，过滤掉非视频文件，避免对所有文件都调用ffprobe
            String contentType = Files.probeContentType(videoPath);
            if (contentType == null || !contentType.startsWith("video/")) {
                return false;
            }

            log.debug("正在探测文件: {}", videoPath.getFileName());
            JsonNode probeResult = ffmpegService.probe(videoPath);

            // 获取 format_name，它可能包含多个格式，用逗号分隔，如 "mov,mp4,m4a,3gp,3g2,mj2"
            String formatName = probeResult.path("format").path("format_name").asText("");

            // 判断 format_name 是否包含 mp4。如果不包含，或者包含 ts，则需要转换。
            // 兼容性好的MP4通常 format_name 是 "mov,mp4,..." 或 "isom"。
            // 伪MP4的 format_name 可能是 "mpegts"。
            boolean isMpegTs = formatName.contains("mpegts");
            boolean isMp4Container = formatName.contains("mp4");

            if (isMpegTs) {
                log.info("发现MPEG-TS容器文件，需要转换: {}", videoPath.getFileName());
                return true;
            }

            if (!isMp4Container) {
                log.info("发现非MP4容器文件 ({})，需要转换: {}", formatName, videoPath.getFileName());
                return true;
            }

            // (可选) 检查 moov atom 位置，如果不在文件开头，也需要转换
            // 这个检查比较复杂，需要解析 flags，暂时可以省略。
            // -movflags +faststart 已经可以解决这个问题。

            return false;
        } catch (Exception e) {
            log.error("探测文件 {} 失败，跳过该文件。", videoPath.getFileName(), e);
            return false;
        }
    }

    /**
     * 转换单个视频文件为MP4格式，并替换原文件
     * @param sourcePath 原始视频文件的绝对路径
     */
    private void normalizeVideo(Path sourcePath) throws Exception {
        // 1. 定义临时输出文件路径，防止直接覆盖原文件时失败
        Path tempOutputFile = sourcePath.getParent().resolve(UUID.randomUUID() + ".mp4");

        // 2. 构建FFmpeg命令
        // 使用 -c copy 可以实现“流拷贝”，如果原始编码（如H.264, AAC）已兼容MP4，
        // 这将极快地完成转换，因为它只重新封装容器而不重新编码。
        // -movflags +faststart 是Web视频优化的关键，它将moov atom（元数据）移到文件头部，
        // 使得视频可以边下载边播放。
        List<String> commandList = Arrays.asList(
            "ffmpeg",
            "-y",
            "-i", sourcePath.toAbsolutePath().toString(),
            "-c", "copy", // 尝试流拷贝，速度极快
            "-movflags", "+faststart",
            tempOutputFile.toAbsolutePath().toString()
        );

        try {
            // 3. 执行转换
            ffmpegService.execute(commandList);

            // 4. 替换原文件
            // 获取原文件名，但后缀改为 .mp4
            String originalName = sourcePath.getFileName().toString();
            String newName = getNameWithoutExtension(originalName) + ".mp4";
            Path finalPath = sourcePath.getParent().resolve(newName);

            // 用转换好的临时文件覆盖最终目标路径
            Files.move(tempOutputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
            // 如果新旧文件名不同，则删除原始文件
            if (!sourcePath.equals(finalPath)) {
                Files.delete(sourcePath);
            }
            log.info("成功将 {} 转换为MP4，并替换原文件。", originalName);
            
            // 5. 更新数据库 (如果该文件已入库)
            updateDatabaseRecord(sourcePath, finalPath);

        } catch (Exception e) {
            // 如果流拷贝失败（例如编码不兼容），则尝试完全重新编码
            log.warn("流拷贝失败: {}，将尝试完全重新编码...", e.getMessage());
            // 删除可能存在的失败的临时文件
            Files.deleteIfExists(tempOutputFile);
            normalizeVideoByReEncoding(sourcePath);
        } finally {
             // 确保临时文件最终被删除
            Files.deleteIfExists(tempOutputFile);
        }
    }

    /**
     * 备用方法：通过完全重新编码来转换视频
     */
    private void normalizeVideoByReEncoding(Path sourcePath) throws Exception {
         Path tempOutputFile = sourcePath.getParent().resolve(UUID.randomUUID() + ".mp4");
         List<String> commandList = Arrays.asList(
            "ffmpeg",
            "-y",
            "-i", sourcePath.toAbsolutePath().toString(),
            "-c:v", "libx264", // 使用兼容性最好的H.264编码
            "-c:a", "aac",     // 使用兼容性最好的AAC编码
            "-preset", "medium",
            "-crf", "23",
            "-movflags", "+faststart",
            tempOutputFile.toAbsolutePath().toString()
        );
        ffmpegService.execute(commandList);

        // 后续逻辑同上：替换文件、更新数据库...
        String originalName = sourcePath.getFileName().toString();
        String newName = getNameWithoutExtension(originalName) + ".mp4";
        Path finalPath = sourcePath.getParent().resolve(newName);
        Files.move(tempOutputFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
        if (!sourcePath.equals(finalPath)) {
             Files.delete(sourcePath);
        }
        log.info("成功将 {} (通过重新编码) 转换为MP4。", originalName);
        updateDatabaseRecord(sourcePath, finalPath);
    }
    
    /**
     * 更新数据库中对应的文件记录
     */
    private void updateDatabaseRecord(Path oldPath, Path newPath) {
        String oldRelativePath = rootPath.relativize(oldPath).toString().replace('\\', '/');
        
        LambdaQueryWrapper<MediaFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaFiles::getFilePath, oldRelativePath);
        MediaFiles fileRecord = mediaFilesMapper.selectOne(wrapper);
        
        if (fileRecord != null) {
            String newRelativePath = rootPath.relativize(newPath).toString().replace('\\', '/');
            fileRecord.setFilePath(newRelativePath);
            fileRecord.setFileName(newPath.getFileName().toString());
            fileRecord.setMimeType("video/mp4"); // 更新MIME类型
            mediaFilesMapper.updateById(fileRecord);
            log.info("数据库记录已更新，从 {} -> {}", oldRelativePath, newRelativePath);
        }
    }

    /**
     * 判断一个文件是否是需要转换的视频
     */
    private boolean isNonMp4Video(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            String fileName = path.getFileName().toString().toLowerCase();
            // 如果是视频类型，但文件名不是.mp4结尾
            return contentType != null && contentType.startsWith("video/") && !fileName.endsWith(".mp4");
        } catch (Exception e) {
            return false;
        }
    }

    // 你需要一个获取文件名不带扩展名的辅助方法
    private String getNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) return fileName;
        return fileName.substring(0, dotIndex);
    }
    
    private void updateProgress(Consumer<TaskProgress> callback, long total, long processed, String step) {
        // ... (代码同前一个回答)
    }
}
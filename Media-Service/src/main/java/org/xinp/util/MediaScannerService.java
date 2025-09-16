package org.xinp.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xinp.constant.FileStatus;
import org.xinp.entity.MediaFiles;
import org.xinp.mapper.MediaFilesMapper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 媒体文件扫描服务
 */
@Service
@Slf4j
public class MediaScannerService {

    private final MediaFilesMapper mediaFilesMapper;
    private final ObjectMapper objectMapper;
    private final Path rootPath; // -- 修正点 2.1: 注入项目根路径
    @Value("${media.system}")
    private String system;

    // -- 修正点 2.1: 修改构造函数以接收根路径
    public MediaScannerService(MediaFilesMapper mediaFilesMapper, ObjectMapper objectMapper, @Qualifier("projectPath") Path rootPath) {
        this.mediaFilesMapper = mediaFilesMapper;
        this.objectMapper = objectMapper;
        this.rootPath = rootPath;
    }

    public void scanAndIndexDirectory(Path directoryPath, ProgressCallback progressCallback) throws InterruptedException{
        // ... (这部分代码无需修改)
        log.info("开始扫描目录: {}", directoryPath);

        try (Stream<Path> paths = Files.walk(directoryPath)) {
            var fileList = paths.filter(Files::isRegularFile).toList();
            long totalFiles = fileList.size();
            long processedFiles = 0;

            if (totalFiles == 0) {
                log.info("目录为空，无需处理。");
                progressCallback.onProgress(0, 0, "目录为空", 100);
                return;
            }

            for (Path filePath : fileList) {
                if (Thread.currentThread().isInterrupted()) {
                    // 如果线程被中断，抛出异常，由上层(ScanTaskManager)捕获并处理
                    throw new InterruptedException("扫描任务已被取消");
                }
                try {
                    if (isAlreadyIndexed(filePath)) {
                        log.warn("文件已在数据库中，跳过: {}", filePath);
                        processedFiles++;
                        progressCallback.onProgress(totalFiles, processedFiles, "跳过已索引文件: " + filePath.getFileName(), (int) (100.0 * processedFiles / totalFiles));
                        continue;
                    }

                    MediaFiles mediaFile = processSingleFile(filePath);
                    mediaFilesMapper.insert(mediaFile);
                    log.info("成功索引文件: {}", filePath.getFileName());

                } catch (Exception e) {
                    log.error("处理文件 {} 失败", filePath, e);
                } finally {
                    processedFiles++;
                    progressCallback.onProgress(totalFiles, processedFiles, "正在处理: " + filePath.getFileName(), (int) (100.0 * processedFiles / totalFiles));
                }
            }
            log.info("目录扫描完成: {}", directoryPath);
        } catch (IOException e) {
            log.error("遍历目录失败: {}", directoryPath, e);
            progressCallback.onError("遍历目录失败: " + e.getMessage());
        }
    }

    public MediaFiles processSingleFile(Path filePath) throws Exception {
        MediaFiles mediaFile = new MediaFiles();

        // -- 修正点 2.2: 计算并存储相对路径
        Path relativePath = rootPath.relativize(filePath);
        String storedPath = relativePath.toString().replace('\\', '/'); // 统一路径分隔符

        mediaFile.setFileName(filePath.getFileName().toString());
        mediaFile.setFilePath(storedPath); // 使用相对路径
        mediaFile.setFileSize(Files.size(filePath));
        mediaFile.setMimeType(Files.probeContentType(filePath));
        mediaFile.setFileStatus(FileStatus.PENDING_CLASSIFICATION);
        mediaFile.setUpdateTime(System.currentTimeMillis());
        mediaFile.setFileHash(calculateFileHash(filePath));

        String mimeType = mediaFile.getMimeType();
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                extractImageMetadata(filePath, mediaFile);
            } else if (mimeType.startsWith("video/")) {
                extractVideoMetadata(filePath, mediaFile);
            } else if (mimeType.startsWith("audio/")) {
                extractVideoMetadata(filePath, mediaFile);
            }
        }

        return mediaFile;
    }

    // -- 修正点 2.3: 查询时也使用相对路径
    private boolean isAlreadyIndexed(Path filePath) {
        Path relativePath = rootPath.relativize(filePath);
        String storedPath = relativePath.toString().replace('\\', '/');

        return mediaFilesMapper.selectCount(
                new QueryWrapper<MediaFiles>().eq("file_path", storedPath)
        ) > 0;
    }

    private String calculateFileHash(Path filePath) {
        // ... (这部分代码无需修改)
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            return DigestUtils.sha256Hex(fis);
        } catch (IOException e) {
            log.error("计算文件哈希失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * -- 修正点 1: 更健壮的图片元数据提取方法
     * 依次尝试从 JPEG, PNG, EXIF 等多种元数据目录中获取尺寸信息。
     */
    private void extractImageMetadata(Path filePath, MediaFiles mediaFile) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(filePath.toFile());

            // 策略1：尝试从 JpegDirectory 获取 (最常见)
            JpegDirectory jpegDirectory = metadata.getFirstDirectoryOfType(JpegDirectory.class);
            if (jpegDirectory != null) {
                mediaFile.setWidth(jpegDirectory.getImageWidth());
                mediaFile.setHeight(jpegDirectory.getImageHeight());
                log.debug("从 JpegDirectory 成功提取尺寸: {}x{}", mediaFile.getWidth(), mediaFile.getHeight());
                return; // 成功获取，直接返回
            }

            // 策略2：尝试从 PngDirectory 获取
            PngDirectory pngDirectory = metadata.getFirstDirectoryOfType(PngDirectory.class);
            if (pngDirectory != null) {
                mediaFile.setWidth(pngDirectory.getInteger(PngDirectory.TAG_IMAGE_WIDTH));
                mediaFile.setHeight(pngDirectory.getInteger(PngDirectory.TAG_IMAGE_HEIGHT));
                log.debug("从 PngDirectory 成功提取尺寸: {}x{}", mediaFile.getWidth(), mediaFile.getHeight());
                return;
            }

            // 策略3：尝试从 ExifSubIFDDirectory 获取 (作为备选)
            ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifDir != null) {
                if (exifDir.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)) {
                    mediaFile.setWidth(exifDir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH));
                }
                if (exifDir.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)) {
                    mediaFile.setHeight(exifDir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT));
                }
                if (mediaFile.getWidth() != null && mediaFile.getHeight() != null) {
                    log.debug("从 ExifSubIFDDirectory 成功提取尺寸: {}x{}", mediaFile.getWidth(), mediaFile.getHeight());
                    return;
                }
            }

            log.warn("未能从任何已知的元数据目录中提取到图片尺寸: {}", filePath);

        } catch (Exception e) {
            log.error("提取图片元数据时发生异常: {}", filePath, e);
        }
    }


    private void extractVideoMetadata(Path filePath, MediaFiles mediaFile) {
        // ... (这部分代码无需修改)
        String command = String.format("ffprobe -v quiet -print_format json -show_format -show_streams \"%s\"", filePath.toAbsolutePath());

        try {
            //判断使用的系统
            ProcessBuilder processBuilder = null;
            if (system.equals("windows")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            }else{
                processBuilder = new ProcessBuilder("sh", "-c", command);
            }
            // 注意: 在Linux/macOS上，最好使用 "sh", "-c"
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("ffprobe 执行失败，退出码: {}，文件: {}", exitCode, filePath);
                return;
            }

            JsonNode rootNode = objectMapper.readTree(output.toString());
            mediaFile.setMetadata(rootNode.toString());

            JsonNode formatNode = rootNode.path("format");
            if (formatNode.has("duration")) {
                mediaFile.setDuration((long) (formatNode.get("duration").asDouble() * 1000));
            }

            for (JsonNode stream : rootNode.path("streams")) {
                if ("video".equals(stream.path("codec_type").asText())) {
                    mediaFile.setWidth(stream.path("width").asInt());
                    mediaFile.setHeight(stream.path("height").asInt());
                    break;
                }
            }
        } catch (Exception e) {
            log.error("执行ffprobe或解析其输出时出错: {}", filePath, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(long total, long processed, String currentFile, int percentage);
        default void onError(String message) {}
    }
}
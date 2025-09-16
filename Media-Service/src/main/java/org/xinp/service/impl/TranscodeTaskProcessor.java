package org.xinp.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.xinp.constant.FileStatus;
import org.xinp.entity.MediaFiles;
import org.xinp.entity.OperationLogs;
import org.xinp.mapper.MediaFilesMapper;
import org.xinp.service.OperationLogProcessor;
import org.xinp.util.FfmpegService;
import org.xinp.util.FileManagementUtil;
import org.xinp.util.MediaScannerService;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service("TRANSCODE_Processor")
@RequiredArgsConstructor
@Slf4j
public class TranscodeTaskProcessor implements OperationLogProcessor {

    private final MediaFilesMapper mediaFilesMapper;
    private final FileManagementUtil fileManagementUtil;
    private final MediaScannerService mediaScannerService;
    private final ObjectMapper objectMapper;
    private final FfmpegService ffmpegService; // 注入 FfmpegService
    @Qualifier("projectPath")
    private final Path rootPath;

    @Override
    public void process(OperationLogs log) throws Exception {
        JsonNode detail = objectMapper.readTree(log.getOperationDetail());
        String sourceFilePath = detail.get("sourceFilePath").asText();
        String outputFileName = detail.get("outputFileName").asText();
        // 修正路径获取，确保兼容根目录
        String outputTargetToolPathStr = detail.has("outputTargetToolPath") && !detail.get("outputTargetToolPath").isNull()
                ? detail.get("outputTargetToolPath").asText()
                : "/"; // 如果没有，默认为根目录
        Integer outputTargetToolId = detail.has("outputTargetToolId") && !detail.get("outputTargetToolId").isNull()
                ? detail.get("outputTargetToolId").asInt() : null;

        JsonNode params = detail.get("ffmpegParameters");

        // 1. 准备转码临时目录
        Path tempDir = fileManagementUtil.createDirectory("VideoTranscodingTemp");
        Path tempOutputFile = tempDir.resolve(java.util.UUID.randomUUID().toString() + "_" + outputFileName); // 使用UUID防止并发冲突

        // 2. 准备FFmpeg命令的参数列表
        List<String> commandList = buildFfmpegCommandAsList(
                rootPath.resolve(sourceFilePath),
                tempOutputFile,
                params
        );
        this.log.info("准备执行FFmpeg命令: {}", String.join(" ", commandList));

        // 3. 使用 ProcessBuilder 执行转码 (健壮的方式)
        ffmpegService.execute(commandList);
//        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
//        processBuilder.redirectErrorStream(true); // 合并标准错误和标准输出
//
//        Process process = processBuilder.start();
//
//        // 实时读取FFmpeg的输出，用于调试，并防止进程缓冲区阻塞
//        StringBuilder ffmpegOutput = new StringBuilder();
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                ffmpegOutput.append(line).append("\n");
//                this.log.debug("[FFMPEG]: {}", line);
//            }
//        }
//
//        int exitCode = process.waitFor();
//        if (exitCode != 0) {
//            // 如果失败，抛出包含FFmpeg完整输出的异常
//            throw new RuntimeException("FFmpeg转码失败 (退出码: " + exitCode + ")。输出: \n" + ffmpegOutput);
//        }

        // 4. 将转码成功的文件移动到最终位置
        String targetDirRelativePath = outputTargetToolPathStr.equals("/") ? "" : outputTargetToolPathStr.substring(1);
        String finalRelativePath = Paths.get(targetDirRelativePath).resolve(outputFileName).toString().replace('\\', '/');

        fileManagementUtil.moveFile(rootPath.relativize(tempOutputFile).toString(), finalRelativePath);

        // 5. 扫描新文件并入库
        MediaFiles newMediaFile = mediaScannerService.processSingleFile(rootPath.resolve(finalRelativePath));
        newMediaFile.setToolId(outputTargetToolId);
        newMediaFile.setFileStatus(FileStatus.AVAILABLE);
        mediaFilesMapper.insert(newMediaFile);

        // 6. 恢复源文件状态
        MediaFiles sourceFile = mediaFilesMapper.selectById(log.getFileId());
        if (sourceFile != null) {
            sourceFile.setFileStatus(FileStatus.AVAILABLE);
            mediaFilesMapper.updateById(sourceFile);
        }
    }

    private List<String> buildFfmpegCommandAsList(Path inputFile, Path outputFile, JsonNode params) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y"); // 覆盖输出文件
        command.add("-i");
        command.add(inputFile.toAbsolutePath().toString());

        JsonNode videoParams = params.path("video");
        JsonNode audioParams = params.path("audio");

        // 视频滤镜链 (-vf)
        List<String> videoFilters = new ArrayList<>();
        double speed = videoParams.path("speed").asDouble(1.0);
        if (speed <= 0) speed = 1.0;
        if (speed != 1.0) {
            videoFilters.add("setpts=" + (1.0 / speed) + "*PTS");
        }
        String resolution = videoParams.path("resolution").asText(null);
        if (StringUtils.hasText(resolution)) {
            videoFilters.add("scale=" + resolution);
        }
        if (!videoFilters.isEmpty()) {
            command.add("-vf");
            command.add(String.join(",", videoFilters));
        }

        // 音频滤镜链 (-af)
        List<String> audioFilters = new ArrayList<>();
        if (speed != 1.0) {
            if (speed >= 0.5 && speed <= 100.0) {
                audioFilters.add("atempo=" + speed);
            } else {
                log.warn("不支持的音频速度倍率 {}，将使用正常速度处理音频。", speed);
            }
        }
        if (!audioFilters.isEmpty()) {
            command.add("-af");
            command.add(String.join(",", audioFilters));
        }

        // 视频编码参数
        if (StringUtils.hasText(videoParams.path("codec").asText())) {
            command.add("-c:v");
            command.add(videoParams.path("codec").asText());
        }
        if (videoParams.has("crf") && !videoParams.get("crf").isNull()) {
            command.add("-crf");
            command.add(String.valueOf(videoParams.get("crf").asInt()));
        } else if (StringUtils.hasText(videoParams.path("bitrate").asText())) {
            command.add("-b:v");
            command.add(videoParams.path("bitrate").asText());
        }
        if (videoParams.has("framerate") && !videoParams.get("framerate").isNull()) {
            command.add("-r");
            command.add(String.valueOf(videoParams.get("framerate").asInt()));
        }

        // 音频编码参数
        if (StringUtils.hasText(audioParams.path("codec").asText())) {
            command.add("-c:a");
            command.add(audioParams.path("codec").asText());
        }
        if (StringUtils.hasText(audioParams.path("bitrate").asText())) {
            command.add("-b:a");
            command.add(audioParams.path("bitrate").asText());
        }

        // 输出文件
        command.add(outputFile.toAbsolutePath().toString());

        return command;
    }
}
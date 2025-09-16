package org.xinp.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FfmpegService {

    private final ObjectMapper objectMapper; // 注入ObjectMapper

    /**
     * 使用 ProcessBuilder 安全地执行一个FFmpeg命令。
     * @param commandList 包含命令和所有参数的列表
     * @return FFmpeg的完整输出日志
     * @throws RuntimeException 如果FFmpeg执行失败 (退出码不为0)
     * @throws InterruptedException 如果线程在等待时被中断
     */
    public String execute(List<String> commandList) throws InterruptedException {
        log.info("准备执行FFmpeg命令: {}", String.join(" ", commandList));

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        processBuilder.redirectErrorStream(true); // 合并标准错误和标准输出

        StringBuilder output = new StringBuilder();
        try {
            Process process = processBuilder.start();

            // 实时读取输出，防止缓冲区阻塞
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    log.debug("[FFMPEG]: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg执行失败 (退出码: " + exitCode + ")。输出: \n" + output);
            }
            
            log.info("FFmpeg命令成功执行。");
            return output.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 保持中断状态
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("执行FFmpeg命令时发生IO异常: " + e.getMessage(), e);
        }
    }
    /**
     * 使用 ffprobe 获取视频的元数据。
     * @param videoPath 视频文件的绝对路径
     * @return 解析后的JSON根节点
     * @throws RuntimeException 如果 ffprobe 执行失败
     */
    public JsonNode probe(Path videoPath) throws InterruptedException {
        List<String> commandList = Arrays.asList(
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                videoPath.toAbsolutePath().toString()
        );

        String jsonOutput = this.execute(commandList); // 复用execute方法
        try {
            return objectMapper.readTree(jsonOutput);
        } catch (Exception e) {
            throw new RuntimeException("解析ffprobe的JSON输出失败: " + e.getMessage(), e);
        }
    }
}
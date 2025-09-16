package org.xinp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xinp.entity.MediaFiles;
import org.xinp.pojo.*;
import org.xinp.service.MediaService;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 媒体文件管理
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * 媒体文件扫描
     * @param path 扫描路径
     * @return 执行结果
     */
    @PostMapping("/scan/start")
    public Result<String> mediaStartScan(@RequestParam("path") String path) {
        return mediaService.mediaStartScan(path);
    }

    /**
     * 进度查询
     * @return 进度信息
     */
    @GetMapping("/scan/progress")
    public Result<ScanProgress> getScanProgress() {
        return mediaService.getScanProgress();
    }

    /**
     * 停止扫描
     * @return 执行信息
     */
    @PostMapping("/scan/cancel")
    public Result<String> cancelScan() {
        return mediaService.cancelScan();
    }

    /**
     * 获取文件信息
     * @param toolId 分类ID
     * @param page 页码
     * @param pageSize  每页大小
     * @param keyword 关键字（模糊查询文件）
     * @return 文件列表
     */
    @GetMapping("/list")
    public Result<PageResult<MediaFileDTO>> getMediaList(
            @RequestParam(required = false) Integer toolId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String keyword
    ) {
        return mediaService.getMediaFilesList(toolId, page, pageSize, keyword);
    }

    /**
     * 文件上传，Nginx
     * @param tempFilePath 临时文件路径
     * @param originalFileName 重命名
     * @param targetToolId 目标分类ID
     * @return 文件信息
     * @throws UnsupportedEncodingException 临时文件路径编码错误
     */
    @PostMapping("/process-upload")
    public Result<MediaFiles> processUpload(
            @RequestHeader("X-Temp-File-Path") String tempFilePath,
            @RequestHeader("X-Original-File-Name") String originalFileName,
            @RequestHeader(value = "X-Target-Tool-Id", required = false) Integer targetToolId
    ) throws UnsupportedEncodingException {

        UploadFileDTO uploadInfo = new UploadFileDTO();
        uploadInfo.setTempFilePath(tempFilePath);
        // 解码文件名
        uploadInfo.setOriginalFileName(URLDecoder.decode(originalFileName, StandardCharsets.UTF_8));
        uploadInfo.setTargetToolId(targetToolId);
        return mediaService.processUploadedFile(uploadInfo);
    }
    /**
     * 文件分组（移动/重命名）接口
     * @param moveRequests 请求体，包含要操作的文件列表
     * @return 响应结果
     * [
     *   {"fileId":10, "toolId":1, "rename": "新名字.jpg"},
     *   {"fileId":9, "toolId":2, "rename": null}
     * ]
     */
    @PostMapping("/move")
    public Result<List<Long>> moveFiles(@RequestBody @Validated List<FileMoveRequestDTO> moveRequests) {
        return mediaService.moveFiles(moveRequests);
    }
    /**
     * 文件删除接口（标记删除）
     * @param fileIds 请求体，包含要删除的文件ID列表
     * @return 响应结果
     * [1, 2, 3]
     */
    @PostMapping("/delete")
    public Result<List<Long>> deleteFiles(@RequestBody List<Long> fileIds) {
        return mediaService.markFilesForDeletion(fileIds);
    }
    /**
     * 在文件详细下方添加新的模块，检测如果当前的展示的文件为视频类型时增加转码的功能区
     * 添加一个视频转码任务
     * @param taskDTO 包含转码参数的请求体
     * @return 新创建的操作日志ID
     * {
     *     "fileId": 102, // 必须，要转码的原始视频文件ID
     *     "outputFileName": "假期录像_高清版.mp4", // 非必须，“”或null就是使用原来的名称+转换后的文件格式
     *     "targetToolId": 8, // 非必须，为“”或null就是原来的分类
     *       "container": "mp4", // 容器格式 (mp4, mkv, flv...)
     *       "video": {
     *         "codec": "libx264", // 视频编码器 (libx264, libx265, vp9...)
     *         "bitrate": "4000k", // 固定码率
     *         "resolution": "1920x1080", // 分辨率 (e.g., "1280x720", "1920x-1" 表示等比缩放)
     *         "framerate": 25, // 帧率 (可选)
     *         "crf": 23 // 恒定速率因子，与bitrate互斥，优先级更高
     *         "speed": 2.0  // <-- 视频倍数（不给默认为1）
     *         },
     *       "audio": {
     *         "codec": "aac", // 音频编码器 (aac, mp3, opus...)
     *         "bitrate": "128k" // 音频码率 (e.g., "128k")
     *         }
     * }
     */
    @PostMapping("/transcode")
    public Result<Long> addTranscodeTask(@RequestBody @Validated TranscodeTaskDTO taskDTO) {
        return mediaService.addTranscodeTask(taskDTO);
    }

}
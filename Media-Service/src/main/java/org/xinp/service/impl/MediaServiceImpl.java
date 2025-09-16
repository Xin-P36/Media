package org.xinp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xinp.constant.Code;
import org.xinp.constant.FileStatus;
import org.xinp.constant.OperationLogStatus;
import org.xinp.entity.HideList;
import org.xinp.entity.MediaFiles;
import org.xinp.entity.OperationLogs;
import org.xinp.entity.ToolList;
import org.xinp.mapper.HideListMapper;
import org.xinp.mapper.MediaFilesMapper;
import org.xinp.mapper.OperationLogsMapper;
import org.xinp.mapper.ToolListMapper;
import org.xinp.pojo.*;
import org.xinp.service.MediaService;
import org.xinp.util.CurrentHolderUtils;
import org.xinp.util.FileManagementUtil;
import org.xinp.util.MediaScannerService;
import org.xinp.util.ScanTaskManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // 使用Lombok的构造函数注入，代替@Autowired
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final FileManagementUtil fileManagementUtil;
    private final ScanTaskManager scanTaskManager;
    private final MediaFilesMapper mediaFilesMapper;
    private final ToolListMapper toolListMapper;
    private final OperationLogsMapper operationLogsMapper;
    private final HideListMapper hideListMapper;

    private final ObjectMapper objectMapper; // Spring Boot 自动配置，用于序列化JSON
    private final MediaScannerService mediaScannerService; // 我们需要复用它的文件处理逻辑
    // Nginx配置的访问前缀是 /content
    private static final String CONTENT_URL_PREFIX = "/content/";
    @Qualifier("projectPath") // 注入项目根路径
    private final Path rootPath;

    /**
     * 媒体文件扫描
     *
     * @param scanPath 扫描路径
     * @return 执行结果
     */
    public Result mediaStartScan(String scanPath) {
        try {
            // 检查路径
            Path targetPath = fileManagementUtil.resolveSafely(scanPath);
            // 开始扫描
            scanTaskManager.startScan(targetPath);
            return Result.okResult();
        } catch (IllegalStateException e) {
            // 如果任务已在运行
            return Result.errorResult(Code.TASK_IN_EXECUTION.getCode(), Code.TASK_IN_EXECUTION.getMsg());
        } catch (Exception e) {
            // 其他路径解析等错误
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), Code.SYSTEM_ERROR.getMsg());
        }
    }

    /**
     * 获取扫描进度
     *
     * @return ScanProgress 对象
     * private ScanStatus status;         // 扫描状态
     * private long totalFiles = 0;       // 总文件数
     * private long processedFiles = 0;   // 已处理文件数
     * private int percentage = 0;        // 完成百分比
     * private String currentFileName = "";// 当前处理的文件名
     * private String message = "尚未开始"; // 状态信息或错误消息
     */
    public Result<ScanProgress> getScanProgress() {
        return Result.okResult(scanTaskManager.getCurrentProgress());
    }

    /**
     * 取消任务
     *
     * @return 响应
     */
    @Override
    public Result<String> cancelScan() {
        // 调用取消扫描任务
        scanTaskManager.cancelScan();
        return Result.okResult();
    }

    /**
     * 分页获取媒体文件列表的实现
     */
    @Override
    public Result<PageResult<MediaFileDTO>> getMediaFilesList(Integer toolId, Integer page, Integer pageSize, String keyword) {
        // --- a. 获取当前用户需要隐藏的所有分类ID ---
        Set<Integer> hiddenToolIds = getHiddenToolIdsForCurrentUser();

        // 1. 处理默认值
        int currentPage = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 50 : pageSize;

        // 2. 构建查询条件
        LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();

        // --- 核心修改：将 fileStatus 的判断用 and() 包裹起来 ---
        queryWrapper.and(qw ->
                qw.eq(MediaFiles::getFileStatus, FileStatus.PENDING_CLASSIFICATION)
                        .or()
                        .eq(MediaFiles::getFileStatus, FileStatus.AVAILABLE)
        );

        // --- 在此之后，再添加其他条件 ---
        // 条件：分类ID。
        if (toolId == null) {
            // 如果请求的是"待分类"，它不受隐藏影响
            queryWrapper.isNull(MediaFiles::getToolId);
        } else {
            // 如果请求的分类本身就在隐藏列表里，直接返回null
            if (hiddenToolIds.contains(toolId)) {
                return Result.okResult(null);
            }
            queryWrapper.eq(MediaFiles::getToolId, toolId);
        }

        // 条件：排除所有属于隐藏分类的文件
        if (!hiddenToolIds.isEmpty()) {
            queryWrapper.notIn(MediaFiles::getToolId, hiddenToolIds);
        }

        // 条件：关键字模糊搜索 (文件名)
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.like(MediaFiles::getFileName, keyword);
        }

        // 排序：可以根据需要添加，例如按更新时间降序
        queryWrapper.orderByDesc(MediaFiles::getUpdateTime);

        // 3. 执行分页查询
        Page<MediaFiles> pageRequest = new Page<>(currentPage, size);
        Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(pageRequest, queryWrapper);
        // 如果查询结果为空
        if (pageResult.getRecords().isEmpty()) {
            return Result.okResult(null);
        }

        // 4. 将 Page<MediaFiles> 转换为 Page<MediaFileDTO>
        Page<MediaFileDTO> dtoPage = new Page<>(currentPage, size, pageResult.getTotal());
        dtoPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));

        // 5. 封装成最终的 PageResult<MediaFileDTO> 并返回
        return Result.okResult(PageResult.from(dtoPage));
    }

    /**
     * 新增的辅助方法：获取当前用户所有需要隐藏的分类ID集合。
     *
     * @return 包含所有直接隐藏和间接隐藏的分类ID的Set
     */
    private Set<Integer> getHiddenToolIdsForCurrentUser() {
        String currentUserIdStr = CurrentHolderUtils.getCurrentUser();
        // 如果未登录，不隐藏任何内容
        if (currentUserIdStr == null) {
            return Collections.emptySet();
        }
        Long currentUserId = Long.parseLong(currentUserIdStr);

        // a. 获取用户直接设置的隐藏列表
        LambdaQueryWrapper<HideList> hideQuery = new LambdaQueryWrapper<>();
        hideQuery.eq(HideList::getUserId, currentUserId);
        List<HideList> directHides = hideListMapper.selectList(hideQuery);

        if (directHides.isEmpty()) {
            return Collections.emptySet();
        }

        // b. 获取所有分类，用于在内存中查找子孙
        List<ToolList> allTools = toolListMapper.selectList(null);

        // c. 计算完整的隐藏ID集合
        Set<Integer> fullHiddenIds = new HashSet<>();
        for (HideList hide : directHides) {
            findChildrenIds(hide.getHideId(), allTools, fullHiddenIds); // 递归查找子孙
            fullHiddenIds.add(hide.getHideId()); // 添加自身
        }

        return fullHiddenIds;
    }

    // 递归查找子孙ID的方法，注意这里第三个参数类型改为了Set
    private void findChildrenIds(Integer parentId, List<ToolList> allTools, Set<Integer> childrenIds) {
        List<ToolList> directChildren = allTools.stream()
                .filter(tool -> parentId.equals(tool.getParentId()))
                .collect(Collectors.toList());

        for (ToolList child : directChildren) {
            if (childrenIds.add(child.getToolId())) { // 使用Set的add方法避免重复添加和无限递归
                findChildrenIds(child.getToolId(), allTools, childrenIds);
            }
        }
    }

    /**
     * 辅助方法：将 MediaFiles 实体转换为 MediaFileDTO
     *
     * @param entity 数据库实体
     * @return DTO对象
     */
    private MediaFileDTO convertToDTO(MediaFiles entity) {
        MediaFileDTO dto = new MediaFileDTO();
        BeanUtils.copyProperties(entity, dto); // 复制大部分同名属性

        // 关键转换：将物理路径转换为可访问的URL
        dto.setFileUrl(CONTENT_URL_PREFIX + entity.getFilePath());

        // 如果有缩略图，也转换其路径
        if (StringUtils.isNotBlank(entity.getThumbnail())) {
            dto.setThumbnailUrl(CONTENT_URL_PREFIX + entity.getThumbnail());
        } else {
            // 如果是图片且没有独立缩略图，可以考虑让缩略图URL等于原图URL
            if (entity.getMimeType() != null && entity.getMimeType().startsWith("image/")) {
                dto.setThumbnailUrl(dto.getFileUrl());
            }
        }
        return dto;
    }

    /**
     * 文件上传
     */
    @Override
    @Transactional // 事务，确保文件移动和数据库写入的原子性
    public Result<MediaFiles> processUploadedFile(UploadFileDTO uploadInfo) {
        // Nginx传来的绝对路径，例如 "/var/www/uploads/0000000001"
        Path tempAbsoluteFilePath = Paths.get(uploadInfo.getTempFilePath());
        // 将绝对路径转换为相对于项目根目录的路径
        // rootPath 是 "/var/www"
        // tempAbsoluteFilePath 是 "/var/www/uploads/0000000001"
        // tempRelativePath 将会是 "uploads/0000000001"
        Path tempRelativePath = rootPath.relativize(tempAbsoluteFilePath);
        String tempRelativePathStr = tempRelativePath.toString().replace('\\', '/');
        try {
            // 安全检查：我们现在可以直接使用 FileManagementUtil 的方法来检查文件是否存在
            // 因为它内部会用 resolveSafely 转换回绝对路径进行检查
            if (!Files.exists(fileManagementUtil.resolveSafely(tempRelativePathStr))) {
                log.error("Nginx转发的临时文件不存在: {}", tempAbsoluteFilePath);
                return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "服务器内部错误：临时文件丢失");
            }

            // 校验文件并提取元数据 (复用MediaScannerService的核心逻辑)
            // 传入绝对路径进行处理，因为文件I/O需要绝对路径
            MediaFiles mediaFile = mediaScannerService.processSingleFile(tempAbsoluteFilePath);

            // 校验失败的判断
            String mimeType = mediaFile.getMimeType();
            if (mimeType != null) {
                if (mimeType.startsWith("video/") && mediaFile.getWidth() == null) {
                    throw new IOException("无法解析视频元数据，文件可能已损坏。");
                }
                if (mimeType.startsWith("image/") && mediaFile.getWidth() == null) {
                    throw new IOException("无法解析图片元数据，文件可能已损坏。");
                }
            }
            // 确定最终存储的相对路径和状态
            String finalRelativePath;
            if (uploadInfo.getTargetToolId() != null) {
                ToolList tool = toolListMapper.selectById(uploadInfo.getTargetToolId());
                if (tool == null) {
                    return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "指定的目标分类不存在");
                }
                // e.g., "分类A/我的照片.jpg"
                finalRelativePath = Paths.get(tool.getPath()).resolve(uploadInfo.getOriginalFileName()).toString().replace('\\', '/');
                mediaFile.setToolId(uploadInfo.getTargetToolId());
                mediaFile.setFileStatus(FileStatus.AVAILABLE);
            } else {
                // e.g., "TemporaryMedia/我的照片.jpg"
                finalRelativePath = Paths.get("TemporaryMedia").resolve(uploadInfo.getOriginalFileName()).toString().replace('\\', '/');
                mediaFile.setFileStatus(FileStatus.PENDING_CLASSIFICATION);
            }

            // 移动文件 (现在可以使用你现有的 moveFile 方法)
            log.info("准备移动文件: from '{}' to '{}'", tempRelativePathStr, finalRelativePath);
            fileManagementUtil.moveFile(tempRelativePathStr, finalRelativePath);

            // 5. 更新数据库记录
            mediaFile.setFileName(uploadInfo.getOriginalFileName());
            mediaFile.setFilePath(finalRelativePath); // 存储最终的相对路径
            mediaFile.setUpdateTime(System.currentTimeMillis());

            // 因为我们是移动文件，processSingleFile 中计算的 hash, size, metadata等都是正确的，可以直接用
            // 只有文件名和路径需要更新
            mediaFilesMapper.insert(mediaFile);

            log.info("文件 {} 成功处理并入库，ID: {}", mediaFile.getFileName(), mediaFile.getFileId());
            return Result.okResult(mediaFile);

        } catch (Exception e) {
            //log.error("处理上传文件失败: {}, 错误: {}", tempAbsoluteFilePath, e.getMessage(), e);
            log.error("处理上传文件失败: {}, 错误: {}", tempAbsoluteFilePath, e.getMessage());

            // 如果处理过程中发生任何异常，确保删除临时文件
            try {
                // 异常情况下，我们仍然用deleteFile并传入相对路径来删除
                fileManagementUtil.deleteFile(tempRelativePathStr);
                log.info("已自动清理处理失败的临时文件: {}", tempRelativePathStr);
            } catch (Exception deleteException) {
                // 如果删除也失败，记录严重错误，可能需要手动干预
                log.error("!!! 严重错误: 自动清理临时文件 {} 失败", tempRelativePathStr);
                //log.error("!!! 严重错误: 自动清理临时文件 {} 失败", tempRelativePathStr, deleteException);
            }

            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "文件处理失败: " + e.getMessage());
        }
    }

    /**
     * 移动文件
     */
    @Override
    @Transactional // 保证所有文件的校验和记录创建在一个事务中完成
    public Result<List<Long>> moveFiles(List<FileMoveRequestDTO> moveRequests) {
        if (moveRequests == null || moveRequests.isEmpty()) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "请求列表不能为空");
        }

        // --- 1. 批量预校验 ---
        // 提取所有需要查询的ID
        List<Long> fileIds = moveRequests.stream().map(FileMoveRequestDTO::getFileId).collect(Collectors.toList());
        List<Integer> toolIds = moveRequests.stream().map(FileMoveRequestDTO::getToolId).distinct().collect(Collectors.toList());

        // 一次性从数据库查询出所有涉及的文件和分类，提高性能
        Map<Long, MediaFiles> mediaFilesMap = mediaFilesMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(MediaFiles::getFileId, file -> file));

        Map<Integer, ToolList> toolListMap = toolListMapper.selectBatchIds(toolIds).stream()
                .collect(Collectors.toMap(ToolList::getToolId, tool -> tool));

        List<Long> createdOperationLogIds = new ArrayList<>();

        // --- 2. 逐个处理请求 ---
        for (FileMoveRequestDTO request : moveRequests) {
            // a. 校验文件是否存在
            MediaFiles mediaFile = mediaFilesMap.get(request.getFileId());
            if (mediaFile == null) {
                // 如果在事务中有一个失败，整个事务会回滚。
                // 或者你可以选择跳过这个错误的，继续处理其他的，取决于业务需求。
                // 这里我们选择让整个操作失败。
                throw new RuntimeException("文件校验失败：未找到ID为 " + request.getFileId() + " 的文件。");
            }

            // b. 校验目标分类是否存在
            ToolList targetTool = toolListMap.get(request.getToolId());
            if (targetTool == null) {
                throw new RuntimeException("分类校验失败：未找到ID为 " + request.getToolId() + " 的分类。");
            }

            // c. 校验文件状态是否允许移动（例如，不能移动正在处理或已锁定的文件）
            if (mediaFile.getFileStatus() == FileStatus.PROCESSING || mediaFile.getFileStatus() == FileStatus.LOCKED) {
                throw new RuntimeException("文件状态错误：文件 " + mediaFile.getFileName() + " 当前状态为 " + mediaFile.getFileStatus().getDescription() + "，无法移动。");
            }

            // d. 校验重命名后是否会产生路径冲突
            String newFileName = StringUtils.isNotBlank(request.getRename()) ? request.getRename() : mediaFile.getFileName();
            Path finalPath = Paths.get(targetTool.getPath(), newFileName);

            // (可选，但推荐) 检查目标路径是否已存在其他文件
            // 此处省略了检查逻辑，因为实际移动时会处理。但提前检查可以提供更友好的提示。

            // --- 3. 更新文件状态 ---
            // 将文件状态更新为 "处理中"，防止在后台任务执行前被再次操作
            mediaFile.setFileStatus(FileStatus.PROCESSING);
            mediaFilesMapper.updateById(mediaFile);

            // --- 4. 创建操作日志 ---
            OperationLogs log = new OperationLogs();
            log.setFileId(request.getFileId());
            log.setOperationType("MOVE"); // 定义一个操作类型
            log.setStatus(OperationLogStatus.PENDING); // 待处理状态
            log.setOperationTime(System.currentTimeMillis());

            // 将移动的细节存入JSON，方便后台任务读取
            // 使用Map可以方便地序列化为JSON
            Map<String, Object> detail = Map.of(
                    "targetToolId", request.getToolId(),
                    "targetToolPath", targetTool.getPath(),
                    "newFileName", newFileName
            );

            try {
                // 使用ObjectMapper将Map转为JSON字符串
                log.setOperationDetail(objectMapper.writeValueAsString(detail));
            } catch (Exception e) {
                throw new RuntimeException("序列化操作详情失败");
                //throw new RuntimeException("序列化操作详情失败", e);
            }

            operationLogsMapper.insert(log);
            createdOperationLogIds.add(log.getOperationId());
        }

        // --- 5. 返回成功响应 ---
        return Result.okResult(createdOperationLogIds);
    }

    /**
     * 文件删除
     *
     * @param fileIds 要删除的文件的ID列表
     * @return 删除成功后的结果
     */
    @Override
    @Transactional // 保证所有文件的标记和日志创建在一个事务中完成
    public Result<List<Long>> markFilesForDeletion(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "文件ID列表不能为空");
        }

        // --- 1. 批量预校验 ---
        // 一次性从数据库查询出所有涉及的文件
        Map<Long, MediaFiles> mediaFilesMap = mediaFilesMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(MediaFiles::getFileId, file -> file));

        List<Long> createdOperationLogIds = new ArrayList<>();

        // --- 2. 逐个处理请求 ---
        for (Long fileId : fileIds) {
            // a. 校验文件是否存在
            MediaFiles mediaFile = mediaFilesMap.get(fileId);
            if (mediaFile == null) {
                // 如果在事务中有一个失败，整个事务会回滚
                throw new RuntimeException("文件校验失败：未找到ID为 " + fileId + " 的文件。");
            }

            // b. 校验文件状态是否允许删除
            if (mediaFile.getFileStatus() == FileStatus.PROCESSING || mediaFile.getFileStatus() == FileStatus.LOCKED) {
                throw new RuntimeException("文件状态错误：文件 " + mediaFile.getFileName() + " 当前状态为 " + mediaFile.getFileStatus().getDescription() + "，无法删除。");
            }

            // 如果文件已经是待删除状态，可以选择跳过或报错。这里我们选择幂等处理，直接跳过。
            if (mediaFile.getFileStatus() == FileStatus.MARKED_FOR_DELETION) {
                log.warn("文件 {} (ID: {}) 已处于待删除状态，跳过重复操作。", mediaFile.getFileName(), fileId);
                continue;
            }

            // --- 3. 更新文件状态 ---
            mediaFile.setFileStatus(FileStatus.MARKED_FOR_DELETION);
            // 记录更新时间，方便追踪文件何时被移入回收站
            mediaFile.setUpdateTime(System.currentTimeMillis());
            mediaFilesMapper.updateById(mediaFile);

            // --- 4. 创建操作日志 ---
            OperationLogs log = new OperationLogs();
            log.setFileId(fileId);
            log.setOperationType("DELETE"); // 定义删除操作类型
            log.setStatus(OperationLogStatus.PENDING); // 待处理状态
            log.setOperationTime(System.currentTimeMillis());

            // 对于删除操作，operationDetail可以存储被删除文件的原始路径，方便恢复或记录
            // 使用Map可以方便地序列化为JSON
            Map<String, Object> detail = Map.of(
                    "originalFilePath", mediaFile.getFilePath()
            );

            try {
                // 使用ObjectMapper将Map转为JSON字符串
                log.setOperationDetail(objectMapper.writeValueAsString(detail));
            } catch (Exception e) {
                throw new RuntimeException("序列化操作详情失败");
                //throw new RuntimeException("序列化操作详情失败", e);
            }

            operationLogsMapper.insert(log);
            createdOperationLogIds.add(log.getOperationId());
        }

        // --- 5. 返回成功响应 ---
        return Result.okResult(createdOperationLogIds);
    }

    /**
     * 添加转码任务
     *
     * @param taskDTO 转码任务信息
     * @return 添加成功后的结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> addTranscodeTask(TranscodeTaskDTO taskDTO) {
        // --- 1. 校验源文件 ---
        MediaFiles sourceFile = mediaFilesMapper.selectById(taskDTO.getFileId());
        if (sourceFile == null) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "源文件 (ID: " + taskDTO.getFileId() + ") 不存在。");
        }
        if (sourceFile.getMimeType() == null || !sourceFile.getMimeType().startsWith("video/")) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "源文件不是一个有效的视频文件。");
        }
        if (sourceFile.getFileStatus() == FileStatus.PROCESSING || sourceFile.getFileStatus() == FileStatus.LOCKED) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "源文件当前状态为 " + sourceFile.getFileStatus().getDescription() + "，无法进行操作。");
        }

        // --- 2. 解析和校验目标信息 ---
        // a. 确定目标分类
        Integer targetToolId = taskDTO.getTargetToolId() != null ? taskDTO.getTargetToolId() : sourceFile.getToolId();
        ToolList targetTool = null;
        if (targetToolId != null) {
            targetTool = toolListMapper.selectById(targetToolId);
            if (targetTool == null) {
                return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "目标分类 (ID: " + targetToolId + ") 不存在。");
            }
        }

        // b. 确定输出文件名
        String outputFileName;
        if (StringUtils.isNotBlank(taskDTO.getOutputFileName())) {
            outputFileName = taskDTO.getOutputFileName();
        } else {
            // 使用原文件名（不含扩展名） + 新的容器格式后缀
            String originalNameWithoutExt = getNameWithoutExtension(sourceFile.getFileName());
            outputFileName = originalNameWithoutExt + "." + taskDTO.getContainer();
        }

        // c. 校验输出文件路径是否冲突
        String targetToolPath = (targetTool != null) ? targetTool.getPath() : "TemporaryMedia"; // 假设无分类的文件在TemporaryMedia
        String finalRelativePath = Paths.get(targetToolPath.substring(1)).resolve(outputFileName).toString().replace('\\', '/');

        LambdaQueryWrapper<MediaFiles> pathCheckWrapper = new LambdaQueryWrapper<>();
        pathCheckWrapper.eq(MediaFiles::getFilePath, finalRelativePath);
        if (mediaFilesMapper.selectCount(pathCheckWrapper) > 0) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "目标路径下已存在同名文件: " + finalRelativePath);
        }

        // --- 3. 更新源文件状态 ---
        sourceFile.setFileStatus(FileStatus.PROCESSING);
        mediaFilesMapper.updateById(sourceFile);

        // --- 4. 创建操作日志 ---
        OperationLogs log = new OperationLogs();
        log.setFileId(sourceFile.getFileId());
        log.setOperationType("TRANSCODE");
        log.setStatus(OperationLogStatus.PENDING);
        log.setOperationTime(System.currentTimeMillis());

        // 构建详细的、自包含的 operationDetail
        Map<String, Object> detail = Map.of(
                "sourceFilePath", sourceFile.getFilePath(),
                "outputFileName", outputFileName,
                "outputTargetToolId", targetToolId,
                "outputTargetToolPath", (targetTool != null) ? targetTool.getPath() : null, // 存下快照
                "ffmpegParameters", taskDTO // 直接将整个DTO存入，因为其结构已经很适合作为参数了
        );

        try {
            log.setOperationDetail(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            this.log.error("序列化转码任务详情失败");
            //this.log.error("序列化转码任务详情失败", e);
            throw new RuntimeException("创建转码任务失败：无法序列化任务详情。");
        }

        operationLogsMapper.insert(log);

        return Result.okResult(log.getOperationId());
    }

    /**
     * 手动实现：获取文件名但不包含其扩展名。
     *
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

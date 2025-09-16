package org.xinp.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xinp.constant.FileStatus;
import org.xinp.entity.MediaFiles;
import org.xinp.entity.OperationLogs;
import org.xinp.mapper.MediaFilesMapper;
import org.xinp.service.OperationLogProcessor;
import org.xinp.util.FileManagementUtil;

import java.nio.file.Paths;

@Service("MOVE_Processor") // 命名处理器Bean，方便注入
@RequiredArgsConstructor
@Slf4j
public class MoveTaskProcessor implements OperationLogProcessor {
    private final MediaFilesMapper mediaFilesMapper;
    private final FileManagementUtil fileManagementUtil;
    private final ObjectMapper objectMapper;

    @Override
    public void process(OperationLogs log) throws Exception {
        JsonNode detail = objectMapper.readTree(log.getOperationDetail());
        String newFileName = detail.get("newFileName").asText();
        String targetToolPath = detail.get("targetToolPath").asText();
        Integer targetToolId = detail.get("targetToolId").asInt();
        
        MediaFiles mediaFile = mediaFilesMapper.selectById(log.getFileId());
        if (mediaFile == null) {
            throw new RuntimeException("源文件 (ID: " + log.getFileId() + ") 在执行任务时已不存在。");
        }
        
        String oldRelativePath = mediaFile.getFilePath();
        String newRelativePath = Paths.get(targetToolPath.substring(1)).resolve(newFileName).toString().replace('\\', '/');

        // 1. 移动物理文件
        fileManagementUtil.moveFile(oldRelativePath, newRelativePath);
        
        // 2. 更新数据库记录
        mediaFile.setFileName(newFileName);
        mediaFile.setFilePath(newRelativePath);
        mediaFile.setToolId(targetToolId);
        mediaFile.setFileStatus(FileStatus.AVAILABLE); // 恢复状态
        mediaFilesMapper.updateById(mediaFile);
    }
}
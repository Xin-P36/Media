package org.xinp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.xinp.entity.MediaFiles;
import org.xinp.entity.OperationLogs;
import org.xinp.mapper.MediaFilesMapper;
import org.xinp.service.OperationLogProcessor;
import org.xinp.util.FileManagementUtil;

@Service("DELETE_Processor")
@RequiredArgsConstructor
public class DeleteTaskProcessor implements OperationLogProcessor {
    private final MediaFilesMapper mediaFilesMapper;
    private final FileManagementUtil fileManagementUtil;

    @Override
    public void process(OperationLogs log) throws Exception {
        MediaFiles mediaFile = mediaFilesMapper.selectById(log.getFileId());
        if (mediaFile == null) {
            // 文件记录可能已被其他操作删除，这是正常情况，直接视为成功
            return; 
        }

        // 1. 删除物理文件
        fileManagementUtil.deleteFile(mediaFile.getFilePath());
        if (StringUtils.isNotBlank(mediaFile.getThumbnail())) {
            fileManagementUtil.deleteFile(mediaFile.getThumbnail());
        }

        // 2. 从数据库中彻底删除记录
        mediaFilesMapper.deleteById(log.getFileId());
    }
}
package org.xinp.service;

import org.xinp.entity.MediaFiles;
import org.xinp.entity.ToolList;
import org.xinp.pojo.*;

import java.util.List;

/**
 * 媒体文件服务
 */
public interface MediaService {
    //启动扫描
    Result mediaStartScan(String scanPath);
    //获取扫描进度
    Result<ScanProgress> getScanProgress();
    //取消扫描
    Result<String> cancelScan();
    /**
     * 新增方法：分页获取媒体文件列表
     * @param toolId   分类ID
     * @param page     当前页
     * @param pageSize 每页数量
     * @param keyword  搜索关键字
     * @return 分页结果
     */
    Result<PageResult<MediaFileDTO>> getMediaFilesList(Integer toolId, Integer page, Integer pageSize, String keyword);
    /**
     * 处理由Nginx上传并转发过来的单个文件。
     * @param uploadInfo 包含临时文件路径和元数据的DTO
     * @return 处理结果，成功时data部分可以返回新创建的MediaFile对象
     */
    Result<MediaFiles> processUploadedFile(UploadFileDTO uploadInfo);
    /**
     * 将多个文件移动或重命名到指定分类。
     * 这是一个异步操作，会先创建操作日志，再由后台任务执行。
     * @param moveRequests 包含文件ID、目标分类ID和可选重命名信息的列表
     * @return 操作结果，成功时data中可返回创建的操作日志ID列表
     */
    Result<List<Long>> moveFiles(List<FileMoveRequestDTO> moveRequests);
    /**
     * 将多个文件标记为待删除。
     * 这是一个异步操作，会更新文件状态并创建删除日志。
     * @param fileIds 要删除的文件的ID列表
     * @return 操作结果，成功时data中可返回创建的操作日志ID列表
     */
    Result<List<Long>> markFilesForDeletion(List<Long> fileIds);
    /**
     * 添加一个新的视频转码任务到操作日志队列。
     * @param taskDTO 包含转码所有参数的DTO
     * @return 操作结果，成功时data为新创建的操作日志ID
     */
    Result<Long> addTranscodeTask(TranscodeTaskDTO taskDTO);
}

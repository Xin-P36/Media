package org.xinp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import org.xinp.constant.OperationLogStatus;

// 操作记录
@Data
public class OperationLogs {
    //操作ID
    @TableId(type = IdType.AUTO)
    private Long operationId;
    //文件ID
    private Long fileId;
    //操作类型
    private String operationType;
    //操作详细Json（操作类型为分类：这里就是移动到哪里。操作为压缩：这里为压缩的分辨率码率等，操作为删除：这里就是null）
    private String operationDetail;
    //状态
    private OperationLogStatus status;
    //失败原因
    private String errorMessage;
    //操作时间
    private Long operationTime;
}

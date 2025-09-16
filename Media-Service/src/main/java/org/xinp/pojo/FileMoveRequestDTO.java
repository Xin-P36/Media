package org.xinp.pojo;

import lombok.Data;

/**
 * 文件移动请求
 */
@Data
public class FileMoveRequestDTO {
    private Long fileId; //文件 ID
    private Integer toolId; //分类 ID
    private String rename; //重命名
}
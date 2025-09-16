package org.xinp.pojo;

import lombok.Data;

/**
 * 文件分类/移动/重命名请求
 */
@Data
public class FileMoveItemDTO {
    private Long fileId;      // 被移动的文件ID (数据库中的主键)
    private Integer toolId;     // 目标分类ID
    private String rename;    // 新的文件名（不含扩展名），可选
}
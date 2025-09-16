package org.xinp.pojo;

import lombok.Data;

/**
 * 文件上传
 */
@Data
public class UploadFileDTO {
    private String tempFilePath; // Nginx保存的临时文件绝对路径
    private String originalFileName; // 原始文件名
    private Integer targetToolId; // 目标分类ID, 可以为null
}
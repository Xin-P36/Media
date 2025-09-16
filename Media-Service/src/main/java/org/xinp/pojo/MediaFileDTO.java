package org.xinp.pojo;

import lombok.Data;

/**
 * 媒体文件返回结果
 */
@Data
public class MediaFileDTO {
    private Long fileId; // 使用数据库的自增ID
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private Integer toolId;
    private Integer width;
    private Integer height;
    private Long duration; // 毫秒
    private String fileUrl;      // 关键：文件内容的访问URL
    private String thumbnailUrl; // 关键：缩略图的访问URL
}
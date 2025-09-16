package org.xinp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.xinp.constant.FileStatus;

//文件信息信息
@Data
@TableName("media_files")
public class MediaFiles {
    //文件id 使用数据库生成的字段
    @TableId(type = IdType.AUTO)
    private Long fileId;
    //文件名称
    private String fileName;
    //文件类型
    private String mimeType;
    //文件大小
    private Long fileSize;
    //文件状态
    private FileStatus fileStatus;
    //所属分类
    private Integer toolId;
    //文件路径
    private String filePath;
    //视频/图片分辨率
    private Integer width;
    private Integer height;
    //视频/音乐播放时长
    private Long duration;
    //缩略图/另类版本路径
    private String thumbnail;
    //文件哈希值
    private String fileHash;
    //文件详细信息JSON信息
    private String metadata;
    //文件更新时间
    private Long updateTime;
}

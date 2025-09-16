package org.xinp.pojo;

import lombok.Data;

/**
 * 视频处理选项
 */
@Data
public class VideoOptionsDTO {
    private String codec;
    private String bitrate;
    private String resolution;
    private Integer framerate;
    private Integer crf; // 恒定速率因子
    // --- 新增字段 ---
    /**
     * 视频播放速度倍率。例如：
     * 1.0 (默认值，正常速度)
     * 2.0 (2倍速)
     * 0.5 (0.5倍慢速)
     * 如果为null或<=0，则视为1.0。
     */
    private Double speed;
}
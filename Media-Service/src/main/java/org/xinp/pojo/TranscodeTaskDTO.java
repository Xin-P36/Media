package org.xinp.pojo;

import com.drew.lang.annotations.NotNull;
import lombok.Data;

/**
 * 转码任务参数
 */
@Data
public class TranscodeTaskDTO {

    @NotNull
    private Long fileId;

    private String outputFileName; // 可选

    private Integer targetToolId; // 可选

    @NotNull
    private String container;

    @NotNull
    private VideoOptionsDTO video;

    @NotNull
    private AudioOptionsDTO audio;
}
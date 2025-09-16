package org.xinp.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.xinp.constant.ScanStatus;

/**
 * 媒体扫描进度
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanProgress {
    private ScanStatus status;
    private long totalFiles = 0;
    private long processedFiles = 0;
    private int percentage = 0;
    private String currentFileName = "";
    private String message = "尚未开始"; // 用于显示状态信息，如错误消息
}
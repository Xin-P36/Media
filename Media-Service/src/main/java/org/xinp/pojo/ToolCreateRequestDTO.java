package org.xinp.pojo;

import lombok.Data;

@Data
public class ToolCreateRequestDTO {
    private String toolName; // 分类名称
    private String path; // 分类路径
    private String description;
    // sort是可选的，可以给个默认值
    private Integer sort = 100;
    // parentId是可选的
    private Integer parentId;
    // coverImageUrl是可选的
    private String coverImageUrl;
}
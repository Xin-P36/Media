package org.xinp.pojo;

import lombok.Data;

/**
 * 分类列表更新请求
 */
@Data
public class ToolUpdateRequestDTO {

    private Integer toolId;
    
    // 以下所有字段都是可选的
    private String toolName;
    private String path;
    private String description;
    private Integer sort;
    private String coverImageUrl;
    
    // parentId 可以为 null，但我们用 Integer 对象来接收，
    // 如果前端传 "" (空字符串)，JSON反序列化可能会出错或变为null，
    // 需要前端配合传 null 或不传该字段来表示顶级分类。
    private Integer parentId; 
}
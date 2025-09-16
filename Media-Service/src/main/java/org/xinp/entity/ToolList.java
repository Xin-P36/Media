package org.xinp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

//分类列表信息
@Data
public class ToolList {
    //分类ID
    @TableId(type = IdType.AUTO) // 主键自增
    private Integer toolId;
    //名称
    private String toolName;
    //路径
    private String path;
    //描述
    private String description;
    //排序
    private Integer sort;
    //父级ID
    private Integer parentId;
    //封面图片
    private String coverImageUrl;
    //创建时间
    private Long createTime;
}

package org.xinp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

//用户隐藏分类列表
@Data
public class HideList {
    //隐藏分类ID 使用数据库自增ID
    @TableId(type = IdType.AUTO)
    private Long id;
    //用户ID
    private Long userId;
    //隐藏分类ID
    private Integer hideId;
}

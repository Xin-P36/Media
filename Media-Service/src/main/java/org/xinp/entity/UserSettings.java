package org.xinp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

//用户爱好设置
@Data
public class UserSettings {
    //用户id
    @TableId(type = IdType.AUTO) // 主键自增
    private Long userId;
    //登录账号
    private String account;
    //密码
    private String password;
    //用户Token
    private String token;
    //用户昵称
    private String nickName;
    //用户头像
    private String avatar;
    //缩略图生成阈值
    private Integer thumbnailThreshold;
    //缩略图生成尺寸
    private Integer width; //生成原来X%的图片
    private Integer height; //视频截取第几秒作为封面
    //登录页背景
    private String loginBackground;
    //主页背景
    private String homeBackground;
}

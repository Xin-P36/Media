package org.xinp.pojo;

import lombok.Data;

/**
 * 登录用户返回
 */
@Data
public class ResultUserDTO {
    //用户id
    private Long userId;
    //登录账号
    private String account;
    //用户Token
    private String token;
    //用户昵称
    private String nickName;
    //用户头像
    private String avatar;
    //缩略图生成阈值
    private Integer thumbnailThreshold;
    //缩略图生成尺寸
    private Integer width;
    private Integer height;
    //登录页背景
    private String loginBackground;
    //主页背景
    private String homeBackground;
}
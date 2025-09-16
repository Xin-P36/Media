package org.xinp.pojo;

import lombok.Data;

/**
 * 用户更新信息
 */
@Data
public class UserUpdateDTO {
    // 账号是核心标识之一，通常不允许用户随意修改，
    // 但如果业务允许，可以放开。这里我们假设它可以被修改。
    private String account;
    
    private String nickName;
    
    // 密码字段需要特殊处理，空字符串表示不修改
    private String password;
    
    private String avatar;
    
    // 注意：数字类型如果前端不传，默认会是null
    private Integer thumbnailThreshold;
    private Integer width;
    private Integer height;
    
    private String loginBackground;
    private String homeBackground;
}
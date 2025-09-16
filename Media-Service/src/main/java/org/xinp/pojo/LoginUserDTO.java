package org.xinp.pojo;

import lombok.Data;

/**
 * 登录用户
 */
@Data
public class LoginUserDTO {
    private String account; // 账号
    private String password; // 密码
}

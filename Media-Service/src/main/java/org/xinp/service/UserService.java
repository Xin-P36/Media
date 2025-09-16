package org.xinp.service;

import org.xinp.pojo.*;

/**
 * 用户服务接口
 */
public interface UserService {
    /**
     * 登录
     * @param loginUser 登录用户信息
     * @return 登录结果
     */
    Result<ResultUserDTO> login(LoginUserDTO loginUser);

    /**
     * 登出
     * @return 结果
     */
    Result<String> logout();
    /**
     * 更新当前登录用户的信息。
     * @param updateDTO 包含要更新字段的DTO
     * @return 更新后的用户信息
     */
    Result<ResultUserDTO> updateUserInfo(UserUpdateDTO updateDTO);

}

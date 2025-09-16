package org.xinp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.xinp.pojo.LoginUserDTO;
import org.xinp.pojo.Result;
import org.xinp.pojo.ResultUserDTO;
import org.xinp.pojo.UserUpdateDTO;
import org.xinp.service.UserService;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 登录
     * @param loginUser 登录用户
     * @return 登录结果
     * 账号/密码Body
     */
    @PostMapping("/login")
    public Result<ResultUserDTO> login(@RequestBody LoginUserDTO loginUser) {
        return userService.login(loginUser);
    }

    /**
     * 登出
     * @return 成功
     */
    @DeleteMapping("/logout")
    public Result<String> logout() {
        return userService.logout();
    }
    /**
     * 更新当前用户信息
     * @param updateDTO 包含要更新字段的请求体
     * @return 更新后的用户信息
     * {
     *     "account":"XinP", 账号
     *     "nickName":"ROOT", 昵称
     *     "avatar":"/image.Useravater.jpg", 头像
     *     "thumbnailThreshold":1048576, 缩略图阈值（字节）
     *     "width":50, 生成的质量时原图的50%
     *     "height":2, 截取视频的第几帧作为封面
     *     "loginBackground":"/image.Useravater.jpg", 登录背景
     *     "homeBackground":"/image.Useravater.jpg" 主页背景
     * }
     */
    @PutMapping("/update")
    public Result<ResultUserDTO> updateUserInfo(@RequestBody UserUpdateDTO updateDTO) {
        return userService.updateUserInfo(updateDTO);
    }
}

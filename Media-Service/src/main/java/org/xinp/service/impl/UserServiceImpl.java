package org.xinp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.xinp.constant.Code;
import org.xinp.entity.UserSettings;
import org.xinp.mapper.UserSettingsMapper;
import org.xinp.pojo.LoginUserDTO;
import org.xinp.pojo.Result;
import org.xinp.pojo.ResultUserDTO;
import org.xinp.pojo.UserUpdateDTO;
import org.xinp.service.UserService;
import org.xinp.util.CurrentHolderUtils;
import org.xinp.util.JwtTokenUtils;

import java.util.HashMap;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserSettingsMapper userSettingsMapper;
    private final JwtTokenUtils jwtTokenUtils;

    /**
     * 登录
     * @param loginUser 登录用户信息
     * @return OK
     */
    @Override
    public Result<ResultUserDTO> login(LoginUserDTO loginUser) {
        if(loginUser == null){
            return Result.errorResult(400, "用户信息不能为空");
        }
        LambdaQueryWrapper<UserSettings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSettings::getAccount,loginUser.getAccount())
                .eq(UserSettings::getPassword,loginUser.getPassword());
        UserSettings user = userSettingsMapper.selectOne(wrapper);
        if (user != null){
            // 属性复制
            ResultUserDTO dto = new ResultUserDTO();
            BeanUtils.copyProperties(user,dto);
            //将用户ID存入Token中
            HashMap<String, Object> map = new HashMap<>();
            map.put("userId",user.getUserId());
            //生成Token
            String token = jwtTokenUtils.generateToken(user.getNickName(), map);
            dto.setToken(token);
            //将Token存入数据库
            UserSettings userSettings = new UserSettings();
            userSettings.setUserId(user.getUserId());
            userSettings.setToken(token);
            userSettingsMapper.updateById(userSettings);
            return Result.okResult(dto);
        }else {
            return Result.errorResult(500,"账号或密码错误");
        }
    }

    /**
     * 登出
     * @return 登出结果
     */
    @Override
    public Result<String> logout() {
        String userId = CurrentHolderUtils.getCurrentUser();
        UserSettings userSettings = new UserSettings();
        userSettings.setUserId(Long.parseLong(userId));
        userSettings.setToken("");
        userSettingsMapper.updateById(userSettings);
        return Result.okResult();
    }

    /**
     * 用户信息更新
     * @param updateDTO 包含要更新字段的DTO
     * @return OK
     */
    @Override
    public Result<ResultUserDTO> updateUserInfo(UserUpdateDTO updateDTO) {
        // 1. 获取当前登录用户的ID
        String currentUserIdStr = CurrentHolderUtils.getCurrentUser();
        if (currentUserIdStr == null) {
            // 这个情况理论上不会发生，因为拦截器已经保证了用户已登录
            return Result.errorResult(Code.NEED_LOGIN.getCode(), Code.NEED_LOGIN.getMsg());
        }
        Long currentUserId = Long.parseLong(currentUserIdStr);

        // 2. 从数据库查询出当前用户最新的实体信息
        UserSettings userToUpdate = userSettingsMapper.selectById(currentUserId);
        if (userToUpdate == null) {
            // 这也是一个异常情况，可能数据库记录被外部删除了
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "用户不存在或已被删除");
        }

        // (可选但推荐) 检查账号唯一性
        // 如果用户尝试修改账号，需要检查新账号是否已被其他用户占用
        if (StringUtils.isNotBlank(updateDTO.getAccount()) && !updateDTO.getAccount().equals(userToUpdate.getAccount())) {
            LambdaQueryWrapper<UserSettings> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserSettings::getAccount, updateDTO.getAccount());
            if (userSettingsMapper.selectCount(wrapper) > 0) {
                return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "该账号已被注册，请使用其他账号");
            }
            userToUpdate.setAccount(updateDTO.getAccount());
        }

        // 3. 动态更新字段
        // 使用 StringUtils.isNotBlank 判断字符串，避免空字符串 "" 更新数据库
        if (StringUtils.isNotBlank(updateDTO.getNickName())) {
            userToUpdate.setNickName(updateDTO.getNickName());
        }

        // 密码字段特殊处理：只有当传入非空字符串时才更新
        if (StringUtils.isNotBlank(updateDTO.getPassword())) {
            // 在实际项目中，密码应该被加密存储，例如使用BCrypt
            // userToUpdate.setPassword(passwordEncoder.encode(updateDTO.getPassword()));
            userToUpdate.setPassword(updateDTO.getPassword());
        }

        // 对于可为空的字符串字段，我们允许设置为空字符串
        if (updateDTO.getAvatar() != null) {
            userToUpdate.setAvatar(updateDTO.getAvatar());
        }
        if (updateDTO.getLoginBackground() != null) {
            userToUpdate.setLoginBackground(updateDTO.getLoginBackground());
        }
        if (updateDTO.getHomeBackground() != null) {
            userToUpdate.setHomeBackground(updateDTO.getHomeBackground());
        }

        // 对于数字类型，判断是否为null
        if (updateDTO.getThumbnailThreshold() != null) {
            userToUpdate.setThumbnailThreshold(updateDTO.getThumbnailThreshold());
        }
        if (updateDTO.getWidth() != null) {
            userToUpdate.setWidth(updateDTO.getWidth());
        }
        if (updateDTO.getHeight() != null) {
            userToUpdate.setHeight(updateDTO.getHeight());
        }

        // 4. 执行更新
        int updatedRows = userSettingsMapper.updateById(userToUpdate);

        if (updatedRows > 0) {
            // 更新成功后，将更新后的用户信息返回给前端
            ResultUserDTO resultDTO = new ResultUserDTO();
            // 注意：此时userToUpdate对象包含了所有最新的信息
            BeanUtils.copyProperties(userToUpdate, resultDTO);
            return Result.okResult(resultDTO);
        } else {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "更新用户信息失败，请稍后重试");
        }
    }
}

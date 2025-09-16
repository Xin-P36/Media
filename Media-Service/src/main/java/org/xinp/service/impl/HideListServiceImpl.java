package org.xinp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xinp.constant.Code;
import org.xinp.entity.HideList;
import org.xinp.entity.ToolList;
import org.xinp.mapper.HideListMapper;
import org.xinp.mapper.ToolListMapper;
import org.xinp.pojo.HideListRequestDTO;
import org.xinp.pojo.Result;
import org.xinp.service.HideListService;
import org.xinp.util.CurrentHolderUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HideListServiceImpl implements HideListService {
    
    private final HideListMapper hideListMapper;
    private final ToolListMapper toolListMapper; // 用于校验toolId是否存在

    @Override
    public Result<HideList> addHiddenTool(HideListRequestDTO request) {
        Long currentUserId = Long.parseLong(CurrentHolderUtils.getCurrentUser());
        Integer toolIdToHide = request.getHideId();

        // --- 校验 ---
        // 1. 校验要隐藏的分类是否存在
        ToolList tool = toolListMapper.selectById(toolIdToHide);
        if (tool == null) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "分类ID " + toolIdToHide + " 不存在，无法隐藏。");
        }
        
        // 2. 校验是否已重复隐藏
        LambdaQueryWrapper<HideList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HideList::getUserId, currentUserId)
               .eq(HideList::getHideId, toolIdToHide);
        if (hideListMapper.selectCount(wrapper) > 0) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "该分类已在你的隐藏列表中。");
        }

        // --- 执行新增 ---
        HideList newHide = new HideList();
        newHide.setUserId(currentUserId);
        newHide.setHideId(toolIdToHide);
        
        hideListMapper.insert(newHide);
        log.info("用户 {} 添加了隐藏分类，ID: {}", currentUserId, toolIdToHide);
        
        return Result.okResult(newHide);
    }

    @Override
    public Result<Void> removeHiddenTool(Long id) {
        Long currentUserId = Long.parseLong(CurrentHolderUtils.getCurrentUser());
        
        // --- 校验 ---
        // 确保要删除的记录存在，并且属于当前用户
        HideList hideRecord = hideListMapper.selectById(id);
        if (hideRecord == null) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "未找到该隐藏记录。");
        }
        if (!hideRecord.getUserId().equals(currentUserId)) {
            // 安全性校验：防止用户删除其他用户的记录
            return Result.errorResult(Code.NO_OPERATOR_AUTH.getCode(), "无权操作该记录。");
        }

        // --- 执行删除 ---
        hideListMapper.deleteById(id);
        log.info("用户 {} 删除了隐藏记录，ID: {}", currentUserId, id);
        
        return Result.okResult();
    }

    @Override
    public Result<List<HideList>> listHiddenTools() {
        Long currentUserId = Long.parseLong(CurrentHolderUtils.getCurrentUser());

        LambdaQueryWrapper<HideList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HideList::getUserId, currentUserId);
        
        List<HideList> hiddenList = hideListMapper.selectList(wrapper);
        
        return Result.okResult(hiddenList);
    }
}
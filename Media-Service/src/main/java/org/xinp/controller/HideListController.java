package org.xinp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xinp.entity.HideList;
import org.xinp.pojo.HideListRequestDTO;
import org.xinp.pojo.Result;
import org.xinp.service.HideListService;

import java.util.List;

@RestController
@RequestMapping("/api/hide-list")
@RequiredArgsConstructor
public class HideListController {

    private final HideListService hideListService;

    /**
     * (增) 添加一个隐藏分类
     * 入参：需要隐藏的分类ID
     *{"hideId": 3}
     */
    @PostMapping
    public Result<HideList> addHiddenTool(@RequestBody @Validated HideListRequestDTO request) {
        return hideListService.addHiddenTool(request);
    }

    /**
     * (删) 移除一个隐藏分类
     * @param id HideList表的主键ID
     * 入参：HideList表主键ID
     */
    @DeleteMapping("/{id}")
    public Result<Void> removeHiddenTool(@PathVariable Long id) {
        return hideListService.removeHiddenTool(id);
    }

    /**
     * (查) 获取当前用户的所有隐藏分类
     */
    @GetMapping
    public Result<List<HideList>> listHiddenTools() {
        return hideListService.listHiddenTools();
    }
}
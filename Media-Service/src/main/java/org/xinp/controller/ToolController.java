package org.xinp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.xinp.entity.ToolList;
import org.xinp.pojo.Result;
import org.xinp.pojo.ToolCreateRequestDTO;
import org.xinp.pojo.ToolTreeDTO;
import org.xinp.pojo.ToolUpdateRequestDTO;
import org.xinp.service.ToolListService;

import java.util.List;

@RestController
@RequestMapping("/api/tool")
@RequiredArgsConstructor
public class ToolController {
    private final ToolListService toolListService;

    /**
     * 创建一个新的媒体分类
     * @param createRequest 包含分类信息的请求体
     * @return 创建的ToolList实体
     * {
     *     "toolName":"图片",
     *     "path":"/school",
     *     "description":"图片",
     *     "sort":2,
     *     "parentId":"",
     *     "coverImageUrl":null
     * }
     * 修改顶层分类时，常规信息parentId为"0"，路径信息parentId为""。
     */
    @PostMapping("/create")
    public Result<ToolList> createTool(@RequestBody @Validated ToolCreateRequestDTO createRequest) {
        return toolListService.createTool(createRequest);
    }
    /**
     * 获取所有分类的树状结构
     * @return 树状分类列表
     * 返回:
     * {
     *     "code": 200,
     *     "message": "操作成功",
     *     "data": {
     *         "toolId": 1,
     *         "toolName": "图片",
     *         "path": "/school",
     *         "description": "图片",
     *         "sort": 2,
     *         "parentId": null,
     *         "coverImageUrl": null,
     *         "createTime": 1754911690055
     *     }
     * }
     */
    @GetMapping("/tree")
    public Result<List<ToolTreeDTO>> getToolTree() {
        return toolListService.getToolTree();
    }
    /**
     * 删除一个分类及其所有子内容
     * @param toolId 要删除的分类ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{toolId}")
    public Result<Void> deleteTool(@PathVariable Integer toolId) {
        return toolListService.deleteToolAndChildren(toolId);
    }
    /**
     * 更新分类信息
     * @param updateRequest 包含更新信息的请求体
     * @return 更新后的ToolList实体
     * {
     *     "toolId": 1,
     *     "toolName": "视",
     *     "path": "/image",
     *     "description": "",
     *     "sort": 20,
     *     "coverImageUrl": "",
     *     "parentId": ""
     * }
     * 修改顶层分类时，常规信息parentId为"0"，路径信息parentId为""。
     */
    @PutMapping("/update")
    public Result<ToolList> updateTool(@RequestBody @Validated ToolUpdateRequestDTO updateRequest) {
        return toolListService.updateTool(updateRequest);
    }
}

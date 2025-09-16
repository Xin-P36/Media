package org.xinp.service;

import org.xinp.entity.ToolList;
import org.xinp.pojo.Result;
import org.xinp.pojo.ToolCreateRequestDTO;
import org.xinp.pojo.ToolTreeDTO;
import org.xinp.pojo.ToolUpdateRequestDTO;

import java.util.List;

/**
 * 媒体分类服务接口
 */
public interface ToolListService {
    /**
     * 创建一个新的媒体分类。
     * 此操作会同时创建数据库记录和物理目录。
     * @param createRequest 包含分类信息的DTO
     * @return 成功时返回新创建的ToolList实体
     */
    Result<ToolList> createTool(ToolCreateRequestDTO createRequest);
    /**
     * 获取所有分类，并以嵌套树状结构返回。
     * @return 包含树状分类列表的Result对象
     */
    Result<List<ToolTreeDTO>> getToolTree();
    /**
     * 删除一个分类及其所有子分类和相关文件。
     * 这是一个级联删除操作，风险较高。
     * @param toolId 要删除的顶级分类ID
     * @return 操作结果
     */
    Result<Void> deleteToolAndChildren(Integer toolId);
    /**
     * 更新一个分类的信息，可能包括重命名、移动父分类等复杂操作。
     * @param updateRequest 包含要更新字段的DTO
     * @return 更新后的ToolList实体
     */
    Result<ToolList> updateTool(ToolUpdateRequestDTO updateRequest);
}

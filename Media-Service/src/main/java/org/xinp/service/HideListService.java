package org.xinp.service;

import org.xinp.entity.HideList;
import org.xinp.pojo.HideListRequestDTO;
import org.xinp.pojo.Result;

import java.util.List;

public interface HideListService {

    /**
     * 为当前用户添加一条隐藏分类记录。
     * @param request 包含要隐藏的分类ID (toolId)
     * @return 新创建的HideList记录
     */
    Result<HideList> addHiddenTool(HideListRequestDTO request);

    /**
     * 为当前用户删除一条隐藏分类记录。
     * @param id HideList表的主键ID
     * @return 操作结果
     */
    Result<Void> removeHiddenTool(Long id);

    /**
     * 获取当前用户的所有隐藏分类记录。
     * @return HideList记录列表
     */
    Result<List<HideList>> listHiddenTools();
    
    // "改"操作通常不直接对HideList表进行，因为它的字段很少。
    // 如果非要提供，也是接收一个HideList的主键id和新的toolId。
    // 这里我们先不提供“改”，因为业务上“先删后增”更常见。
    // 如果确实需要，可以再添加。
}
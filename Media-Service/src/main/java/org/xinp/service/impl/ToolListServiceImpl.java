package org.xinp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xinp.constant.Code;
import org.xinp.entity.HideList;
import org.xinp.entity.MediaFiles;
import org.xinp.entity.ToolList;
import org.xinp.exception.FileOperationException;
import org.xinp.mapper.HideListMapper;
import org.xinp.mapper.MediaFilesMapper;
import org.xinp.mapper.ToolListMapper;
import org.xinp.pojo.*;
import org.xinp.service.ToolListService;
import org.xinp.util.CurrentHolderUtils;
import org.xinp.util.FileManagementUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolListServiceImpl implements ToolListService {
    private final ToolListMapper toolListMapper;
    private final FileManagementUtil fileManagementUtil;
    private final MediaFilesMapper mediaFilesMapper;
    private final HideListMapper hideListMapper;

    /**
     * 创建分类
     *
     * @param createRequest 包含分类信息的DTO
     * @return 创建成功的分类信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 确保所有异常都触发回滚
    public Result<ToolList> createTool(ToolCreateRequestDTO createRequest) {
        // --- 1. 数据库层面的校验 ---
        // a. 检查路径是否已在数据库中存在，防止重复
        LambdaQueryWrapper<ToolList> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ToolList::getPath, createRequest.getPath());
        if (toolListMapper.selectCount(queryWrapper) > 0) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "路径 '" + createRequest.getPath() + "' 已存在，请使用其他路径。");
        }

        // b. 如果提供了parentId，校验父级分类是否存在
        if (createRequest.getParentId() != null) {
            ToolList parentTool = toolListMapper.selectById(createRequest.getParentId());
            if (parentTool == null) {
                return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "指定的父级分类ID " + createRequest.getParentId() + " 不存在。");
            }
            // (可选) 严格校验路径层级关系
            if (!createRequest.getPath().startsWith(parentTool.getPath() + "/")) {
                return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "路径层级错误，子分类路径必须以父分类路径为前缀。");
            }
        }

        // --- 2. 文件系统操作 ---
        String relativePath = createRequest.getPath().substring(1); // 去掉开头的'/'
        try {
            log.info("尝试创建物理目录: {}", relativePath);
            fileManagementUtil.createDirectory(relativePath);
        } catch (FileOperationException e) {
            log.error("创建物理目录失败: {}", relativePath, e);
            // 将文件系统异常转换为对用户更友好的业务异常
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "创建目录失败: " + e.getMessage());
        }

        // --- 3. 创建数据库记录 ---
        ToolList newTool = new ToolList();
        BeanUtils.copyProperties(createRequest, newTool);
        newTool.setCreateTime(System.currentTimeMillis());

        try {
            toolListMapper.insert(newTool);
            log.info("成功创建分类 '{}'，ID: {}", newTool.getToolName(), newTool.getToolId());
            // 插入成功后，MyBatis-Plus会自动将自增的ID回填到newTool对象中
            return Result.okResult(newTool);
        } catch (Exception dbException) {
            // *** 关键：补偿操作 ***
            // 如果数据库插入失败，我们需要尝试删除刚刚创建的物理目录，以保持一致性。
            log.error("数据库插入分类记录失败，将尝试回滚物理目录创建。", dbException);
            try {
                fileManagementUtil.deleteDirectory(relativePath);
                log.info("成功回滚（删除）物理目录: {}", relativePath);
            } catch (FileOperationException rollbackException) {
                // 如果回滚也失败，记录一个严重错误，需要手动干预
                log.error("!!! 严重错误：数据库插入失败后，回滚物理目录 {} 也失败！", relativePath, rollbackException);
            }
            // 向上层抛出异常，触发@Transactional的回滚
            throw new RuntimeException("数据库操作失败", dbException);
        }
    }

    /**
     * 获取分类树
     *
     * @return 分类树
     */
    @Override
    public Result<List<ToolTreeDTO>> getToolTree() {
        // --- a. 获取当前用户需要隐藏的所有分类ID (包括子孙) ---
        Set<Integer> hiddenToolIds = getHiddenToolIdsForCurrentUser();

        // 1. 从数据库中一次性获取所有分类列表
        List<ToolList> allTools = toolListMapper.selectList(null);

        if (allTools == null || allTools.isEmpty()) {
            return Result.okResult(null); // 按要求，没数据返回null
        }

        // --- b. 过滤掉所有在黑名单中的分类 ---
        List<ToolList> visibleTools = allTools.stream()
                .filter(tool -> !hiddenToolIds.contains(tool.getToolId()))
                .collect(Collectors.toList());

        if (visibleTools.isEmpty()) {
            return Result.okResult(null); // 过滤后没数据也返回null
        }

        // 2. 将可见的列表转换为DTO列表，并使用toolId作为key存入Map
        Map<Integer, ToolTreeDTO> toolMap = visibleTools.stream().map(tool -> {
            ToolTreeDTO dto = new ToolTreeDTO();
            BeanUtils.copyProperties(tool, dto);
            return dto;
        }).collect(Collectors.toMap(ToolTreeDTO::getToolId, dto -> dto));

        // 3. 构建树形结构
        List<ToolTreeDTO> rootNodes = new ArrayList<>();

        for (ToolTreeDTO node : toolMap.values()) {
            Integer parentId = node.getParentId();

            // 如果父节点存在于可见的Map中
            if (parentId != null && toolMap.containsKey(parentId)) {
                ToolTreeDTO parentNode = toolMap.get(parentId);
                if (parentNode.getChildren() == null) {
                    parentNode.setChildren(new ArrayList<>());
                }
                parentNode.getChildren().add(node);
            } else {
                // 如果父节点被隐藏了，或者它本身就是根节点，则将它作为根节点
                rootNodes.add(node);
            }
        }

        // 4. 对结果进行排序
        sortTree(rootNodes);

        return Result.okResult(rootNodes.isEmpty() ? null : rootNodes);
    }

    /**
     * 新增的辅助方法：获取当前用户所有需要隐藏的分类ID集合。
     * @return 包含所有直接隐藏和间接隐藏的分类ID的Set
     */
    private Set<Integer> getHiddenToolIdsForCurrentUser() {
        String currentUserIdStr = CurrentHolderUtils.getCurrentUser();
        // 如果未登录，不隐藏任何内容
        if (currentUserIdStr == null) {
            return Collections.emptySet();
        }
        Long currentUserId = Long.parseLong(currentUserIdStr);

        // a. 获取用户直接设置的隐藏列表
        LambdaQueryWrapper<HideList> hideQuery = new LambdaQueryWrapper<>();
        hideQuery.eq(HideList::getUserId, currentUserId);
        List<HideList> directHides = hideListMapper.selectList(hideQuery);

        if (directHides.isEmpty()) {
            return Collections.emptySet();
        }

        // b. 获取所有分类，用于在内存中查找子孙
        List<ToolList> allTools = toolListMapper.selectList(null);

        // c. 计算完整的隐藏ID集合
        Set<Integer> fullHiddenIds = new HashSet<>();
        for (HideList hide : directHides) {
            findChildrenIds(hide.getHideId(), allTools, fullHiddenIds); // 递归查找子孙
            fullHiddenIds.add(hide.getHideId()); // 添加自身
        }

        return fullHiddenIds;
    }

    // 之前已有的排序方法
    private void sortTree(List<ToolTreeDTO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(ToolList::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ToolList::getToolId));
        for (ToolTreeDTO node : nodes) {
            sortTree(node.getChildren());
        }
    }

    // 之前已有的递归查找子孙ID的方法，注意这里第三个参数类型改为了Set
    private void findChildrenIds(Integer parentId, List<ToolList> allTools, Set<Integer> childrenIds) {
        List<ToolList> directChildren = allTools.stream()
                .filter(tool -> parentId.equals(tool.getParentId()))
                .collect(Collectors.toList());

        for (ToolList child : directChildren) {
            if (childrenIds.add(child.getToolId())) { // 使用Set的add方法避免重复添加和无限递归
                findChildrenIds(child.getToolId(), allTools, childrenIds);
            }
        }
    }

    /**
     * 删除分类及其子分类
     *
     * @param toolId 要删除的顶级分类ID
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteToolAndChildren(Integer toolId) {
        // --- 1. 查找所有待删除的分类 (包括自身和所有子孙) ---
        // a. 从数据库获取所有分类，以便在内存中构建树形关系
        List<ToolList> allTools = toolListMapper.selectList(null);
        if (allTools.isEmpty()) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "系统中没有任何分类。");
        }

        // b. 递归查找所有子孙分类ID
        List<Integer> idsToDelete = new ArrayList<>();
        findChildrenIds(toolId, allTools, idsToDelete);

        // c. 将自身也加入待删除列表
        idsToDelete.add(toolId);
        log.info("准备删除的分类ID列表: {}", idsToDelete);

        // --- 2. 查找这些分类下的所有文件 ---
        LambdaQueryWrapper<MediaFiles> filesQuery = new LambdaQueryWrapper<>();
        filesQuery.in(MediaFiles::getToolId, idsToDelete);
        List<MediaFiles> filesToDelete = mediaFilesMapper.selectList(filesQuery);
        List<Long> fileIdsToDelete = filesToDelete.stream()
                .map(MediaFiles::getFileId).collect(Collectors.toList());

        // --- 3. 数据库删除操作 ---
        // a. 删除所有相关的文件记录
        if (!fileIdsToDelete.isEmpty()) {
            int deletedFilesCount = mediaFilesMapper.deleteBatchIds(fileIdsToDelete);
            log.info("从数据库中删除了 {} 条文件记录。", deletedFilesCount);
        }

        // b. 删除所有相关的分类记录
        int deletedToolsCount = toolListMapper.deleteBatchIds(idsToDelete);
        log.info("从数据库中删除了 {} 条分类记录。", deletedToolsCount);
        if (deletedToolsCount == 0) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "未找到ID为 " + toolId + " 的分类，无法删除。");
        }

        // --- 4. 物理文件和目录删除操作 ---
        // *** 将物理删除放在数据库操作成功之后 ***
        // a. 删除物理文件
        for (MediaFiles file : filesToDelete) {
            try {
                // 同时删除文件本身和可能的缩略图
                fileManagementUtil.deleteFile(file.getFilePath());
                if (StringUtils.isNotBlank(file.getThumbnail())) {
                    fileManagementUtil.deleteFile(file.getThumbnail());
                }
            } catch (Exception e) {
                // 如果单个文件删除失败，我们记录日志并继续，但事务会回滚
                log.error("删除物理文件 {} 失败，操作将回滚。", file.getFilePath(), e);
                // 抛出异常以触发事务回滚
                throw new RuntimeException("物理文件删除失败: " + file.getFilePath(), e);
            }
        }

        // b. 删除物理目录 (从子到父的顺序是最好的，但FileManagementUtil的递归删除已处理)
        // 我们只需要删除最顶层的那个目录即可，它的子目录和文件都会被级联删除
        ToolList topLevelToolToDelete = allTools.stream()
                .filter(t -> t.getToolId().equals(toolId)).findFirst().orElse(null);

        if (topLevelToolToDelete != null && StringUtils.isNotBlank(topLevelToolToDelete.getPath())) {
            try {
                String relativePath = topLevelToolToDelete.getPath().substring(1); // 去掉开头的'/'
                fileManagementUtil.deleteDirectory(relativePath);
                log.info("成功删除物理目录及其所有内容: {}", relativePath);
            } catch (Exception e) {
                log.error("删除物理目录 {} 失败，操作将回滚。", topLevelToolToDelete.getPath(), e);
                throw new RuntimeException("物理目录删除失败: " + topLevelToolToDelete.getPath(), e);
            }
        }

        return Result.okResult();
    }

    /**
     * 辅助方法：递归查找一个节点的所有子孙ID
     *
     * @param parentId    当前要查找的父ID
     * @param allTools    所有分类的列表
     * @param childrenIds 结果列表，用于收集所有子孙ID
     */
    private void findChildrenIds(Integer parentId, List<ToolList> allTools, List<Integer> childrenIds) {
        // 找到所有直接子节点
        List<ToolList> directChildren = allTools.stream()
                .filter(tool -> parentId.equals(tool.getParentId()))
                .toList();

        // 对每个直接子节点，递归查找它们的子节点
        for (ToolList child : directChildren) {
            childrenIds.add(child.getToolId());
            findChildrenIds(child.getToolId(), allTools, childrenIds);
        }
    }

    /**
     * 更新分类
     *
     * @param request 包含更新信息的DTO
     * @return 更新结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ToolList> updateTool(ToolUpdateRequestDTO request) {
        // --- 1. 基础校验 ---
        ToolList toolToUpdate = toolListMapper.selectById(request.getToolId());
        if (toolToUpdate == null) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "未找到ID为 " + request.getToolId() + " 的分类。");
        }

        // --- 2. 准备更新数据 ---
        // 将请求中的值赋给实体，为后续判断和更新做准备
        // 这里只是在内存中修改，尚未持久化
        boolean hasSimpleChanges = false;
        if (request.getToolName() != null && !request.getToolName().equals(toolToUpdate.getToolName())) {
            toolToUpdate.setToolName(request.getToolName());
            hasSimpleChanges = true;
        }
        if (request.getDescription() != null && !request.getDescription().equals(toolToUpdate.getDescription())) {
            toolToUpdate.setDescription(request.getDescription());
            hasSimpleChanges = true;
        }
        if (request.getSort() != null && !request.getSort().equals(toolToUpdate.getSort())) {
            toolToUpdate.setSort(request.getSort());
            hasSimpleChanges = true;
        }
        if (request.getCoverImageUrl() != null && !request.getCoverImageUrl().equals(toolToUpdate.getCoverImageUrl())) {
            toolToUpdate.setCoverImageUrl(request.getCoverImageUrl());
            hasSimpleChanges = true;
        }

        // --- 3. 关键修正：判断核心字段（路径和父ID）是否真的发生了变化 ---
        String oldPath = toolToUpdate.getPath();
        String newPath = request.getPath();
        Integer oldParentId = toolToUpdate.getParentId();
        Integer newParentId = request.getParentId();

        // pathChanged: 仅当请求中提供了新路径，且它与旧路径不同时，才为 true
        boolean pathChanged = newPath != null && !newPath.equals(oldPath);

        // parentIdChanged: 仅当请求中提供了新父ID，且它与旧父ID不同时，才为 true
        // 注意：这里要正确处理 null 的情况。
        boolean parentIdChanged = (request.getParentId() != null && !request.getParentId().equals(oldParentId)) ||
                (oldParentId != null && request.getParentId() == null && request.getPath() != null); // 从有父级变到根目录

        // --- 4. 逻辑分流 ---
        // 如果路径和父ID都没有发生变化
        if (!pathChanged && !parentIdChanged) {
            // 如果只有简单的信息变更
            if (hasSimpleChanges) {
                log.info("执行简单信息更新，分类ID: {}", request.getToolId());
                toolListMapper.updateById(toolToUpdate);
            } else {
                log.info("无任何变更，操作跳过，分类ID: {}", request.getToolId());
            }
            // 无论是否有简单变更，都返回最新的（可能已更新的）实体信息
            return Result.okResult(toolToUpdate);
        }

        // --- 5. 执行路径或父ID变更的复杂逻辑 ---
        log.info("检测到路径或父ID变更，执行复杂更新流程，分类ID: {}", request.getToolId());

        // a. 校验新的路径是否为空
        if (newPath == null) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "当父分类ID变更或路径变更时，必须提供新的'path'字段。");
        }

        // b. 校验新路径唯一性
        LambdaQueryWrapper<ToolList> pathCheckWrapper = new LambdaQueryWrapper<>();
        pathCheckWrapper.eq(ToolList::getPath, newPath)
                .ne(ToolList::getToolId, request.getToolId()); // 排除自身
        if (toolListMapper.selectCount(pathCheckWrapper) > 0) {
            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "路径 '" + newPath + "' 已被占用。");
        }

        // c. 更新实体中的路径和父ID
        toolToUpdate.setPath(newPath);
        // 如果请求中没有明确提供parentId, 我们保持原样，除非路径变化暗示了它需要变
        if(request.getParentId() != null || (oldParentId != null && request.getPath() != null) ){
            toolToUpdate.setParentId(newParentId);
        }

        // d. 校验parentId和path的匹配关系 (现在toolToUpdate对象已更新，可以直接用它来校验)
        if (toolToUpdate.getParentId() != null) {
            ToolList newParentTool = toolListMapper.selectById(toolToUpdate.getParentId());
            if (newParentTool == null) {
                throw new RuntimeException("指定的新父级分类ID " + toolToUpdate.getParentId() + " 不存在。");
            }
            if (!toolToUpdate.getPath().startsWith(newParentTool.getPath() + "/")) {
                throw new RuntimeException("路径层级错误：新路径 '" + toolToUpdate.getPath() + "' 与新父级分类 '" + newParentTool.getPath() + "' 不匹配。");
            }
        } else { // 根目录
            if (Paths.get(toolToUpdate.getPath().substring(1)).getNameCount() > 1) {
                throw new RuntimeException("移动到根目录时，路径层级不应超过一级。");
            }
        }

        // --- 6. 确定操作类型并执行 ---
        // 这里的逻辑可以简化，因为moveDirectory都能处理
        // 获取所有子孙分类和文件...
        List<ToolList> allTools = toolListMapper.selectList(null);
        List<Integer> allChildrenIds = new ArrayList<>();
        findChildrenIds(request.getToolId(), allTools, allChildrenIds);

        // 更新数据库记录 (当前分类，子孙分类，相关文件)
        toolListMapper.updateById(toolToUpdate);

        // b. 批量更新所有子孙分类的路径
        if (!allChildrenIds.isEmpty()) {
            List<ToolList> childrenToUpdate = allTools.stream()
                    .filter(t -> allChildrenIds.contains(t.getToolId()))
                    .collect(Collectors.toList());

            for (ToolList child : childrenToUpdate) {
                String newChildPath = child.getPath().replaceFirst(oldPath, newPath);
                child.setPath(newChildPath);
                toolListMapper.updateById(child);
            }
        }

        // c. 批量更新所有相关文件的路径
        allChildrenIds.add(request.getToolId());
        LambdaUpdateWrapper<MediaFiles> fileUpdateWrapper = new LambdaUpdateWrapper<>();
        fileUpdateWrapper.in(MediaFiles::getToolId, allChildrenIds)
                .setSql("file_path = REPLACE(file_path, {0}, {1})", oldPath.substring(1), newPath.substring(1));
        mediaFilesMapper.update(null, fileUpdateWrapper);

        // --- 7. 执行文件系统操作 ---
        // 执行文件系统操作
        try {
            fileManagementUtil.moveDirectory(oldPath.substring(1), newPath.substring(1));
        } catch (Exception e) {
            log.error("移动/重命名目录失败，但数据库已更新。源: {}, 目标: {}", oldPath, newPath, e);
            throw new RuntimeException("文件系统操作失败：从 " + oldPath + " 到 " + newPath + " 移动失败，请联系管理员。数据库更改将回滚。");
        }

        return Result.okResult(toolToUpdate);
    }
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Result<ToolList> updateTool(ToolUpdateRequestDTO request) {
//        // --- 1. 基础校验 ---
//        ToolList toolToUpdate = toolListMapper.selectById(request.getToolId());
//        if (toolToUpdate == null) {
//            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "未找到ID为 " + request.getToolId() + " 的分类。");
//        }
//
//        // --- 2. 判断是否需要执行复杂的文件系统操作 ---
//        String oldPath = toolToUpdate.getPath();
//        Integer oldParentId = toolToUpdate.getParentId();
//
//        // 只有当请求中明确提供了 path 或 parentId，且与旧值不同时，才认为需要移动或重命名。
//        boolean needsFileSystemOperation =
//                (request.getPath() != null && !request.getPath().equals(oldPath)) ||
//                        (request.getParentId() != null && !request.getParentId().equals(oldParentId)) ||
//                        (request.getParentId() == null && oldParentId != null); // 从有父级变到根目录
//
//        // --- 3. 根据判断结果分流 ---
//        // 逻辑A：如果不需要文件系统操作（简单信息更新）
//        if (!needsFileSystemOperation) {
//            log.info("执行简单信息更新，分类ID: {}", request.getToolId());
//            boolean hasChanges = false;
//            // 更新非核心字段
//            if (request.getToolName() != null) {
//                toolToUpdate.setToolName(request.getToolName());
//                hasChanges = true;
//            }
//            if (request.getDescription() != null) {
//                toolToUpdate.setDescription(request.getDescription());
//                hasChanges = true;
//            }
//            if (request.getSort() != null) {
//                toolToUpdate.setSort(request.getSort());
//                hasChanges = true;
//            }
//            if (request.getCoverImageUrl() != null) {
//                toolToUpdate.setCoverImageUrl(request.getCoverImageUrl());
//                hasChanges = true;
//            }
//
//            if (hasChanges) {
//                toolListMapper.updateById(toolToUpdate);
//            } else {
//                log.info("无任何变更，操作跳过。");
//            }
//            return Result.okResult(toolToUpdate);
//        }
//
//        // 逻辑B：需要执行文件系统操作的复杂流程
//        log.info("检测到路径或父ID变更，执行复杂更新流程，分类ID: {}", request.getToolId());
//
//        // --- 4. 复杂流程的校验和执行 ---
//        // a. 准备新旧路径和ID
//        String newPath = request.getPath() != null ? request.getPath() : oldPath;
//        Integer newParentId = request.getParentId() != null ? request.getParentId() : oldParentId;
//        // 如果前端传了parentId: "" 会变null, 如果原来是0，需要统一处理
//        if (request.getParentId() == null && request.getPath() != null) {
//            newParentId = null;
//        }
//
//        // b. 校验新路径唯一性
//        LambdaQueryWrapper<ToolList> pathCheckWrapper = new LambdaQueryWrapper<>();
//        pathCheckWrapper.eq(ToolList::getPath, newPath)
//                .ne(ToolList::getToolId, request.getToolId());
//        if (toolListMapper.selectCount(pathCheckWrapper) > 0) {
//            return Result.errorResult(Code.SYSTEM_ERROR.getCode(), "路径 '" + newPath + "' 已被占用。");
//        }
//
//        // c. 校验父子关系
//        if (newParentId != null) {
//            ToolList newParentTool = toolListMapper.selectById(newParentId);
//            if (newParentTool == null) {
//                throw new RuntimeException("指定的新父级分类ID " + newParentId + " 不存在。");
//            }
//            if (!newPath.startsWith(newParentTool.getPath() + "/")) {
//                throw new RuntimeException("路径层级错误：新路径 '" + newPath + "' 与新父级分类 '" + newParentTool.getPath() + "' 不匹配。");
//            }
//        } else { // 根目录
//            if (Paths.get(newPath.substring(1)).getNameCount() > 1) {
//                throw new RuntimeException("移动到根目录时，路径层级不应超过一级。");
//            }
//        }
//
//        // --- 5. 执行数据库和文件系统更新 ---
//
//        // 更新当前实体
//        toolToUpdate.setPath(newPath);
//        toolToUpdate.setParentId(newParentId);
//        // 也更新其他简单字段
//        if (request.getToolName() != null) toolToUpdate.setToolName(request.getToolName());
//        if (request.getDescription() != null) toolToUpdate.setDescription(request.getDescription());
//        if (request.getSort() != null) toolToUpdate.setSort(request.getSort());
//        if (request.getCoverImageUrl() != null) toolToUpdate.setCoverImageUrl(request.getCoverImageUrl());
//
//        // ... (后面更新子孙分类、文件记录，以及移动目录的逻辑保持不变)
//        // 获取所有子孙分类和文件...
//        List<ToolList> allTools = toolListMapper.selectList(null);
//        List<Integer> allChildrenIds = new ArrayList<>();
//        findChildrenIds(request.getToolId(), allTools, allChildrenIds);
//
//        // 更新数据库记录 (当前分类，子孙分类，相关文件)
//        toolListMapper.updateById(toolToUpdate);
//
//        if (!allChildrenIds.isEmpty()) {
//            List<ToolList> childrenToUpdate = allTools.stream()
//                    .filter(t -> allChildrenIds.contains(t.getToolId()))
//                    .collect(Collectors.toList());
//
//            for (ToolList child : childrenToUpdate) {
//                String newChildPath = child.getPath().replaceFirst(oldPath, newPath);
//                child.setPath(newChildPath);
//                toolListMapper.updateById(child);
//            }
//        }
//
//        allChildrenIds.add(request.getToolId());
//        LambdaUpdateWrapper<MediaFiles> fileUpdateWrapper = new LambdaUpdateWrapper<>();
//        fileUpdateWrapper.in(MediaFiles::getToolId, allChildrenIds)
//                .setSql("file_path = REPLACE(file_path, {0}, {1})", oldPath.substring(1), newPath.substring(1));
//        mediaFilesMapper.update(null, fileUpdateWrapper);
//
//        // 执行文件系统操作
//        try {
//            fileManagementUtil.moveDirectory(oldPath.substring(1), newPath.substring(1));
//        } catch (Exception e) {
//            log.error("移动/重命名目录失败，但数据库已更新。源: {}, 目标: {}", oldPath, newPath, e);
//            throw new RuntimeException("文件系统操作失败：从 " + oldPath + " 到 " + newPath + " 移动失败，请联系管理员。数据库更改将回滚。");
//        }
//
//        return Result.okResult(toolToUpdate);
//    }
}

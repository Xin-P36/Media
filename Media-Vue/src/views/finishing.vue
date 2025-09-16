<script setup>
import { ref, onMounted } from "vue";
import {
  ArrowLeftBold,
  Plus,
  PictureFilled,
  Setting,
} from "@element-plus/icons-vue";
import { getToolCategoryTree } from "@/utils/toolCategory";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed } from "vue";
import request from "@/utils/axiosRequest";
import { useRouter } from "vue-router";

// 分类数据
let directory = ref([]);

// 添加状态管理
let currentMenu = ref("main"); // 'main' 表示主菜单, 'sub' 表示子菜单
let currentSubMenu = ref(null); // 当前显示的子菜单项
let menuPath = ref([]); // 菜单路径数组，用于支持多级菜单导航

// 获取路由实例
const router = useRouter();

// 添加分类表单相关数据
const dialogVisible = ref(false); // 控制对话框显示
const formLoading = ref(false); // 表单提交loading状态
const formRef = ref(); // 表单引用

// 表单数据
const formData = ref({
  toolId: "", // 分类ID（仅在编辑时使用）
  toolName: "", // 分类名称
  path: "", // 分类路径
  description: "", // 分类描述
  sort: 90, // 分类优先级
  parentId: "", // 父分类ID
  coverImageUrl: null, // 分类封面
});

// 是否为编辑模式
const isEditMode = ref(false);

// 当前添加分类的上下文信息
const addContext = ref({
  isSubCategory: false, // 是否为子分类
  parentPath: "", // 父级路径
  parentId: "", // 父级ID
});

// 当前编辑的分类
const currentEditingCategory = ref(null);

// 获取分类及其所有子分类的ID
const getCategoryAndChildrenIds = (categoryId) => {
  const ids = [categoryId];
  const findChildren = (items) => {
    if (items && Array.isArray(items)) {
      items.forEach((item) => {
        ids.push(item.toolId);
        if (item.lists && item.lists.length > 0) {
          findChildren(item.lists);
        }
      });
    }
  };

  // 查找指定分类并获取其所有子分类
  const findCategory = (items) => {
    if (items && Array.isArray(items)) {
      for (const item of items) {
        if (item.toolId === categoryId) {
          findChildren(item.lists);
          return;
        }
        if (item.lists && item.lists.length > 0) {
          findCategory(item.lists);
        }
      }
    }
  };

  findCategory(directory.value);
  return ids;
};

// 父类选项列表
const parentOptions = computed(() => {
  // 将目录数据转换为下拉选项格式
  const allOptions = [];

  // 递归函数，将所有分类添加到选项列表中
  const addOptions = (items) => {
    if (items && Array.isArray(items)) {
      items.forEach((item) => {
        allOptions.push({
          value: item.toolId,
          label: `${item.name} (${item.path})`,
          path: item.path,
        });
        if (item.lists && item.lists.length > 0) {
          addOptions(item.lists);
        }
      });
    }
  };

  addOptions(directory.value);

  // 在编辑模式下，排除自己及其所有子分类
  if (isEditMode.value && formData.value.toolId) {
    const excludedIds = getCategoryAndChildrenIds(formData.value.toolId);
    return allOptions.filter((item) => !excludedIds.includes(item.value));
  }

  return allOptions;
});

// 表单验证规则
const formRules = {
  toolName: [
    { required: true, message: "请输入分类名称", trigger: "blur" },
    { min: 1, max: 20, message: "长度在 1 到 20 个字符", trigger: "blur" },
  ],
  path: [{ required: true, message: "请输入分类路径", trigger: "blur" }],
};

// 关闭对话框
const handleDialogClose = () => {
  dialogVisible.value = false;
  // 重置表单数据
  formData.value = {
    toolName: "",
    path: "",
    description: "",
    sort: 90,
    parentId: "",
    coverImageUrl: null,
  };
  // 重置表单验证
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

// 处理父类ID变化
const handleParentIdChange = (val) => {
  if (val) {
    // 查找选中的父类项（在完整的分类树中查找）
    const findItem = (items) => {
      if (items && Array.isArray(items)) {
        for (const item of items) {
          if (item.toolId === val) {
            return item;
          }
          if (item.lists && item.lists.length > 0) {
            const found = findItem(item.lists);
            if (found) {
              return found;
            }
          }
        }
      }
      return null;
    };

    const selectedItem = findItem(directory.value);
    if (selectedItem) {
      if (isEditMode.value) {
        // 编辑模式下，路径 = 父类路径 + 原来分类路径的最后一部分
        // 获取原来分类路径的最后一部分
        const pathParts = formData.value.path.split("/");
        const lastPathPart =
          pathParts[pathParts.length - 1] ||
          pathParts[pathParts.length - 2] ||
          "";
        // 设置路径为父类路径 + "/" + 原来分类路径的最后一部分
        formData.value.path = selectedItem.path + "/" + lastPathPart;
      } else {
        // 添加模式下，设置预填路径为选中项的路径 + "/"
        formData.value.path = selectedItem.path + "/";
      }
    }
  } else {
    // 如果没有选择父类，重置路径为 "/" 或保持当前路径（编辑模式下）
    if (!isEditMode.value) {
      formData.value.path = "/";
    }
  }
};

// 处理添加分类
const handleAddCategory = (isSubCategory = false, parentItem = null) => {
  // 设置为添加模式
  isEditMode.value = false;

  // 设置上下文信息
  addContext.value.isSubCategory = isSubCategory;

  if (isSubCategory && parentItem) {
    // 子分类情况
    addContext.value.parentPath = parentItem.path;
    addContext.value.parentId = parentItem.toolId;
    // 设置表单默认路径
    formData.value.path = parentItem.path + "/";
    formData.value.parentId = parentItem.toolId;
  } else {
    // 顶级分类情况（根目录添加）
    addContext.value.parentPath = "";
    addContext.value.parentId = "";
    // 设置表单默认路径
    formData.value.path = "/";
    formData.value.parentId = "";
  }

  // 清空其他表单字段
  formData.value.toolId = "";
  formData.value.toolName = "";
  formData.value.description = "";
  formData.value.sort = 90;
  formData.value.coverImageUrl = null;

  // 显示对话框
  dialogVisible.value = true;
};

// 处理编辑分类
const handleEditCategory = (item) => {
  // 设置为编辑模式
  isEditMode.value = true;

  // 填充表单数据
  formData.value.toolId = item.toolId;
  formData.value.toolName = item.name;
  formData.value.description = item.description || "";
  formData.value.sort = item.sort || 90;
  formData.value.parentId = item.parentId || "";
  formData.value.coverImageUrl = item.coverImageUrl || null;

  // 特殊处理路径预填值
  // 取原来分类路径的最后一部分
  const pathParts = item.path.split("/");
  const lastPathPart =
    pathParts[pathParts.length - 1] || pathParts[pathParts.length - 2] || "";

  // 查找父类路径
  let parentPath = "";
  if (item.parentId) {
    const parentItem = directory.value.find(
      (dir) => dir.toolId === item.parentId
    );
    if (parentItem) {
      parentPath = parentItem.path;
    }
  }

  // 设置路径预填值：父类路径 + "/" + 原来分类路径的最后一部分
  formData.value.path =
    parentPath && lastPathPart ? parentPath + "/" + lastPathPart : item.path;

  // 设置上下文信息
  addContext.value.isSubCategory = false; // 编辑时不限制

  // 显示对话框
  dialogVisible.value = true;

  // 设置当前编辑的分类
  currentEditingCategory.value = item;
};

// 创建/更新分类
const handleCreateCategory = async () => {
  // 表单验证
  if (!formRef.value) return;

  await formRef.value.validate(async (valid) => {
    if (valid) {
      formLoading.value = true;

      try {
        // 检查是否同时修改了根分类的路径和其他字段
        if (isEditMode.value && currentEditingCategory.value) {
          // 如果是根分类（没有 parentId 或 parentId 为空）
          if (
            !currentEditingCategory.value.parentId ||
            currentEditingCategory.value.parentId === ""
          ) {
            // 检查是否同时修改了路径和其他字段
            const isPathChanged =
              formData.value.path !== currentEditingCategory.value.path;
            const isNameChanged =
              formData.value.toolName !== currentEditingCategory.value.name;
            const isDescriptionChanged =
              formData.value.description !==
              (currentEditingCategory.value.description || "");
            const isSortChanged =
              formData.value.sort !== (currentEditingCategory.value.sort || 90);

            // 如果同时修改了路径和其他字段，显示提示
            if (
              isPathChanged &&
              (isNameChanged || isDescriptionChanged || isSortChanged)
            ) {
              ElMessage.warning("根路径与常规信息不能同时修改");
              formLoading.value = false;
              return;
            }
          }
        }

        // 构造请求参数
        // 构造请求参数
        const params = {
          toolId: formData.value.toolId, // 仅在编辑时使用
          toolName: formData.value.toolName,
          path: formData.value.path,
          description: formData.value.description || "",
          sort: formData.value.sort,
          parentId: formData.value.parentId,
          coverImageUrl: formData.value.coverImageUrl,
        };

        // 处理根分类的 parentId 问题
        if (isEditMode.value && currentEditingCategory.value) {
          // 编辑模式下的特殊处理
          // 如果是根分类（没有 parentId 或 parentId 为空）
          if (
            !currentEditingCategory.value.parentId ||
            currentEditingCategory.value.parentId === ""
          ) {
            if (formData.value.path !== currentEditingCategory.value.path) {
              // 更改根分类的路径时，父级 ID 必须为 ""
              params.parentId = "";
            } else {
              // 修改分类名称、分类描述、优先级时，父级 ID 必须为 0
              params.parentId = 0;
            }
          }
        }

        let response;
        if (isEditMode.value) {
          // 编辑模式 - PUT 请求
          response = await request({
            url: "/tool/update",
            method: "PUT",
            data: params,
          });

          if (response.code === 200) {
            ElMessage.success("分类更新成功");
          } else {
            ElMessage.error(response.message || "更新分类失败");
          }
        } else {
          // 创建模式 - POST 请求
          response = await request({
            url: "/tool/create",
            method: "POST",
            data: params,
          });

          if (response.code === 200) {
            ElMessage.success("分类创建成功");
          } else {
            ElMessage.error(response.message || "创建分类失败");
          }
        }

        if (response.code === 200) {
          handleDialogClose();
          // 重新获取分类数据
          await fetchCategoryTree();
        }
      } catch (error) {
        ElMessage.error(
          isEditMode.value ? "更新分类时发生错误" : "创建分类时发生错误"
        );
        console.error(
          isEditMode.value ? "更新分类时发生错误:" : "创建分类时发生错误:",
          error
        );
      } finally {
        formLoading.value = false;
      }
    }
  });
};

// 删除分类
const handleDeleteCategory = async () => {
  // 使用当前编辑的分类
  const item = currentEditingCategory.value;

  if (!item) {
    ElMessage.error("未找到要删除的分类");
    return;
  }

  try {
    // 自定义确认提示框，实现确认按钮5秒内不可点击
    let countdown = 5;
    let confirmButtonText = `确定 (${countdown}s)`;

    // 创建消息框
    const messageBox = ElMessageBox.confirm(
      `确定要删除分类 "${item.name}" 吗？此操作不可恢复！`,
      "删除确认",
      {
        type: "warning",
        showCancelButton: true,
        confirmButtonText: confirmButtonText,
        cancelButtonText: "取消",
        closeOnClickModal: false, // 点击遮罩层不关闭
        closeOnPressEscape: false, // 按ESC不关闭
        lockScroll: true, // 锁定滚动
        distinguishCancelAndClose: true, // 区分取消和关闭
        beforeClose: (action, instance, done) => {
          if (action === "confirm") {
            // 如果倒计时还没结束，不关闭对话框
            if (countdown > 0) {
              ElMessage.warning(`请等待 ${countdown} 秒后确认删除`);
              return;
            }
            // 倒计时结束，关闭对话框
            done();
          } else {
            // 取消操作，关闭对话框
            done();
          }
        },
      }
    )
      .then(() => {
        // 用户确认删除
        return Promise.resolve();
      })
      .catch((action) => {
        // 用户取消删除
        return Promise.reject(action);
      });

    // 倒计时更新确认按钮文字
    const countdownInterval = setInterval(() => {
      countdown--;
      if (countdown >= 0) {
        confirmButtonText = `确定 (${countdown}s)`;
        // 更新按钮文字
        const confirmButton = document.querySelector(
          ".el-message-box__btns .el-button--primary"
        );
        if (confirmButton) {
          confirmButton.textContent = confirmButtonText;
          // 禁用按钮
          if (countdown > 0) {
            confirmButton.disabled = true;
          } else {
            confirmButton.disabled = false;
          }
        }
      }

      // 倒计时结束，清除定时器
      if (countdown <= 0) {
        clearInterval(countdownInterval);
      }
    }, 1000);

    // 等待用户确认
    await messageBox;

    // 调用后端删除接口
    const response = await request({
      url: `/tool/delete/${item.toolId}`,
      method: "DELETE",
    });

    if (response.code === 200) {
      ElMessage.success("分类删除成功");
      handleDialogClose();
      // 重新获取分类数据
      await fetchCategoryTree();
    } else {
      ElMessage.error(response.message || "删除分类失败");
    }
  } catch (error) {
    // 用户取消删除或发生错误
    if (error !== "cancel" && error !== "close") {
      ElMessage.error("删除分类时发生错误");
      console.error("删除分类时发生错误:", error);
    }
  }
};

// 悬停状态管理
let hoveredItem = ref(null);
let hoverTimer = ref(null);
let mousePosition = ref({ x: 0, y: 0 });

// 计算提示框位置
const tooltipStyle = computed(() => {
  return {
    left: `${mousePosition.value.x + 10}px`,
    top: `${mousePosition.value.y + 10}px`,
  };
});

// 递归转换分类数据
const transformCategoryData = (data) => {
  if (!data || !Array.isArray(data)) return [];

  return data.map((item) => ({
    toolId: item.toolId,
    name: item.toolName,
    coverImageUrl: item.coverImageUrl,
    path: item.path,
    description: item.description,
    sort: item.sort,
    parentId: item.parentId,
    lists: item.children ? transformCategoryData(item.children) : null,
  }));
};

// 获取分类树状结构数据
const fetchCategoryTree = async () => {
  try {
    const response = await getToolCategoryTree();
    if (response.code === 200) {
      // 将后端返回的数据转换为前端需要的格式
      directory.value = transformCategoryData(response.data);
    } else {
      ElMessage.error("获取分类数据失败: " + response.message);
      console.error("获取分类数据失败:", response.message);
    }
  } catch (error) {
    ElMessage.error("获取分类数据时发生错误，请检查网络连接");
    console.error("获取分类数据时发生错误:", error);
  }
};

// 显示子菜单
const showSubMenu = (item) => {
  if (item.lists && item.lists.length > 0) {
    // 更新菜单路径
    if (currentMenu.value === "main") {
      // 从主菜单进入子菜单
      menuPath.value = [item];
    } else {
      // 进入更深层的菜单
      menuPath.value = [...menuPath.value, item];
    }
    currentMenu.value = "sub";
  } else {
    // 如果是叶子节点，跳转到分类展示页面
    router.push({
      name: "classificationdisplay",
      query: {
        id: item.toolId
      }
    });
  }
};

// 返回上一级菜单
const backToParent = () => {
  if (menuPath.value.length > 1) {
    // 返回到上一级子菜单
    menuPath.value = menuPath.value.slice(0, -1);
  } else {
    // 返回到主菜单
    currentMenu.value = "main";
    menuPath.value = [];
  }
};

// 获取当前显示的菜单项列表
const getCurrentMenuItems = () => {
  if (currentMenu.value === "main") {
    return directory.value;
  } else if (currentMenu.value === "sub" && menuPath.value.length > 0) {
    // 返回当前路径下最深层的菜单项列表
    const current = menuPath.value[menuPath.value.length - 1];
    return current.lists || [];
  }
  return [];
};

// 获取当前菜单标题
const getCurrentMenuTitle = () => {
  if (menuPath.value.length > 0) {
    const current = menuPath.value[menuPath.value.length - 1];
    return current.name;
  }
  return "";
};

// 获取父级菜单标题
const getParentMenuTitle = () => {
  if (menuPath.value.length > 1) {
    const parent = menuPath.value[menuPath.value.length - 2];
    return parent.name;
  } else if (menuPath.value.length === 1) {
    return "主菜单";
  }
  return "";
};

// 鼠标进入分类项
const handleMouseEnter = (item, event) => {
  // 更新鼠标位置
  mousePosition.value = { x: event.clientX, y: event.clientY };

  // 清除之前的计时器
  if (hoverTimer.value) {
    clearTimeout(hoverTimer.value);
  }

  // 设置新的计时器，1秒后显示介绍
  hoverTimer.value = setTimeout(() => {
    hoveredItem.value = item;
  }, 1000);
};

// 鼠标离开分类项
const handleMouseLeave = () => {
  // 清除计时器
  if (hoverTimer.value) {
    clearTimeout(hoverTimer.value);
    hoverTimer.value = null;
  }
  // 清除悬停项
  hoveredItem.value = null;
};

// 组件挂载时获取分类数据
onMounted(() => {
  fetchCategoryTree();
});
</script>

<template>
  <div id="menuList">
    <!-- 主菜单 -->
    <!-- [优化] 使用通用的 .menu-grid 类来应用 flex 布局 -->
    <div v-if="currentMenu === 'main'" class="menu-grid">
      <!-- 整理主功能键 -->
      <div class="column picture-rounded-container" @click="router.push('/pigeonhole')">
        <el-icon><PictureRounded /></el-icon>
      </div>
      <div
        v-for="item in getCurrentMenuItems()"
        :key="item.toolId"
        class="column"
        @click="showSubMenu(item)"
        @mouseenter="handleMouseEnter(item, $event)"
        @mouseleave="handleMouseLeave"
      >
        <!-- 如果有封面图片则显示，否则显示默认占位 -->
        <img
          v-if="item.coverImageUrl && item.coverImageUrl !== ''"
          :src="item.coverImageUrl"
        />
        <el-icon v-else class="placeholder-icon"><PictureFilled /></el-icon>
        {{ item.name }}
        <el-icon class="setting-icon" @click.stop="handleEditCategory(item)"
          ><Setting
        /></el-icon>
      </div>
      <!-- 添加分类图标 -->
      <div class="column" @click="handleAddCategory(false)">
        <el-icon><Plus /></el-icon>
      </div>
    </div>

    <!-- 子菜单 -->
    <div v-else-if="currentMenu === 'sub'">
      <!-- 返回按钮区域 -->
      <div class="submenu-header">
        <el-icon class="back-icon" @click="backToParent"
          ><ArrowLeftBold
        /></el-icon>
        <span class="submenu-title">{{ getCurrentMenuTitle() }}</span>
        <span class="parent-menu-title" v-if="getParentMenuTitle()"
          >（返回：{{ getParentMenuTitle() }}）</span
        >
      </div>

      <!-- 子菜单项容器 -->
      <!-- [优化] 同样使用 .menu-grid 类，保持布局一致性 -->
      <div class="menu-grid">
        <div
          v-for="item in getCurrentMenuItems()"
          :key="item.toolId"
          class="column"
          @click="showSubMenu(item)"
          @mouseenter="handleMouseEnter(item, $event)"
          @mouseleave="handleMouseLeave"
        >
          <!-- 如果有封面图片则显示，否则显示默认占位 -->
          <img
            v-if="item.coverImageUrl && item.coverImageUrl !== ''"
            :src="item.coverImageUrl"
          />
          <el-icon v-else class="placeholder-icon"><PictureFilled /></el-icon>
          {{ item.name }}
          <el-icon class="setting-icon" @click.stop="handleEditCategory(item)"
            ><Setting
          /></el-icon>
        </div>

        <!-- 添加分类图标 -->
        <div
          class="column"
          @click="handleAddCategory(true, menuPath[menuPath.length - 1])"
        >
          <el-icon><Plus /></el-icon>
        </div>
      </div>
    </div>
  </div>

  <!-- 自定义提示组件 -->
  <div
    v-if="hoveredItem && hoveredItem.description"
    class="custom-tooltip"
    :style="tooltipStyle"
    @mouseenter="handleMouseEnter(hoveredItem, $event)"
    @mouseleave="handleMouseLeave"
  >
    {{ hoveredItem.description }}
  </div>

  <!-- 添加/编辑分类对话框 -->
  <el-dialog
    v-model="dialogVisible"
    :title="
      isEditMode
        ? '编辑分类'
        : addContext.isSubCategory
        ? '添加子分类'
        : '添加分类'
    "
    width="500px"
    :before-close="handleDialogClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="80px"
      v-loading="formLoading"
    >
      <el-form-item label="分类ID" v-if="isEditMode">
        <el-input v-model="formData.toolId" disabled />
      </el-form-item>

      <el-form-item label="分类名称" prop="toolName">
        <el-input v-model="formData.toolName" placeholder="请输入分类名称" />
      </el-form-item>

      <el-form-item label="父级ID" v-if="!addContext.isSubCategory">
        <el-select
          v-model="formData.parentId"
          placeholder="请选择父级分类"
          clearable
          @change="handleParentIdChange"
        >
          <el-option
            v-for="item in parentOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="分类路径" prop="path">
        <el-input v-model="formData.path" placeholder="请输入分类路径" />
      </el-form-item>

      <el-form-item
        label="父级ID"
        v-if="addContext.isSubCategory && formData.parentId"
      >
        <el-input v-model="formData.parentId" disabled />
      </el-form-item>

      <el-form-item label="分类描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          placeholder="请输入分类描述（可选）"
        />
      </el-form-item>

      <el-form-item label="封面地址" prop="coverImageUrl">
        <el-input
          v-model="formData.coverImageUrl"
          placeholder="请输入封面图片地址"
        />
      </el-form-item>

      <el-form-item label="优先级" prop="sort">
        <el-input-number
          v-model="formData.sort"
          :min="1"
          :max="100"
          controls-position="right"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button
          v-if="isEditMode"
          type="danger"
          @click="handleDeleteCategory()"
          :loading="formLoading"
          >删除分类</el-button
        >
        <div class="main-buttons">
          <el-button @click="handleDialogClose">取消</el-button>
          <el-button
            type="primary"
            @click="handleCreateCategory"
            :loading="formLoading"
            >确定</el-button
          >
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/* [优化] #menuList 只作为最外层容器，负责全局边距 */
#menuList {
  padding: 20px;
  width: 100%;
  box-sizing: border-box;
}

/* [优化] 新建一个通用的 .menu-grid 类，专门负责网格布局 */
.menu-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 20px;
  width: 100%;
}

/* 控制分类项的基本样式 */
.column {
  width: 150px;
  height: 150px;
  border: 1px solid #ddd;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); /* 添加轻微阴影效果 */
  background-color: rgba(255, 255, 255, 0.8); /* 半透明白色背景 */
  cursor: pointer;
  transition: all 0.3s ease; /* 添加过渡动画效果 */
  position: relative; /* 用于设置图标的定位 */
}

/* 控制分类项中图片的样式 */
.column img {
  width: 100px;
  height: 100px;
  object-fit: cover;
  margin-bottom: 10px;
  border-radius: 4px;
}

/* 控制占位图标的样式 */
.placeholder-icon {
  width: 100px;
  height: 100px;
  font-size: 40px;
  margin-bottom: 10px;
  color: #ccc;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 控制整理主功能键中图标样式 */
.picture-rounded-container > .el-icon {
  width: 50%;
  height: 50%;
  font-size: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 控制鼠标悬停时分类项的样式 */
.column:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15); /* 增强阴影效果 */
  transform: translateY(-2px); /* 向上轻微移动 */
}

/* 子菜单返回按钮区域 */
.submenu-header {
  width: 100%;
  display: flex;
  align-items: center;
  margin-bottom: 20px; /* 增加了与下方内容的间距 */
  color: #999;
}

/* 控制返回按钮的样式 */
.back-icon {
  font-size: 24px;
  cursor: pointer;
  margin-right: 10px;
}

/* 控制鼠标悬停时返回按钮的样式 */
.back-icon:hover {
  color: #409eff;
}

/* 控制子菜单标题的样式 */
.submenu-title {
  font-size: 18px;
  font-weight: bold;
  color: #999;
}

/* 控制父级菜单标题的样式 */
.parent-menu-title {
  font-size: 14px;
  color: #999;
  margin-left: 10px;
}

/* 自定义提示组件样式 */
.custom-tooltip {
  position: fixed;
  background-color: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 10px 15px;
  border-radius: 4px;
  font-size: 14px;
  z-index: 1000;
  max-width: 300px;
  word-wrap: break-word;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  pointer-events: none; /* 防止提示框干扰鼠标事件 */
}

/* 设置图标样式 */
.setting-icon {
  position: absolute;
  top: 5px;
  right: 5px;
  font-size: 16px;
  color: #909399;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.3s;
}

/* 控制鼠标悬停时设置图标的样式 */
.setting-icon:hover {
  color: #409eff;
}

/* 控制鼠标悬停时设置图标的显示 */
.column:hover .setting-icon {
  opacity: 1;
}

/* 对话框底部按钮布局 */
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

/* 控制主按钮区域的样式 */
.main-buttons {
  display: flex;
  gap: 10px;
}
</style>
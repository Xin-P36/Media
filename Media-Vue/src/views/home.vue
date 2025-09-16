<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { SemiSelect } from "@element-plus/icons-vue";
import request from "@/utils/axiosRequest";
import { ElMessage } from "element-plus";
import { getToolCategoryTree } from "@/utils/toolCategory";

const router = useRouter();

// 分类数据
const toolCategories = ref([]); //分类列表
const defaultDisplay = ref(""); //默认展示分类
const defaultNumberPages = ref(50); // 首页默认加载条数

// 用户数据
const userData = ref({
  nickName: "", //昵称
  avatar: "", //头像
  homeBackground: "", //背景图片
});

// 保存原始用户数据
const originalUserForm = ref({});

// 任务进度数据
const moveTaskProgress = ref(null); // 移动任务进度
const transcodeTaskProgress = ref(null); // 视频转码任务进度
const deleteTaskProgress = ref(null); // 删除任务进度
const thumbnailTaskProgress = ref(null); // 缩略图任务进度
const scanTaskProgress = ref(null); // 扫描任务进度
const normalizeVideoTaskProgress = ref(null); // 视频规范任务进度

// 轮询定时器
const progressPollingTimer = ref(null); // 移动任务进度轮询定时器
const transcodeProgressPollingTimer = ref(null); // 视频转码任务进度轮询定时器
const deleteProgressPollingTimer = ref(null); // 删除任务进度轮询定时器
const thumbnailProgressPollingTimer = ref(null); // 缩略图任务进度轮询定时器
const scanProgressPollingTimer = ref(null); // 扫描任务进度轮询定时器
const normalizeVideoProgressPollingTimer = ref(null); // 视频规范任务进度轮询定时器

// 视频压缩预设数据
const videoPresets = ref([]); // 视频压缩预设列表

// 隐藏分类数据
const hiddenCategories = ref([]); // 已隐藏的分类列表
const selectedCategoryToAdd = ref(""); // 选中要添加的分类ID
const currentPreset = ref(null); // 当前编辑的预设索引
const showPresetForm = ref(false); // 控制预设编辑对话框的显示
const presetForm = ref({
  name: "", // 预设名称
  container: "MP4", // 容器格式
  video: {
    codec: "libx265", // 视频编码器
    bitrate: null, // 视频码率
    resolution: "", // 视频分辨率
    framerate: "", // 视频帧率
    crf: "", // 视频CRF值
    speed: "" // 视频编码速度
  },
  audio: {
    codec: "aac", // 音频编码器
    bitrate: "128k" // 音频码率
  }
});

const userForm = ref({
  account: "", //账号
  nickName: "", //昵称
  avatar: "", //头像地址
  thumbnailThreshold: "", //缩略图阈值
  width: "", //缩略图生成质量
  height: "", //视频第？秒作为封面
  loginBackground: "", //登录背景图片
  homeBackground: "", //主页背景图片
});

// 初始化数据
onMounted(async () => {
  //router.push("/show"); //默认展示首页
  //读取用户数据
  const userToken = JSON.parse(localStorage.getItem("userToken")); // 从localStorage获取用户token
  if (userToken) {
    userData.value = userToken; //主页使用的数据
    userForm.value = { ...userToken }; //修改用户信息使用的数据
    originalUserForm.value = { ...userToken }; //保存原始用户数据
  }

  // 获取分类数据
  try {
    const response = await getToolCategoryTree(); // 获取工具分类树
    if (response.code === 200) {
      // 添加一个空选项作为默认选项
      toolCategories.value = [
        {
          toolId: "",
          toolName: "无",
          children: []
        },
        ...response.data
      ];
    }
  } catch (error) {
    console.error("获取分类数据失败:", error);
    ElMessage.error("获取分类数据失败");
  }

  // 读取默认展示分类设置
  const savedDefaultDisplay = localStorage.getItem("defaultDisplay"); // 从localStorage获取默认展示分类
  if (savedDefaultDisplay) {
    defaultDisplay.value = isNaN(Number(savedDefaultDisplay)) ? savedDefaultDisplay : Number(savedDefaultDisplay);
  }

  // 初始化视频预设数据
  initVideoPresets();

  // 获取隐藏分类数据
  await fetchHiddenCategories();
  // 读取首页默认加载条数设置
  const savedDefaultNumberPages = localStorage.getItem("defaultNumberPages"); // 从localStorage获取首页默认加载条数
  if (savedDefaultNumberPages) {
    defaultNumberPages.value = parseInt(savedDefaultNumberPages, 10);
  }
});

// 组件卸载时清除定时器
onUnmounted(() => {
  clearAllTimers();
});

// 初始化视频预设数据
const initVideoPresets = () => {
  const savedPresets = localStorage.getItem("videoPresets"); // 从localStorage获取视频预设数据
  if (savedPresets) {
    videoPresets.value = JSON.parse(savedPresets); // 如果存在，则解析并赋值
  } else {
    // 初始化预设项
    videoPresets.value = [
      {
        name: "4K",
        container: "MP4",
        video:{
          codec: "libx265",
          bitrate: "10Mbps",
          resolution: "3840x2160",
          framerate: "",
          crf: "",
          speed: ""
        },
        audio:{
          codec: "aac",
          bitrate: "128k"
        },
      },
      {
        name: "2K",
        container: "MP4",
        video:{
          codec: "libx265",
          bitrate: "8Mbps",
          resolution: "2560x1440",
          framerate: "",
          crf: "",
          speed: ""
        },
        audio:{
          codec: "aac",
          bitrate: "128k"
        },

      },
      {
        name: "1080P",
        container: "MP4",
        video:{
          codec: "libx265",
          bitrate: "6Mbps",
          resolution: "1920x1080",
          framerate: "",
          crf: "",
          speed: "",
        },
        audio:{
          codec: "aac",
          bitrate: "128k"
        },
      },
    ];
    // 将初始化的数据存入 localStorage
    localStorage.setItem("videoPresets", JSON.stringify(videoPresets.value));
  }
};
// 获取隐藏分类数据
const fetchHiddenCategories = async () => {
  try {
    const response = await request({
      url: "/hide-list",
      method: "GET"
    }); // 发送GET请求获取隐藏分类列表
    
    if (response.code === 200) {
      hiddenCategories.value = response.data || []; // 如果请求成功，更新隐藏分类列表
    } else {
      ElMessage.error(response.message || "获取隐藏分类数据失败");
    }
  } catch (error) {
    console.error("获取隐藏分类数据失败:", error);
    ElMessage.error("获取隐藏分类数据失败");
  }
};

//数据定义
let showAvatarMenu = ref(false); // 控制头像菜单显示状态
let hideTimer = ref(null); // 菜单隐藏定时器
let showSettingsDialog = ref(false); // 控制设置对话框显示状态

// 表单引用
const formRef = ref(null); // 表单引用
// 表单验证规则
const formRules = {
  account: [ // 登录账号验证规则
    { required: true, message: "请输入登录账号", trigger: "blur" },
    { min: 4, max: 20, message: "账号长度应在4到20个字符之间", trigger: "blur" },
  ],
  nickName: [ // 昵称验证规则
    { required: true, message: "请输入昵称", trigger: "blur" },
    { min: 2, max: 10, message: "昵称长度应在2到10个字符之间", trigger: "blur" },
  ],
  avatar: [ // 头像图片路径验证规则
    { required: true, message: "请输入头像图片路径", trigger: "blur" }
  ],
  thumbnailThreshold: [ // 缩略图阈值验证规则
    { required: true, message: "请输入缩略图阈值", trigger: "blur" }
  ],
  width: [ // 缩略图生成质量验证规则
    { required: true, message: "请输入缩略图生成质量", trigger: "blur" },
    { type: "number", min: 1, max: 99, message: "缩略图生成质量应在1到99之间", trigger: "blur" },
  ],
  height: [ // 封面开始时间验证规则
    { required: true, message: "请输入封面开始时间", trigger: "blur" }
  ],
  loginBackground: [ // 登录背景图片路径验证规则
    { required: true, message: "请输入登录背景图片路径", trigger: "blur" }
  ],
  homeBackground: [ // 主页背景图片路径验证规则
    { required: true, message: "请输入主页背景图片路径", trigger: "blur" }
  ],
};

// 显示菜单
const showMenu = () => {
  if (hideTimer.value) {
    clearTimeout(hideTimer.value); // 清除隐藏定时器
    hideTimer.value = null;
  }
  showAvatarMenu.value = true; // 显示头像菜单
};

// 隐藏菜单
const hideMenu = () => {
  hideTimer.value = setTimeout(() => {
    showAvatarMenu.value = false; // 隐藏头像菜单
    hideTimer.value = null; // 清除定时器引用
  }, 100);
};

// 跳转到首页
const goToHome = () => {
  router.push("/show"); // 使用vue-router跳转到/show路由
};

// 跳转到整理页面
const goToFinishing = () => {
  router.push("/finishing"); // 使用vue-router跳转到/finishing路由
};

// 退出登录
const logout = async () => {
  try {
    await request.delete("/user/logout"); // 发送DELETE请求退出登录
    ElMessage.success("退出登录成功");
  } catch (error) {
    ElMessage.error("退出登录失败: " + (error.message || "未知错误"));
  } finally {
    localStorage.removeItem("userToken"); // 从localStorage移除用户token
    window.location.reload(); // 重新加载页面
  }
};

// 显示设置对话框
const showSettings = () => {
  showSettingsDialog.value = true; // 显示设置对话框
  showAvatarMenu.value = false; // 隐藏头像菜单
};
// 保存用户爱好设置
const saveUserHobbySettings = async () => {
  try {
    await formRef.value.validate(); // 验证表单
    const response = await request.put("/user/update", userForm.value); // 发送PUT请求更新用户信息

    if (response.code === 200) {
      ElMessage.success("用户爱好设置保存成功");

      const userToken = JSON.parse(localStorage.getItem("userToken")) || {};
      const updatedUserToken = { ...userToken, ...response.data };
      localStorage.setItem("userToken", JSON.stringify(updatedUserToken)); // 更新localStorage中的用户token

      userData.value = updatedUserToken; // 更新主页使用的用户数据
      originalUserForm.value = { ...userForm.value }; // 更新原始用户数据
    } else {
      ElMessage.error(response.message || "保存失败");
    }
  } catch (error) {
    console.error("保存用户爱好设置时出错:", error);
    ElMessage.error("保存用户爱好设置时出错");
  }
};

// 取消用户爱好设置
const cancelUserHobbySettings = () => {
  userForm.value = { ...originalUserForm.value }; // 恢复表单数据为原始数据
  ElMessage.info("已取消用户爱好设置更改");
};

// 保存首页展示分类设置
const saveDisplayCategorySettings = () => {
  localStorage.setItem("defaultDisplay", defaultDisplay.value); // 将默认展示分类保存到localStorage
  ElMessage.success("首页展示分类设置保存成功");
};

// 取消首页展示分类设置
const cancelDisplayCategorySettings = () => {
  const savedDefaultDisplay = localStorage.getItem("defaultDisplay"); // 从localStorage获取默认展示分类
  if (savedDefaultDisplay) {
    defaultDisplay.value = isNaN(Number(savedDefaultDisplay)) ? savedDefaultDisplay : Number(savedDefaultDisplay); // 恢复默认展示分类
  } else {
    defaultDisplay.value = "";
  }
  ElMessage.info("已取消首页展示分类设置更改");
};

// 保存首页默认加载条数设置
const saveNumberPagesSettings = () => {
  localStorage.setItem("defaultNumberPages", defaultNumberPages.value); // 将首页默认加载条数保存到localStorage
  ElMessage.success("首页默认加载条数设置保存成功");
};

// 取消首页默认加载条数设置
const cancelNumberPagesSettings = () => {
  const savedDefaultNumberPages = localStorage.getItem("defaultNumberPages"); // 从localStorage获取首页默认加载条数
  if (savedDefaultNumberPages) {
    defaultNumberPages.value = parseInt(savedDefaultNumberPages, 10); // 恢复首页默认加载条数
  } else {
    defaultNumberPages.value = 50;
  }
  ElMessage.info("已取消首页默认加载条数设置更改");
};

// 保存展示分类设置（保持原有函数以确保其他地方调用不报错）
const saveCategorySettings = () => {
  localStorage.setItem("defaultDisplay", defaultDisplay.value); // 保存默认展示分类
  ElMessage.success("展示分类设置保存成功");
  localStorage.setItem("defaultNumberPages", defaultNumberPages.value); // 保存首页默认加载条数
};

// 取消展示分类设置（保持原有函数以确保其他地方调用不报错）
const cancelCategorySettings = () => {
  const savedDefaultDisplay = localStorage.getItem("defaultDisplay"); // 获取默认展示分类
  if (savedDefaultDisplay) {
    defaultDisplay.value = isNaN(Number(savedDefaultDisplay)) ? savedDefaultDisplay : Number(savedDefaultDisplay); // 恢复默认展示分类
  } else {
    defaultDisplay.value = "";
  }
  // 恢复默认加载条数设置
  const savedDefaultNumberPages = localStorage.getItem("defaultNumberPages"); // 获取首页默认加载条数
  if (savedDefaultNumberPages) {
    defaultNumberPages.value = parseInt(savedDefaultNumberPages, 10); // 恢复首页默认加载条数
  } else {
    defaultNumberPages.value = 50;
  }
  ElMessage.info("已取消展示分类设置更改");
};

// 保存视频预设数据到localStorage
const saveVideoPresets = () => {
  localStorage.setItem("videoPresets", JSON.stringify(videoPresets.value)); // 将视频预设数据保存到localStorage
  ElMessage.success("视频预设保存成功");
};

// 添加或更新预设
const savePreset = () => {
  // 如果设置了CRF，则bitrate固定为null, 反之亦然
  if (presetForm.value.video.crf) {
    presetForm.value.video.bitrate = null; // 如果设置了CRF，则bitrate固定为null
  }
  
  if (currentPreset.value !== null) {
    // 更新现有预设
    videoPresets.value[currentPreset.value] = { ...presetForm.value }; // 更新指定索引的预设
  } else {
    // 添加新预设
    videoPresets.value.push({ ...presetForm.value }); // 添加新的预设到列表
  }
  saveVideoPresets(); // 保存视频预设数据
  showPresetForm.value = false; // 隐藏预设编辑对话框
};

// 编辑预设
const editPreset = (index) => {
  currentPreset.value = index; // 设置当前编辑的预设索引
  // 深拷贝预设数据到表单
  presetForm.value = JSON.parse(JSON.stringify(videoPresets.value[index])); // 将指定索引的预设数据深拷贝到表单
  showPresetForm.value = true; // 显示预设编辑对话框
};

// 删除预设
const deletePreset = (index) => {
  videoPresets.value.splice(index, 1); // 从视频预设列表中删除指定索引的预设
  saveVideoPresets(); // 保存视频预设数据
};

// 添加新预设
const addPreset = () => {
  currentPreset.value = null; // null表示是新增
  // 重置表单
  presetForm.value = {
    name: "", // 预设名称
    container: "MP4", // 容器格式
    video: {
      codec: "libx265", // 视频编码器
      bitrate: null, // 视频码率
      resolution: "", // 视频分辨率
      framerate: "", // 视频帧率
      crf: "", // 视频CRF值
      speed: "" // 视频编码速度
    },
    audio: {
      codec: "aac", // 音频编码器
      bitrate: "128k" // 音频码率
    }
  };
  showPresetForm.value = true; // 显示预设编辑对话框
};
// 添加隐藏分类
const addHiddenCategory = async () => {
  if (!selectedCategoryToAdd.value) {
    ElMessage.warning("请选择要隐藏的分类");
    return;
  }

  try {
    const response = await request({
      url: "/hide-list",
      method: "POST",
      data: {
        hideId: selectedCategoryToAdd.value // 要隐藏的分类ID
      }
    }); // 发送POST请求添加隐藏分类

    if (response.code === 200) {
      ElMessage.success("添加隐藏分类成功");
      // 重新获取隐藏分类列表
      await fetchHiddenCategories();
      // 清空选择
      selectedCategoryToAdd.value = ""; // 清空选中的分类
    } else {
      ElMessage.error(response.message || "添加隐藏分类失败");
    }
  } catch (error) {
    console.error("添加隐藏分类失败:", error);
    ElMessage.error("添加隐藏分类失败");
  }
};

// 删除隐藏分类
const deleteHiddenCategory = async (id) => {
  try {
    const response = await request({
      url: `/hide-list/${id}`, // 删除指定ID的隐藏分类
      method: "DELETE"
    }); // 发送DELETE请求删除隐藏分类

    if (response.code === 200) {
      ElMessage.success("删除隐藏分类成功");
      // 重新获取隐藏分类列表
      await fetchHiddenCategories();
    } else {
      ElMessage.error(response.message || "删除隐藏分类失败");
    }
  } catch (error) {
    console.error("删除隐藏分类失败:", error);
    ElMessage.error("删除隐藏分类失败");
  }
};

// 启动移动任务
const startMoveTask = async () => {
  try {
    const res = await request({
      url: "/task/start?type=MOVE", // 启动移动任务的URL
      method: "POST",
    }); // 发送POST请求启动移动任务
    
    if (res.code === 200) {
      ElMessage.success("移动任务已启动");
      // 初始化进度
      moveTaskProgress.value = {
        status: "RUNNING", // 任务状态
        percentage: 0, // 任务进度百分比
        message: "", // 任务消息
        currentStep: "" // 当前步骤
      };
      // 开始轮询进度
      startProgressPolling();
    } else {
      ElMessage.error("启动移动任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动移动任务异常: " + error.message);
  }
};

// 启动视频转码任务
const startTranscodeTask = async () => {
  try {
    const res = await request({
      url: "/task/start?type=TRANSCODE", // 启动视频转码任务的URL
      method: "POST",
    }); // 发送POST请求启动视频转码任务
    
    if (res.code === 200) {
      ElMessage.success("视频转码任务已启动");
      // 初始化进度
      transcodeTaskProgress.value = {
        status: "RUNNING", // 任务状态
        percentage: 0, // 任务进度百分比
        message: "", // 任务消息
        currentStep: "" // 当前步骤
      };
      // 开始轮询进度
      startTranscodeProgressPolling();
    } else {
      ElMessage.error("启动视频转码任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动视频转码任务异常: " + error.message);
  }
};

// 启动删除任务
const startDeleteTask = async () => {
  try {
    const res = await request({
      url: "/task/start?type=DELETE", // 启动删除任务的URL
      method: "POST",
    }); // 发送POST请求启动删除任务
    
    if (res.code === 200) {
      ElMessage.success("删除任务已启动");
      // 初始化进度
      deleteTaskProgress.value = {
        status: "RUNNING", // 任务状态
        percentage: 0, // 任务进度百分比
        message: "", // 任务消息
        currentStep: "" // 当前步骤
      };
      // 开始轮询进度
      startDeleteProgressPolling();
    } else {
      ElMessage.error("启动删除任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动删除任务异常: " + error.message);
  }
};

// 启动缩略图任务
const startThumbnailTask = async () => {
  try {
    const res = await request({
      url: "/task/start?type=THUMBNAIL", // 启动缩略图任务的URL
      method: "POST",
    }); // 发送POST请求启动缩略图任务
    
    if (res.code === 200) {
      ElMessage.success("缩略图任务已启动");
      // 初始化进度
      thumbnailTaskProgress.value = {
        status: "RUNNING", // 任务状态
        percentage: 0, // 任务进度百分比
        message: "", // 任务消息
        currentStep: "" // 当前步骤
      };
      // 开始轮询进度
      startThumbnailProgressPolling();
    } else {
      ElMessage.error("启动缩略图任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动缩略图任务异常: " + error.message);
  }
};

// 开始轮询移动任务进度
const startProgressPolling = () => {
  // 如果已有轮询定时器，先清除
  if (progressPollingTimer.value) {
    clearInterval(progressPollingTimer.value); // 清除已有的轮询定时器
  }
  
  // 设置新的轮询定时器（每2秒查询一次）
  progressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({
        url: "/task/progress", // 获取任务进度的URL
        method: "GET",
      }); // 发送GET请求获取任务进度
      
      if (res.code === 200) {
        moveTaskProgress.value = res.data; // 更新移动任务进度
        
        // 如果任务已完成、失败或取消，停止轮询
        if (res.data.status === "COMPLETED" || res.data.status === "FAILED" || res.data.status === "CANCELED") {
          clearInterval(progressPollingTimer.value); // 清除轮询定时器
          progressPollingTimer.value = null; // 清除定时器引用
        }
      } else {
        console.error("获取移动任务进度失败:", res.message);
      }
    } catch (error) {
      console.error("获取移动任务进度异常:", error.message);
    }
  }, 2000); // 每2秒轮询一次
};

// 开始轮询转码任务进度
const startTranscodeProgressPolling = () => {
  // 如果已有轮询定时器，先清除
  if (transcodeProgressPollingTimer.value) {
    clearInterval(transcodeProgressPollingTimer.value); // 清除已有的轮询定时器
  }
  
  // 设置新的轮询定时器（每2秒查询一次）
  transcodeProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({
        url: "/task/progress", // 获取任务进度的URL
        method: "GET",
      }); // 发送GET请求获取任务进度
      
      if (res.code === 200) {
        transcodeTaskProgress.value = res.data; // 更新转码任务进度
        
        // 如果任务已完成、失败或取消，停止轮询
        if (res.data.status === "COMPLETED" || res.data.status === "FAILED" || res.data.status === "CANCELED") {
          clearInterval(transcodeProgressPollingTimer.value); // 清除轮询定时器
          transcodeProgressPollingTimer.value = null; // 清除定时器引用
        }
      } else {
        console.error("获取转码任务进度失败:", res.message);
      }
    } catch (error) {
      console.error("获取转码任务进度异常:", error.message);
    }
  }, 2000); // 每2秒轮询一次
};

// 开始轮询删除任务进度
const startDeleteProgressPolling = () => {
  // 如果已有轮询定时器，先清除
  if (deleteProgressPollingTimer.value) {
    clearInterval(deleteProgressPollingTimer.value); // 清除已有的轮询定时器
  }
  
  // 设置新的轮询定时器（每2秒查询一次）
  deleteProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({
        url: "/task/progress", // 获取任务进度的URL
        method: "GET",
      }); // 发送GET请求获取任务进度
      
      if (res.code === 200) {
        deleteTaskProgress.value = res.data; // 更新删除任务进度
        
        // 如果任务已完成、失败或取消，停止轮询
        if (res.data.status === "COMPLETED" || res.data.status === "FAILED" || res.data.status === "CANCELED") {
          clearInterval(deleteProgressPollingTimer.value); // 清除轮询定时器
          deleteProgressPollingTimer.value = null; // 清除定时器引用
        }
      } else {
        console.error("获取删除任务进度失败:", res.message);
      }
    } catch (error) {
      console.error("获取删除任务进度异常:", error.message);
    }
  }, 2000); // 每2秒轮询一次
};

// 开始轮询缩略图任务进度
const startThumbnailProgressPolling = () => {
  // 如果已有轮询定时器，先清除
  if (thumbnailProgressPollingTimer.value) {
    clearInterval(thumbnailProgressPollingTimer.value); // 清除已有的轮询定时器
  }
  
  // 设置新的轮询定时器（每2秒查询一次）
  thumbnailProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({
        url: "/task/progress", // 获取任务进度的URL
        method: "GET",
      }); // 发送GET请求获取任务进度
      
      if (res.code === 200) {
        thumbnailTaskProgress.value = res.data; // 更新缩略图任务进度
        
        // 如果任务已完成、失败或取消，停止轮询
        if (res.data.status === "COMPLETED" || res.data.status === "FAILED" || res.data.status === "CANCELED") {
          clearInterval(thumbnailProgressPollingTimer.value); // 清除轮询定时器
          thumbnailProgressPollingTimer.value = null; // 清除定时器引用
        }
      } else {
        console.error("获取缩略图任务进度失败:", res.message);
      }
    } catch (error) {
      console.error("获取缩略图任务进度异常:", error.message);
    }
  }, 2000); // 每2秒轮询一次
};

// 获取进度条状态
const getProgressStatus = (status) => {
  switch (status) {
    case "COMPLETED": // 任务完成
      return "success";
    case "FAILED": // 任务失败
    case "CANCELED": // 任务取消
      return "exception";
    default:
      return null; // 任务进行中
  }
};

// 取消移动任务
const cancelMoveTask = async () => {
  try {
    const res = await request({
      url: "/task/cancel", // 取消任务的URL
      method: "POST",
    }); // 发送POST请求取消任务
    
    if (res.code === 200) {
      ElMessage.success("取消请求已发送");
    } else {
      ElMessage.error("取消任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("取消任务异常: " + error.message);
  }
};

// 取消视频规范任务
const cancelNormalizeVideoTask = async () => {
  try {
    const res = await request({
      url: "/task/cancel", // 取消任务的URL
      method: "POST",
    }); // 发送POST请求取消任务
    
    if (res.code === 200) {
      ElMessage.success("取消请求已发送");
    } else {
      ElMessage.error("取消视频规范任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("取消视频规范任务异常: " + error.message);
  }
};

// 组件卸载时清除定时器
const clearAllTimers = () => {
  if (progressPollingTimer.value) {
    clearInterval(progressPollingTimer.value); // 清除移动任务进度轮询定时器
  }
  if (transcodeProgressPollingTimer.value) {
    clearInterval(transcodeProgressPollingTimer.value); // 清除视频转码任务进度轮询定时器
  }
  if (deleteProgressPollingTimer.value) {
    clearInterval(deleteProgressPollingTimer.value); // 清除删除任务进度轮询定时器
  }
  if (thumbnailProgressPollingTimer.value) {
    clearInterval(thumbnailProgressPollingTimer.value); // 清除缩略图任务进度轮询定时器
  }
  if (scanProgressPollingTimer.value) {
    clearInterval(scanProgressPollingTimer.value); // 清除扫描任务进度轮询定时器
  }
  if (normalizeVideoProgressPollingTimer.value) {
    clearInterval(normalizeVideoProgressPollingTimer.value); // 清除视频规范任务进度轮询定时器
  }
};

// 启动扫描任务
const startScanTask = async () => {
  try {
    const res = await request({
      url: "/media/scan/start?path=TemporaryMedia", // 启动扫描任务的URL
      method: "POST",
    }); // 发送POST请求启动扫描任务
    
    if (res.code === 200) {
      ElMessage.success("扫描任务已启动");
      // 初始化进度
      scanTaskProgress.value = {
        status: "RUNNING", // 任务状态
        totalFiles: 0, // 总文件数
        processedFiles: 0, // 已处理文件数
        percentage: 0, // 任务进度百分比
        currentFileName: "", // 当前处理的文件名
        message: "正在启动扫描任务" // 任务消息
      };
      // 开始轮询进度
      startScanProgressPolling();
    } else {
      ElMessage.error("启动扫描任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动扫描任务异常: " + error.message);
  }
};

// 开始轮询扫描任务进度
const startScanProgressPolling = () => {
  // 如果已有轮询定时器，先清除
  if (scanProgressPollingTimer.value) {
    clearInterval(scanProgressPollingTimer.value); // 清除已有的轮询定时器
  }
  
  // 设置新的轮询定时器（每2秒查询一次）
  scanProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({
        url: "/media/scan/progress", // 获取扫描任务进度的URL
        method: "GET",
      }); // 发送GET请求获取扫描任务进度
      
      if (res.code === 200) {
        scanTaskProgress.value = res.data; // 更新扫描任务进度
        
        // 如果任务已完成、失败或取消，停止轮询
        if (res.data.status === "COMPLETED" || res.data.status === "FAILED" || res.data.status === "CANCELED") {
          clearInterval(scanProgressPollingTimer.value); // 清除轮询定时器
          scanProgressPollingTimer.value = null; // 清除定时器引用
        }
      } else {
        console.error("获取扫描任务进度失败:", res.message);
      }
    } catch (error) {
      console.error("获取扫描任务进度异常:", error.message);
    }
  }, 2000); // 每2秒轮询一次
};

// 取消扫描任务
const cancelScanTask = async () => {
  try {
    const res = await request({
      url: "/media/scan/cancel", // 取消扫描任务的URL
      method: "POST",
    }); // 发送POST请求取消扫描任务
    
    if (res.code === 200) {
      ElMessage.success("取消请求已发送");
    } else {
      ElMessage.error("取消扫描任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("取消扫描任务异常: " + error.message);
  }
};

// 启动视频规范任务
const startNormalizeVideoTask = async () => {
  try {
    const res = await request({
      url: "/task/start?type=NORMALIZE_VIDEO", // 启动视频规范任务的URL
      method: "POST",
    }); // 发送POST请求启动视频规范任务
    
    if (res.code === 200) {
      ElMessage.success("视频规范任务已启动");
      // 初始化进度
      normalizeVideoTaskProgress.value = {
        status: "RUNNING", // 任务状态
        percentage: 0, // 任务进度百分比
        message: "", // 任务消息
        currentStep: "" // 当前步骤
      };
      // 开始轮询进度
      startNormalizeVideoProgressPolling();
    } else {
      ElMessage.error("启动视频规范任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动视频规范任务异常: " + error.message);
  }
};

// 开始轮询视频规范任务进度
const startNormalizeVideoProgressPolling = () => {
  // 如果已有轮询定时器，先清除
  if (normalizeVideoProgressPollingTimer.value) {
    clearInterval(normalizeVideoProgressPollingTimer.value); // 清除已有的轮询定时器
  }
  
  // 设置新的轮询定时器（每2秒查询一次）
  normalizeVideoProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({
        url: "/task/progress", // 获取任务进度的URL
        method: "GET",
      }); // 发送GET请求获取任务进度
      
      if (res.code === 200) {
        normalizeVideoTaskProgress.value = res.data; // 更新视频规范任务进度
        
        // 如果任务已完成、失败或取消，停止轮询
        if (res.data.status === "COMPLETED" || res.data.status === "FAILED" || res.data.status === "CANCELED") {
          clearInterval(normalizeVideoProgressPollingTimer.value); // 清除轮询定时器
          normalizeVideoProgressPollingTimer.value = null; // 清除定时器引用
        }
      } else {
        console.error("获取视频规范任务进度失败:", res.message);
      }
    } catch (error) {
      console.error("获取视频规范任务进度异常:", error.message);
    }
  }, 2000); // 每2秒轮询一次
};
</script>

<template>
  <div class="background-container">
    <div
      class="background"
      :style="{ backgroundImage: 'url(' + userData.homeBackground + ')' }"
    ></div>
    <!-- 顶栏 -->
    <el-row id="home-top">
      <el-col :span="10" id="home-top-1" class="home-top-p" @click="goToHome">
        首页
      </el-col>
      <el-col :span="2" id="home-top-2" class="home-top-p">
        <el-icon><SemiSelect /></el-icon>
      </el-col>
      <el-col
        :span="10"
        id="home-top-4"
        class="home-top-p"
        @click="goToFinishing"
        >整理</el-col
      >
      <el-col :span="1" style="text-align: center">{{
        userData.nickName
      }}</el-col>
      <!-- 头像区域 -->
      <el-col :span="1" id="home-top-3" class="home-top-p">
        <div
          class="avatar-container"
          @mouseenter="showMenu"
          @mouseleave="hideMenu"
        >
          <img :src="userData.avatar" alt="用户头像" class="avatar-image" />
          <!-- 头像下拉菜单 -->
          <div
            v-show="showAvatarMenu"
            class="avatar-menu"
            @mouseenter="showMenu"
            @mouseleave="hideMenu"
          >
            <ul class="menu-list">
              <li class="menu-item" @click="showSettings">设置</li>
              <li class="menu-item" @click="logout">退出登录</li>
            </ul>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 设置对话框 -->
    <el-dialog
      v-model="showSettingsDialog"
      title="设置"
      width="650px"
      header-align="left"
    >
      <el-collapse :model-value="['0']">
        <!-- 任务列表模块 -->
        <el-collapse-item title="任务列表" name="0">
          <div class="task-content">
            <!-- 视频规范任务按钮 -->
            <el-button
              v-if="!normalizeVideoTaskProgress || normalizeVideoTaskProgress.status === 'COMPLETED'"
              type="primary"
              @click="startNormalizeVideoTask"
              style="width: calc(100% - 20px); margin: 10px;"
            >
              1.视频规范任务
            </el-button>
            
            <!-- 视频规范任务进度条和取消按钮 -->
            <div v-else class="progress-container" style="padding: 10px;">
              <div style="display: flex; align-items: center; margin-bottom: 10px;">
                <el-progress :percentage="normalizeVideoTaskProgress.percentage" :status="getProgressStatus(normalizeVideoTaskProgress.status)" :show-text="false" style="flex: 1;" />
                <el-button v-if="normalizeVideoTaskProgress.status === 'RUNNING'" type="warning" @click="cancelNormalizeVideoTask" style="margin-left: 10px;">取消任务</el-button>
              </div>
              <p>{{ normalizeVideoTaskProgress.message }}</p>
              <p>{{ normalizeVideoTaskProgress.currentStep }}</p>
            </div>
            
            <!-- 初始化文件按钮 -->
            <el-button
              v-if="!scanTaskProgress || scanTaskProgress.status === 'COMPLETED' || scanTaskProgress.status === 'IDLE'"
              type="primary"
              @click="startScanTask"
              style="width: calc(100% - 20px); margin: 10px;"
            >
              2.初始化文件
            </el-button>
            
            <!-- 扫描任务进度条和取消按钮 -->
            <div v-else class="progress-container" style="padding: 10px;">
              <div style="display: flex; align-items: center; margin-bottom: 10px;">
                <el-progress :percentage="scanTaskProgress.percentage" :status="getProgressStatus(scanTaskProgress.status)" :show-text="false" style="flex: 1;" />
                <el-button v-if="scanTaskProgress.status === 'RUNNING'" type="warning" @click="cancelScanTask" style="margin-left: 10px;">取消任务</el-button>
              </div>
              <p>{{ scanTaskProgress.message }}</p>
              <p v-if="scanTaskProgress.currentFileName">正在处理: {{ scanTaskProgress.currentFileName }}</p>
              <p v-if="scanTaskProgress.processedFiles !== undefined && scanTaskProgress.totalFiles !== undefined">
                进度: {{ scanTaskProgress.processedFiles }}/{{ scanTaskProgress.totalFiles }}
              </p>
            </div>
            
            <!-- 启动移动任务按钮 -->
            <el-button
              v-if="!moveTaskProgress || moveTaskProgress.status === 'COMPLETED'"
              type="primary"
              @click="startMoveTask"
              style="width: calc(100% - 20px); margin: 10px;"
            >
              启动移动任务
            </el-button>
            
            <!-- 移动任务进度条和取消按钮 -->
            <div v-else class="progress-container" style="padding: 10px;">
              <div style="display: flex; align-items: center; margin-bottom: 10px;">
                <el-progress :percentage="moveTaskProgress.percentage" :status="getProgressStatus(moveTaskProgress.status)" :show-text="false" style="flex: 1;" />
                <el-button v-if="moveTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
              </div>
              <p>{{ moveTaskProgress.message }}</p>
              <p>{{ moveTaskProgress.currentStep }}</p>
            </div>
            
            <!-- 执行视频转码任务按钮 -->
            <el-button
              v-if="!transcodeTaskProgress || transcodeTaskProgress.status === 'COMPLETED'"
              type="primary"
              @click="startTranscodeTask"
              style="width: calc(100% - 20px); margin: 10px;"
            >
              执行视频转码任务
            </el-button>
            
            <!-- 转码任务进度条和取消按钮 -->
            <div v-else class="progress-container" style="padding: 10px;">
              <div style="display: flex; align-items: center; margin-bottom: 10px;">
                <el-progress :percentage="transcodeTaskProgress.percentage" :status="getProgressStatus(transcodeTaskProgress.status)" :show-text="false" style="flex: 1;" />
                <el-button v-if="transcodeTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
              </div>
              <p>{{ transcodeTaskProgress.message }}</p>
              <p>{{ transcodeTaskProgress.currentStep }}</p>
            </div>
            
            <!-- 生成缩略图任务按钮 -->
            <el-button
              v-if="!thumbnailTaskProgress || thumbnailTaskProgress.status === 'COMPLETED'"
              type="primary"
              @click="startThumbnailTask"
              style="width: calc(100% - 20px); margin: 10px;"
            >
              生成缩略图任务
            </el-button>
            
            <!-- 缩略图任务进度条和取消按钮 -->
            <div v-else class="progress-container" style="padding: 10px;">
              <div style="display: flex; align-items: center; margin-bottom: 10px;">
                <el-progress :percentage="thumbnailTaskProgress.percentage" :status="getProgressStatus(thumbnailTaskProgress.status)" :show-text="false" style="flex: 1;" />
                <el-button v-if="thumbnailTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
              </div>
              <p>{{ thumbnailTaskProgress.message }}</p>
              <p>{{ thumbnailTaskProgress.currentStep }}</p>
            </div>
            
            <!-- 删除任务按钮 -->
            <el-button
              v-if="!deleteTaskProgress || deleteTaskProgress.status === 'COMPLETED'"
              type="primary"
              @click="startDeleteTask"
              style="width: calc(100% - 20px); margin: 10px;"
            >
              删除任务
            </el-button>
            
            <!-- 删除任务进度条和取消按钮 -->
            <div v-else class="progress-container" style="padding: 10px;">
              <div style="display: flex; align-items: center; margin-bottom: 10px;">
                <el-progress :percentage="deleteTaskProgress.percentage" :status="getProgressStatus(deleteTaskProgress.status)" :show-text="false" style="flex: 1;" />
                <el-button v-if="deleteTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
              </div>
              <p>{{ deleteTaskProgress.message }}</p>
              <p>{{ deleteTaskProgress.currentStep }}</p>
            </div>
          </div>
        </el-collapse-item>
        
        <!-- 用户爱好模块 -->
        <el-collapse-item title="用户爱好" name="1">
          <el-form
            :model="userForm"
            :rules="formRules"
            label-width="30%"
            ref="formRef"
          >
            <el-form-item label="登录账号" prop="account">
              <el-input
                v-model="userForm.account"
                :maxlength="20"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickName">
              <el-input
                v-model="userForm.nickName"
                :maxlength="10"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="头像图片" prop="avatar">
              <el-input
                v-model="userForm.avatar"
                placeholder="请输入头像图片路径"
              />
            </el-form-item>
            <el-form-item label="缩略图阈值(字节)" prop="thumbnailThreshold">
              <el-input-number
                v-model="userForm.thumbnailThreshold"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="缩略图生成质量" prop="width">
              <el-input-number
                v-model="userForm.width"
                :min="1"
                :max="99"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="封面开始时间(秒)" prop="height">
              <el-input-number
                v-model="userForm.height"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="登录背景" prop="loginBackground">
              <el-input
                v-model="userForm.loginBackground"
                placeholder="请输入登录背景图片路径"
              />
            </el-form-item>
            <el-form-item label="主页背景" prop="homeBackground">
              <el-input
                v-model="userForm.homeBackground"
                placeholder="请输入主页背景图片路径"
              />
            </el-form-item>
          </el-form>
          <div style="text-align: right; padding: 10px">
            <el-button @click="cancelUserHobbySettings">取消</el-button>
            <el-button type="primary" @click="saveUserHobbySettings"
              >保存</el-button
            >
          </div>
        </el-collapse-item>

        <!-- 展示分类模块 -->
        <el-collapse-item title="展示分类" name="2">
          <el-form>
            <el-row :gutter="10" align="middle" style="margin-bottom: 15px;">
              <el-col :span="16">
                <el-form-item label="首页展示的分类" style="margin-bottom: 0;">
                  <el-tree-select
                    v-model="defaultDisplay"
                    :data="toolCategories"
                    placeholder="请选择首页展示的分类"
                    style="width: 100%"
                    node-key="toolId"
                    :props="{
                      label: 'toolName',
                      children: 'children',
                    }"
                    check-strictly
                    clearable
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8" style="text-align: right;">
                <el-button @click="cancelDisplayCategorySettings">取消</el-button>
                <el-button type="primary" @click="saveDisplayCategorySettings">保存</el-button>
              </el-col>
            </el-row>
            
            <el-row :gutter="10" align="middle">
              <el-col :span="16">
                <el-form-item label="首页默认加载条数" style="margin-bottom: 0;">
                  <el-input-number
                    v-model="defaultNumberPages"
                    :min="1"
                    :max="100"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8" style="text-align: right;">
                <el-button @click="cancelNumberPagesSettings">取消</el-button>
                <el-button type="primary" @click="saveNumberPagesSettings">保存</el-button>
              </el-col>
            </el-row>
          </el-form>
        </el-collapse-item>

        <!-- 视频压缩设置模块 -->
        <el-collapse-item title="视频压缩设置" name="3">
          <div style="margin-bottom: 10px">
            <el-button type="primary" @click="addPreset">添加预设</el-button>
          </div>

          <!-- 预设列表 -->
          <el-table :data="videoPresets" style="width: 100%" max-height="250" class="video-presets-table">
            <el-table-column prop="name" label="名称" width="100" />
            <el-table-column prop="video.resolution" label="分辨率" width="120" />
            <el-table-column prop="video.bitrate" label="码率" width="100" />
            <el-table-column prop="video.crf" label="CRF" width="60" />
            <el-table-column prop="video.codec" label="编码器" width="100" />
            <el-table-column label="操作" fixed="right" width="120">
              <template #default="scope">
                <div class="video-preset-actions">
                  <el-button size="small" @click="editPreset(scope.$index)"
                    >编辑</el-button
                  >
                  <el-button
                    size="small"
                    type="danger"
                    @click="deletePreset(scope.$index)"
                    >删除</el-button
                  >
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>

        <!-- 隐藏分类管理模块 -->
        <el-collapse-item title="隐藏分类管理" name="4">
          <el-form>
            <!-- 添加隐藏分类 -->
            <el-form-item label="添加隐藏分类">
              <el-tree-select
                v-model="selectedCategoryToAdd"
                :data="toolCategories"
                placeholder="请选择要隐藏的分类"
                style="width: 70%"
                node-key="toolId"
                :props="{
                  label: 'toolName',
                  children: 'children',
                }"
                check-strictly
                clearable
              />
              <el-button type="primary" @click="addHiddenCategory" style="margin-left: 10px">添加</el-button>
            </el-form-item>
            
            <!-- 已隐藏的分类列表 -->
            <el-form-item class="hidden-categories-table">
              <el-table :data="hiddenCategories" style="width: 100%" max-height="250">
                <el-table-column prop="hideId" label="分类ID" width="100" />
                <el-table-column label="操作" fixed="right" width="120">
                  <template #default="scope">
                    <div class="video-preset-actions">
                      <el-button size="small" type="danger" @click="deleteHiddenCategory(scope.row.id)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>
    </el-dialog>

    <!-- 预设编辑对话框 -->
    <el-dialog
      v-model="showPresetForm"
      :title="currentPreset !== null ? '编辑预设' : '添加预设'"
      width="500px"
    >
      <el-form :model="presetForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="presetForm.name" />
        </el-form-item>
        <el-form-item label="容器">
          <el-select
            v-model="presetForm.container"
            placeholder="请选择容器"
            style="width: 100%"
          >
            <el-option label="MP4" value="MP4" />
            <el-option label="MKV" value="MKV" />
            <el-option label="AVI" value="AVI" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码器">
          <el-select
            v-model="presetForm.video.codec"
            placeholder="请选择编码器"
            style="width: 100%"
          >
            <el-option label="libx264" value="libx264" />
            <el-option label="libx265" value="libx265" />
            <el-option label="libvpx-vp9" value="libvpx-vp9" />
          </el-select>
        </el-form-item>
        <el-form-item label="码率">
          <el-input
            v-model="presetForm.video.bitrate"
            :disabled="!!presetForm.video.crf"
            placeholder="例如: 8Mbps"
          />
        </el-form-item>
        <el-form-item label="CRF">
          <el-input
            v-model="presetForm.video.crf"
            :disabled="!!presetForm.video.bitrate"
            placeholder="0-51, 越小质量越好"
          />
        </el-form-item>
        <el-form-item label="分辨率">
          <el-input
            v-model="presetForm.video.resolution"
            placeholder="例如: 1920x1080"
          />
        </el-form-item>
        <el-form-item label="帧率">
          <el-input v-model="presetForm.video.framerate" />
        </el-form-item>
        <el-form-item label="速度">
          <el-input
            v-model="presetForm.video.speed"
            placeholder="例如: medium, slow"
          />
        </el-form-item>
        <!-- 音频设置 -->
        <el-form-item label="音频编码">
          <el-select
            v-model="presetForm.audio.codec"
            placeholder="请选择音频编码"
            style="width: 100%"
          >
            <el-option label="aac" value="aac" />
            <el-option label="mp3" value="mp3" />
            <el-option label="flac" value="flac" />
          </el-select>
        </el-form-item>
        <el-form-item label="音频码率">
          <el-input
            v-model="presetForm.audio.bitrate"
            placeholder="例如: 128k, 256k"
          />
        </el-form-item>
      </el-form>
      <div style="text-align: right; padding-top: 10px">
        <el-button @click="showPresetForm = false">取消</el-button>
        <el-button type="primary" @click="savePreset">保存</el-button>
      </div>
    </el-dialog>

    <!-- 主窗口 -->
    <el-row>
      <el-col :span="24" class="main-content">
        <router-view></router-view>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
/* 控制顶栏整体布局和样式 */
#home-top {
  height: 5vh; /* 高度为视口高度的5% */
  display: flex; /* 使用弹性布局 */
  align-items: center; /* 垂直居中对齐 */
  background: rgba(255, 255, 255, 0.5); /* 背景色：白色 透明度50% */
  backdrop-filter: blur(5px); /* 背景模糊效果：5px */
  z-index: 1000; /* 层级：1000 */
}

/* 控制顶栏中每个子元素的基本样式 */
.home-top-p {
  height: 100%; /* 高度占满父元素 */
  display: flex; /* 使用弹性布局 */
  align-items: center; /* 垂直居中对齐 */
  font-weight: bold; /* 字体加粗 */
  transition: box-shadow 0.3s ease; /* 过渡效果：阴影 0.3秒 缓动 */
  cursor: pointer; /* 鼠标悬停时显示手型光标 */
}

/* 控制鼠标悬停时顶栏子元素的样式 */
.home-top-p:hover {
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.5); /* 阴影效果：黑色 透明50%*/
}

/* 控制顶栏左侧"首页"文字的对齐方式 */
#home-top-1 {
  justify-content: flex-end; /* 水平靠右对齐 */
  padding-right: 5%; /* 右侧内边距：5% */
}

/* 控制顶栏中间图标的位置 */
#home-top-2 {
  justify-content: center; /* 水平居中对齐 */
}

/* 控制顶栏中间图标内部元素的居中对齐 */
#home-top-2 .el-icon {
  margin: auto; /* 自动外边距实现居中 */
}

/* 控制顶栏右侧"整理"文字的对齐方式 */
#home-top-4 {
  justify-content: flex-start; /* 水平靠左对齐 */
  padding-left: 5%; /* 左侧内边距：5% */
}

/* 控制昵称居中显示 */
.nickname-center {
  display: flex; /* 使用弹性布局 */
  justify-content: center; /* 水平居中对齐 */
  align-items: center; /* 垂直居中对齐 */
}

/* 控制顶栏右侧头像的对齐方式 */
#home-top-3 {
  justify-content: center; /* 水平居中对齐 */
}

/* 头像容器样式 */
.avatar-container {
  position: relative; /* 相对定位 */
  height: 100%; /* 高度占满父元素 */
  display: flex; /* 使用弹性布局 */
  align-items: center; /* 垂直居中对齐 */
  justify-content: center; /* 水平居中对齐 */
}

/* 控制头像图片的样式 */
.avatar-image {
  height: 100%; /* 高度占满父元素 */
  aspect-ratio: 1 / 1; /* 宽高比：1:1 */
  border-radius: 50%; /* 圆角：50%形成圆形 */
  object-fit: cover; /* 图片填充方式：覆盖 */
}

/* 头像菜单样式 */
.avatar-menu {
  position: absolute; /* 绝对定位 */
  top: 100%; /* 顶部位置：相对于父元素100% */
  right: 0; /* 右侧位置：0 */
  width: 150px; /* 宽度：150px */
  background: rgba(255, 255, 255, 0.9); /* 背景色：白色 透明度90% */
  backdrop-filter: blur(5px); /* 背景模糊效果：5px */
  border-radius: 5px; /* 圆角：5px */
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1); /* 阴影效果：黑色 透明10% */
  z-index: 1001; /* 层级：1001 */
  margin-top: 5px; /* 上外边距：5px */
}

/* 菜单列表样式 */
.menu-list {
  list-style: none; /* 列表样式：无 */
  padding: 0; /* 内边距：0 */
  margin: 0; /* 外边距：0 */
}

/* 菜单项样式 */
.menu-item {
  padding: 10px 15px; /* 内边距：上下10px 左右15px */
  cursor: pointer; /* 鼠标悬停时显示手型光标 */
  transition: background 0.3s ease; /* 过渡效果：背景 0.3秒 缓动 */
}

/* 菜单项悬停效果 */
.menu-item:hover {
  background: rgba(0, 0, 0, 0.05); /* 背景色：黑色 透明度5% */
}

/* 控制背景容器的定位和尺寸 */
.background-container {
  position: relative; /* 相对定位 */
  width: 100%; /* 宽度：100% */
  min-height: 100vh; /* 最小高度：视口高度的100% */
}

/* 控制背景图片的显示样式 */
.background {
  position: fixed; /* 固定定位 */
  top: 0; /* 顶部位置：0 */
  left: 0; /* 左侧位置：0 */
  width: 100%; /* 宽度：100% */
  height: 100%; /* 高度：100% */
  background-size: cover; /* 背景尺寸：覆盖 */
  background-position: center; /* 背景位置：居中 */
  background-repeat: no-repeat; /* 背景重复：不重复 */
  z-index: -1; /* 层级：-1 */
}

/* 控制主内容区域的样式 */
.main-content {
  min-height: 95vh; /* 最小高度：视口高度的95% */
  background: rgba(0, 0, 0, 0); /* 背景色：透明 */
  overflow-y: auto; /* 垂直滚动：自动 */
}

/* 视频压缩设置模块中的操作按钮样式 */
.video-preset-actions {
  display: flex; /* 使用弹性布局 */
  justify-content: center; /* 水平居中对齐 */
  gap: 5px; /* 元素间距：5px */
}

.video-preset-actions .el-button {
  border-radius: 4px; /* 圆角：4px */
  transition: all 0.3s ease; /* 过渡效果：所有属性 0.3秒 缓动 */
}

.video-preset-actions .el-button:hover {
  transform: translateY(-2px); /* 变换：垂直上移2px */
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2); /* 阴影效果：黑色 透明20% */
}

.video-preset-actions .el-button--danger {
  background-color: #ff4d4f; /* 背景色：红色 */
  border-color: #ff4d4f; /* 边框颜色：红色 */
}

.video-preset-actions .el-button--danger:hover {
  background-color: #ff7875; /* 背景色：浅红色 */
  border-color: #ff7875; /* 边框颜色：浅红色 */
}

/* 隐藏分类表格样式 */
.hidden-categories-table {
  margin-top: 20px; /* 上外边距：20px */
  border-radius: 8px; /* 圆角：8px */
  overflow: hidden; /* 溢出隐藏 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); /* 阴影效果：黑色 透明10% */
}

/* 表格整体样式 */
.hidden-categories-table :deep(.el-table) {
  border-radius: 8px; /* 圆角：8px */
  border: 1px solid #ebeef5; /* 边框：1px 实线 #ebeef5 */
}

/* 表头样式 */
.hidden-categories-table :deep(.el-table__header-wrapper) {
  background-color: #f5f7fa; /* 背景色：浅灰色 */
}

.hidden-categories-table :deep(.el-table__header th) {
  background-color: #f5f7fa; /* 背景色：浅灰色 */
  color: #606266; /* 文字颜色：深灰色 */
  font-weight: 600; /* 字体加粗 */
  transition: background-color 0.3s ease; /* 过渡效果：背景色 0.3秒 缓动 */
}

/* 表格行样式 */
.hidden-categories-table :deep(.el-table__row) {
  transition: background-color 0.3s ease; /* 过渡效果：背景色 0.3秒 缓动 */
}

.hidden-categories-table :deep(.el-table__row:hover) {
  background-color: #f5f7fa; /* 背景色：浅灰色 */
}

/* 操作按钮样式 */
.hidden-categories-table :deep(.el-button--danger) {
  background-color: #ff4d4f; /* 背景色：红色 */
  border-color: #ff4d4f; /* 边框颜色：红色 */
  transition: all 0.3s ease; /* 过渡效果：所有属性 0.3秒 缓动 */
}

.hidden-categories-table :deep(.el-button--danger:hover) {
  background-color: #ff7875; /* 背景色：浅红色 */
  border-color: #ff7875; /* 边框颜色：浅红色 */
  transform: translateY(-2px); /* 变换：垂直上移2px */
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2); /* 阴影效果：黑色 透明20% */
}

/* 视频预设表格样式 */
.video-presets-table {
  margin-top: 10px; /* 上外边距：10px */
  border-radius: 8px; /* 圆角：8px */
  overflow: hidden; /* 溢出隐藏 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); /* 阴影效果：黑色 透明10% */
}

/* 表格整体样式 */
.video-presets-table :deep(.el-table) {
  border-radius: 8px; /* 圆角：8px */
  border: 1px solid #ebeef5; /* 边框：1px 实线 #ebeef5 */
}

/* 表头样式 */
.video-presets-table :deep(.el-table__header-wrapper) {
  background-color: #f5f7fa; /* 背景色：浅灰色 */
}

.video-presets-table :deep(.el-table__header th) {
  background-color: #f5f7fa; /* 背景色：浅灰色 */
  color: #606266; /* 文字颜色：深灰色 */
  font-weight: 600; /* 字体加粗 */
  transition: background-color 0.3s ease; /* 过渡效果：背景色 0.3秒 缓动 */
}

/* 表格行样式 */
.video-presets-table :deep(.el-table__row) {
  transition: background-color 0.3s ease; /* 过渡效果：背景色 0.3秒 缓动 */
}

.video-presets-table :deep(.el-table__row:hover) {
  background-color: #f5f7fa; /* 背景色：浅灰色 */
}

/* 操作按钮样式 */
.video-presets-table :deep(.el-button) {
  transition: all 0.3s ease; /* 过渡效果：所有属性 0.3秒 缓动 */
}

.video-presets-table :deep(.el-button:hover) {
  transform: translateY(-2px); /* 变换：垂直上移2px */
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2); /* 阴影效果：黑色 透明20% */
}

.video-presets-table :deep(.el-button--danger) {
  background-color: #ff4d4f; /* 背景色：红色 */
  border-color: #ff4d4f; /* 边框颜色：红色 */
}

.video-presets-table :deep(.el-button--danger:hover) {
  background-color: #ff7875; /* 背景色：浅红色 */
  border-color: #ff7875; /* 边框颜色：浅红色 */
}
</style>
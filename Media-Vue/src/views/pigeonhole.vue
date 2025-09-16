<script setup>
import { ref, onMounted, computed, onUnmounted } from "vue";
import { getToolCategoryTree } from "@/utils/toolCategory";
import request from "@/utils/axiosRequest";
import { useRouter } from "vue-router";
import JSZip from "jszip";
import {
  Folder,
  FolderOpened,
  Picture,
  VideoPlay,
  ArrowLeftBold,
  Files,
  Document,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
// 添加节流优化悬停事件
import { debounce } from 'lodash';

// ... (你其他的 ref 和常量定义保持不变) ...
// 路由
const router = useRouter();

// 在setup函数中添加
const handleNodeHover = debounce((node, isHover) => {
  // 处理悬停逻辑
}, 16); // 约60fps
// 在其他 ref 声明附近添加这行
const isProcessing = ref(false);
// 跳转到整理页面
const goToFinishing = () => {
  router.push("/finishing");
};

// 分类数据
const categories = ref([]);
// 文件数据
const files = ref([]);
// 当前页码
const currentPage = ref(1);
// 每页大小
const pageSize = ref(50);
// 是否还有更多数据
const hasMoreData = ref(true);
// 当前展示的文件
const currentFile = ref(null);
// 修改后的文件名
const modifiedFileName = ref("");
// 文件URL前缀
const fileUrlPrefix = ref("http://10.10.10.101");
// 视频预设数据
const videoPresets = ref([]);
// 选中的预设
const selectedPreset = ref("");
// 待删除文件列表
const fileDeleteList = ref([]);
// Del键按下计数器
const delKeyPressCount = ref(0);
// Del键按下的定时器
const delKeyPressTimer = ref(null);
// 等待分类提交的文件列表
const pendingClassificationList = ref([]);
// 视频表单数据
const videoForm = ref({
  fileId: null,
  outputFileName: "",
  targetToolId: "",
  container: "",
  video: {
    codec: "",
    bitrate: "",
    resolution: "",
    framerate: "",
    crf: "",
    speed: "",
  },
  audio: {
    codec: "",
    bitrate: "",
  },
});

// 视频任务列表弹窗控制
const videoTasksDialogVisible = ref(false);
// 视频任务列表
const videoTasks = ref([]);

// 移动任务进度
const moveTaskProgress = ref(null);
// 转码任务进度
const transcodeTaskProgress = ref(null);
// 删除任务进度
const deleteTaskProgress = ref(null);
// 缩略图任务进度
const thumbnailTaskProgress = ref(null);
// 轮询定时器
const progressPollingTimer = ref(null);
// 转码任务轮询定时器
const transcodeProgressPollingTimer = ref(null);
// 删除任务轮询定时器
const deleteProgressPollingTimer = ref(null);
// 缩略图任务轮询定时器
const thumbnailProgressPollingTimer = ref(null);


// 获取分类数据
const fetchCategories = async () => {
  try {
    const res = await getToolCategoryTree();
    if (res.code === 200) {
      categories.value = res.data;
    }
  } catch (error) {
    console.error("获取分类数据失败:", error);
  }
};

// 获取文件列表
const fetchFiles = async (
  toolId = null,
  page = currentPage.value,
  size = pageSize.value,
  keyword = ""
) => {
  try {
    const res = await request({
      url: "/media/list",
      method: "GET",
      params: {
        toolId,
        page,
        pageSize: size,
        keyword,
      },
    });

    if (res.code === 200) {
      if (page === 1) {
        files.value = res.data.records;
      } else {
        files.value = [...files.value, ...res.data.records];
      }
      currentPage.value = page;
      pageSize.value = size;
      hasMoreData.value = res.data.records.length === size;

      if (page === 1 && res.data.records && res.data.records.length > 0) {
        currentFile.value = res.data.records[0];
        modifiedFileName.value = res.data.records[0].fileName;
        if (isCompressedFile(res.data.records[0])) {
          processCompressedFile(res.data.records[0]);
        }
      }
    }
  } catch (error) {
    console.error("获取文件列表失败:", error);
  }
};

// 检查并加载更多数据
const checkAndLoadMore = async () => {
  if (!hasMoreData.value) {
    return;
  }
  const remainingCount = files.value.length; // 直接使用当前列表长度判断
  if (remainingCount < 5) {
    await fetchFiles(null, currentPage.value + 1, pageSize.value);
  }
};

// 【核心修改】更新当前文件（在移除文件后）
const updateCurrentFileAfterRemoval = (removedIndex) => {
  // 列表已经移除了一个元素，所以：
  // 如果移除的不是最后一个，那么新列表在 `removedIndex` 位置的元素就是原来的下一个
  if (removedIndex < files.value.length) {
    currentFile.value = files.value[removedIndex];
  }
  // 如果移除的是最后一个，但列表不为空，则显示新的最后一个（即原来倒数第二个）
  else if (files.value.length > 0) {
    currentFile.value = files.value[files.value.length - 1];
  }
  // 如果列表空了
  else {
    currentFile.value = null;
  }

  // 更新文件名输入框
  modifiedFileName.value = currentFile.value ? currentFile.value.fileName : "";

  // 如果新的当前文件是压缩文件，处理其内容
  if (currentFile.value && isCompressedFile(currentFile.value)) {
    processCompressedFile(currentFile.value);
  }
};

// 【核心修改】添加文件到等待分类列表
const addToPendingClassification = (file, category) => {
  // 检查是否已存在
  const exists = pendingClassificationList.value.some(item => item.fileId === file.fileId);
  if (exists) {
    ElMessage.warning("文件已在等待分类列表中");
    return;
  }

  // 添加到等待分类列表
  const pendingItem = {
    fileId: file.fileId,
    toolId: category.toolId,
    rename: modifiedFileName.value !== file.fileName ? modifiedFileName.value : null,
    fileName: file.fileName,
    categoryName: category.toolName,
    originalFile: file // 保存原始文件信息用于还原
  };

  pendingClassificationList.value.push(pendingItem);
  savePendingClassificationList();

  // 从文件列表中查找并移除该文件
  const fileIndex = files.value.findIndex(f => f.fileId === file.fileId);
  if (fileIndex !== -1) {
    const isCurrentFile = currentFile.value && currentFile.value.fileId === file.fileId;
    
    // **关键：先从数组中移除**
    files.value.splice(fileIndex, 1);
    
    // **然后，如果移除的是当前文件，则更新视图**
    if (isCurrentFile) {
      updateCurrentFileAfterRemoval(fileIndex);
    }
  }

  ElMessage.success(`文件已添加到等待分类列表 (${pendingClassificationList.value.length}/10)`);

  // 检查是否达到10个，自动提交
  if (pendingClassificationList.value.length >= 10) {
    submitPendingClassifications();
  }

  // 检查是否需要加载更多数据
  checkAndLoadMore();
};

// 优化分类点击处理
const handleCategoryClick = (category) => {
  // 添加防重复点击
  if (isProcessing.value) return;
  
  if (currentFile.value) {
    isProcessing.value = true;
    addToPendingClassification(currentFile.value, category);
    setTimeout(() => {
      isProcessing.value = false;
    }, 300);
  } else {
    ElMessage.warning("当前没有选中的文件");
  }
};

// 提交等待分类的文件
const submitPendingClassifications = async () => {
  if (pendingClassificationList.value.length === 0) {
    ElMessage.info("没有等待分类的文件");
    return;
  }

  try {
    const submitData = pendingClassificationList.value.map(item => ({
      fileId: item.fileId,
      toolId: item.toolId,
      rename: item.rename
    }));

    const res = await request({
      url: "/media/move",
      method: "POST",
      data: submitData,
    });

    if (res.code === 200) {
      ElMessage.success(`成功提交 ${pendingClassificationList.value.length} 个文件的分类`);
      pendingClassificationList.value = [];
      savePendingClassificationList();
    } else {
      ElMessage.error("文件分类提交失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("文件分类提交异常: " + error.message);
  }
};

// 【新增】从等待分类列表中删除单个文件并还原
const removeFromPendingClassification = (index) => {
  const item = pendingClassificationList.value[index];

  // 还原文件到文件列表的开头
  if (item.originalFile) {
    files.value.unshift(item.originalFile);

    // 如果当前没有选中文件，则自动选中刚还原的文件
    if (!currentFile.value) {
      currentFile.value = item.originalFile;
      modifiedFileName.value = item.originalFile.fileName;
      if (isCompressedFile(item.originalFile)) {
        processCompressedFile(item.originalFile);
      }
    }
  }

  // 从等待分类列表中移除
  pendingClassificationList.value.splice(index, 1);
  savePendingClassificationList();

  ElMessage.success("文件已还原到待分类列表");
};

// 【新增】提交单个等待分类文件
const submitSinglePendingClassification = async (index) => {
  const item = pendingClassificationList.value[index];

  try {
    const submitData = [{
      fileId: item.fileId,
      toolId: item.toolId,
      rename: item.rename
    }];

    const res = await request({
      url: "/media/move",
      method: "POST",
      data: submitData,
    });

    if (res.code === 200) {
      ElMessage.success("文件分类提交成功");
      // 提交成功后从等待列表中移除
      pendingClassificationList.value.splice(index, 1);
      savePendingClassificationList();
    } else {
      ElMessage.error("文件分类提交失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("文件分类提交异常: " + error.message);
  }
};

// 保存和加载等待分类列表的函数
const savePendingClassificationList = () => {
  localStorage.setItem("pendingClassificationList", JSON.stringify(pendingClassificationList.value));
};

const loadPendingClassificationList = () => {
  const list = localStorage.getItem("pendingClassificationList");
  if (list) {
    try {
      pendingClassificationList.value = JSON.parse(list);
    } catch (e) {
      console.error("解析 pendingClassificationList 数据失败:", e);
      pendingClassificationList.value = [];
    }
  }
};

// ... (你其他的函数如 isImageFile, findLeafNodes, onMounted 等保持不变) ...

// 判断是否为图片文件
const isImageFile = (file) => {
  return file.mimeType && file.mimeType.startsWith("image/");
};

// 判断是否为视频文件
const isVideoFile = (file) => {
  return file.mimeType && file.mimeType.startsWith("video/");
};

// 判断是否为压缩文件
const isCompressedFile = (file) => {
  const compressedTypes = [
    "application/zip",
    "application/x-rar-compressed",
    "application/x-7z-compressed",
    "application/x-tar",
    "application/gzip"
  ];
  return file.mimeType && compressedTypes.includes(file.mimeType);
};

const findLeafNodes = (nodes, path = [], isRoot = true) => {
  let leafNodes = [];
  nodes.forEach((node) => {
    const currentPath = [
      ...path,
      { toolId: node.toolId, toolName: node.toolName },
    ];
    if (!node.children || node.children.length === 0) {
      const pathInfo = isRoot ? [] : currentPath.slice(0, -1);
      leafNodes.push({ ...node, pathInfo });
    } else {
      leafNodes = leafNodes.concat(findLeafNodes(node.children, currentPath, false));
    }
  });
  return leafNodes;
};

const groupedLeafNodes = computed(() => {
  const leafNodes = findLeafNodes(categories.value);
  const groups = {};
  leafNodes.forEach((node) => {
    if (!node.pathInfo || node.pathInfo.length === 0) {
      const parentPath = node.toolName;
      if (!groups[parentPath]) {
        groups[parentPath] = { parentPath: "", nodes: [] };
      }
      groups[parentPath].nodes.push(node);
    } else {
      if (node.pathInfo && node.pathInfo.length > 0) {
        const parentPath = node.pathInfo.map((item) => item.toolName).join("/");
        if (!groups[parentPath]) {
          groups[parentPath] = { parentPath, nodes: [] };
        }
        groups[parentPath].nodes.push(node);
      }
    }
  });
  return Object.values(groups);
});

const truncateString = (str, length) => {
  if (!str) return "";
  return str.length > length ? str.substring(0, length) + "..." : str;
};

const formatFileSize = (size) => {
  if (!size) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let unitIndex = 0;
  let fileSize = size;
  while (fileSize >= 1024 && unitIndex < units.length - 1) {
    fileSize /= 1024;
    unitIndex++;
  }
  return `${fileSize.toFixed(2)} ${units[unitIndex]}`;
};

const compressedFileContents = ref([]);

const isTextDecoderSupport = (encoding) => {
  try {
    new TextDecoder(encoding);
    return true;
  } catch (e) {
    return false;
  }
};

const processCompressedFile = async (file) => {
  try {
    compressedFileContents.value = [];
    const response = await fetch(fileUrlPrefix.value + file.fileUrl);
    const blob = await response.blob();
    const zip = new JSZip();
    let zipContent;
    const decodeOptions = [
      {},
      { decodeFileName: (bytes) => new TextDecoder('utf-8').decode(bytes) },
    ];
    if (isTextDecoderSupport('gbk')) {
      decodeOptions.push({
        decodeFileName: (bytes) => {
          try {
            return new TextDecoder('gbk').decode(bytes);
          } catch (e) {
            return new TextDecoder('utf-8').decode(bytes);
          }
        }
      });
    }
    let lastError;
    for (const options of decodeOptions) {
      try {
        zipContent = await zip.loadAsync(blob, options);
        lastError = null;
        break;
      } catch (error) {
        lastError = error;
      }
    }
    if (lastError) {
      throw lastError;
    }
    const fileList = [];
    zipContent.forEach((relativePath, zipEntry) => {
      fileList.push({
        name: zipEntry.name,
        dir: zipEntry.dir,
        date: zipEntry.date,
        size: zipEntry.dir ? 0 : zipEntry._data.uncompressedSize
      });
    });
    compressedFileContents.value = fileList;
  } catch (error) {
    console.error("处理压缩文件时出错:", error);
    ElMessage.error("处理压缩文件时出错: " + error.message);
  }
};

onMounted(() => {
  fetchCategories();
  fetchFiles(null, 1, pageSize.value);
  const presets = localStorage.getItem("videoPresets");
  if (presets) {
    try {
      videoPresets.value = JSON.parse(presets);
    } catch (e) {
      console.error("解析 videoPresets 数据失败:", e);
    }
  }
  loadVideoTasks();
  loadFileDeleteList();
  // 挂载时加载等待分类列表
  loadPendingClassificationList();
  window.addEventListener('keydown', handleKeyDown);
});

const handleKeyDown = async (event) => {
  if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
    return;
  }
  
  if (event.key === 'Delete' && currentFile.value) {
    delKeyPressCount.value++;
    
    if (delKeyPressCount.value === 1) {
      ElMessage.info('再次按下 Delete 键确认删除');
      
      if (delKeyPressTimer.value) {
        clearTimeout(delKeyPressTimer.value);
      }
      
      delKeyPressTimer.value = setTimeout(() => {
        delKeyPressCount.value = 0;
      }, 1500);
    } else if (delKeyPressCount.value === 2) {
      if (delKeyPressTimer.value) {
        clearTimeout(delKeyPressTimer.value);
        delKeyPressTimer.value = null;
      }
      delKeyPressCount.value = 0;
      
      const fileToDelete = currentFile.value;
      const success = await deleteFiles([fileToDelete.fileId]);
      
      if (success) {
        ElMessage.success("文件已提交删除任务");
        
        setTimeout(() => {
          startDeleteTask();
        }, 500);
        
        const fileIndex = files.value.findIndex(file => file.fileId === fileToDelete.fileId);
        if (fileIndex !== -1) {
          // **关键：先从数组移除，再更新视图**
          files.value.splice(fileIndex, 1);
          updateCurrentFileAfterRemoval(fileIndex);
        }
      }
    }
  }
};

const loadFileDeleteList = () => {
  const list = localStorage.getItem("fileDeleteList");
  if (list) {
    try {
      fileDeleteList.value = JSON.parse(list);
    } catch (e) {
      console.error("解析 fileDeleteList 数据失败:", e);
      fileDeleteList.value = [];
    }
  }
};

const saveFileDeleteList = () => {
  localStorage.setItem("fileDeleteList", JSON.stringify(fileDeleteList.value));
};

const deleteFiles = async (fileIds) => {
  try {
    const res = await request({
      url: "/media/delete",
      method: "POST",
      data: fileIds,
    });
    
    if (res.code === 200) {
      // 在这里不再显示 ElMessage.success("文件删除成功")，由提交任务的逻辑统一提示
      return true;
    } else {
      ElMessage.error("文件删除失败: " + res.message);
      return false;
    }
  } catch (error) {
    ElMessage.error("文件删除异常: " + error.message);
    return false;
  }
};

const removeFromFileDeleteList = (index) => {
  fileDeleteList.value.splice(index, 1);
  saveFileDeleteList();
  ElMessage.success("已从待删除列表中移除");
};

const clearFileDeleteList = () => {
  fileDeleteList.value = [];
  saveFileDeleteList();
  ElMessage.success("已清空待删除列表");
};

const submitAllDeleteTasks = async () => {
  if (fileDeleteList.value.length === 0) {
    ElMessage.info("没有待删除的文件");
    return;
  }
  
  const fileIds = fileDeleteList.value.map(file => file.fileId);
  const success = await deleteFiles(fileIds);
  
  if (success) {
    fileDeleteList.value = [];
    saveFileDeleteList();
  }
};

const formatFileSizeForTable = (row, column, cellValue) => {
  return formatFileSize(cellValue);
};

const applyPreset = (presetName) => {
  const preset = videoPresets.value.find((p) => p.name === presetName);
  if (preset) {
    videoForm.value.container = preset.container || "";
    videoForm.value.video.codec = preset.video?.codec || "";
    videoForm.value.video.bitrate = preset.video?.bitrate || "";
    videoForm.value.video.resolution = preset.video?.resolution || "";
    videoForm.value.video.framerate = preset.video?.framerate || "";
    videoForm.value.video.crf = preset.video?.crf || "";
    videoForm.value.video.speed = preset.video?.speed || "";
    videoForm.value.audio.codec = preset.audio?.codec || "";
    videoForm.value.audio.bitrate = preset.audio?.bitrate || "";
  }
};

const submitTranscode = () => {
  if (!currentFile.value) {
    ElMessage.error("当前没有选中的文件");
    return;
  }
  
  videoForm.value.fileId = currentFile.value.fileId;
  
  if (videoForm.value.video.crf) {
    videoForm.value.video.bitrate = null;
  }
  
  const existingTasks = JSON.parse(localStorage.getItem("videoTasks") || "[]");
  
  const newTask = {
    ...videoForm.value,
    fileName: currentFile.value.fileName,
    taskId: Date.now()
  };
  
  const updatedTasks = [...existingTasks, newTask];
  localStorage.setItem("videoTasks", JSON.stringify(updatedTasks));
  ElMessage.success("视频转码任务已添加到待处理列表");
};

const loadVideoTasks = () => {
  const tasks = JSON.parse(localStorage.getItem("videoTasks") || "[]");
  videoTasks.value = tasks;
};

const showVideoTasks = () => {
  loadVideoTasks(); // 每次打开都重新加载
  videoTasksDialogVisible.value = true;
};

const deleteTask = (index) => {
  videoTasks.value.splice(index, 1);
  localStorage.setItem("videoTasks", JSON.stringify(videoTasks.value));
  ElMessage.success("任务已删除");
};

const submitAllTasks = async () => {
  if (videoTasks.value.length === 0) {
    ElMessage.info("没有待提交的任务");
    return;
  }
  
  let successCount = 0;
  let failCount = 0;
  
  for (let i = videoTasks.value.length - 1; i >= 0; i--) {
    try {
      const res = await request({
        url: "/media/transcode",
        method: "POST",
        data: videoTasks.value[i],
      });
      
      if (res.code === 200) {
        videoTasks.value.splice(i, 1);
        successCount++;
      } else {
        failCount++;
        console.error("任务提交失败:", res.message);
      }
    } catch (error) {
      failCount++;
      console.error("任务提交异常:", error.message);
    }
  }
  
  localStorage.setItem("videoTasks", JSON.stringify(videoTasks.value));
  
  if (failCount === 0) {
    ElMessage.success(`成功提交 ${successCount} 个任务`);
  } else {
    ElMessage.warning(`提交完成，成功 ${successCount} 个，失败 ${failCount} 个`);
  }
};

const submitSingleTask = async (index) => {
  try {
    const res = await request({
      url: "/media/transcode",
      method: "POST",
      data: videoTasks.value[index],
    });
    
    if (res.code === 200) {
      videoTasks.value.splice(index, 1);
      localStorage.setItem("videoTasks", JSON.stringify(videoTasks.value));
      ElMessage.success("任务提交成功");
    } else {
      ElMessage.error("任务提交失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("任务提交异常: " + error.message);
  }
};

const updateFileName = () => {
  if (!modifiedFileName.value) {
    ElMessage.warning("请输入新的文件名");
    return;
  }
  ElMessage.success("文件名已更新，将在移动文件时生效");
};

const startMoveTask = async () => {
  try {
    const res = await request({ url: "/task/start?type=MOVE", method: "POST" });
    if (res.code === 200) {
      ElMessage.success("移动任务已启动");
      moveTaskProgress.value = { status: "RUNNING", percentage: 0, message: "", currentStep: "" };
      startProgressPolling();
    } else {
      ElMessage.error("启动移动任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动移动任务异常: " + error.message);
  }
};

const startTranscodeTask = async () => {
  try {
    const res = await request({ url: "/task/start?type=TRANSCODE", method: "POST" });
    if (res.code === 200) {
      ElMessage.success("视频转码任务已启动");
      transcodeTaskProgress.value = { status: "RUNNING", percentage: 0, message: "", currentStep: "" };
      startTranscodeProgressPolling();
    } else {
      ElMessage.error("启动视频转码任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动视频转码任务异常: " + error.message);
  }
};

const startDeleteTask = async () => {
  try {
    const res = await request({ url: "/task/start?type=DELETE", method: "POST" });
    if (res.code === 200) {
      ElMessage.success("删除任务已启动");
      deleteTaskProgress.value = { status: "RUNNING", percentage: 0, message: "", currentStep: "" };
      startDeleteProgressPolling();
    } else {
      ElMessage.error("启动删除任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动删除任务异常: " + error.message);
  }
};

const startThumbnailTask = async () => {
  try {
    const res = await request({ url: "/task/start?type=THUMBNAIL", method: "POST" });
    if (res.code === 200) {
      ElMessage.success("缩略图任务已启动");
      thumbnailTaskProgress.value = { status: "RUNNING", percentage: 0, message: "", currentStep: "" };
      startThumbnailProgressPolling();
    } else {
      ElMessage.error("启动缩略图任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("启动缩略图任务异常: " + error.message);
  }
};

// ... (所有轮询函数和 onUnmounted 保持不变)
const startTranscodeProgressPolling = () => {
  if (transcodeProgressPollingTimer.value) clearInterval(transcodeProgressPollingTimer.value);
  transcodeProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({ url: "/task/progress", method: "GET" });
      if (res.code === 200) {
        transcodeTaskProgress.value = res.data;
        if (["COMPLETED", "FAILED", "CANCELED"].includes(res.data.status)) {
          clearInterval(transcodeProgressPollingTimer.value);
          transcodeProgressPollingTimer.value = null;
        }
      }
    } catch (error) { console.error("获取转码任务进度异常:", error.message); }
  }, 2000);
};
const startDeleteProgressPolling = () => {
  if (deleteProgressPollingTimer.value) clearInterval(deleteProgressPollingTimer.value);
  deleteProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({ url: "/task/progress", method: "GET" });
      if (res.code === 200) {
        deleteTaskProgress.value = res.data;
        if (["COMPLETED", "FAILED", "CANCELED"].includes(res.data.status)) {
          clearInterval(deleteProgressPollingTimer.value);
          deleteProgressPollingTimer.value = null;
        }
      }
    } catch (error) { console.error("获取删除任务进度异常:", error.message); }
  }, 2000);
};
const startThumbnailProgressPolling = () => {
  if (thumbnailProgressPollingTimer.value) clearInterval(thumbnailProgressPollingTimer.value);
  thumbnailProgressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({ url: "/task/progress", method: "GET" });
      if (res.code === 200) {
        thumbnailTaskProgress.value = res.data;
        if (["COMPLETED", "FAILED", "CANCELED"].includes(res.data.status)) {
          clearInterval(thumbnailProgressPollingTimer.value);
          thumbnailProgressPollingTimer.value = null;
        }
      }
    } catch (error) { console.error("获取缩略图任务进度异常:", error.message); }
  }, 2000);
};
const startProgressPolling = () => {
  if (progressPollingTimer.value) clearInterval(progressPollingTimer.value);
  progressPollingTimer.value = setInterval(async () => {
    try {
      const res = await request({ url: "/task/progress", method: "GET" });
      if (res.code === 200) {
        moveTaskProgress.value = res.data;
        if (["COMPLETED", "FAILED", "CANCELED"].includes(res.data.status)) {
          clearInterval(progressPollingTimer.value);
          progressPollingTimer.value = null;
        }
      }
    } catch (error) { console.error("获取任务进度异常:", error.message); }
  }, 2000);
};

const getProgressStatus = (status) => {
  switch (status) {
    case "COMPLETED": return "success";
    case "FAILED": case "CANCELED": return "exception";
    default: return null;
  }
};

const cancelMoveTask = async () => {
  try {
    const res = await request({ url: "/task/cancel", method: "POST" });
    if (res.code === 200) {
      ElMessage.success("取消请求已发送");
    } else {
      ElMessage.error("取消任务失败: " + res.message);
    }
  } catch (error) {
    ElMessage.error("取消任务异常: " + error.message);
  }
};

onUnmounted(() => {
  if (progressPollingTimer.value) clearInterval(progressPollingTimer.value);
  if (transcodeProgressPollingTimer.value) clearInterval(transcodeProgressPollingTimer.value);
  if (deleteProgressPollingTimer.value) clearInterval(deleteProgressPollingTimer.value);
  if (thumbnailProgressPollingTimer.value) clearInterval(thumbnailProgressPollingTimer.value);
  window.removeEventListener('keydown', handleKeyDown);
  if (delKeyPressTimer.value) clearTimeout(delKeyPressTimer.value);
});

</script>
<template>
  <!-- 顶部信息显示区域 -->
  <div class="top-info-bar" v-if="currentFile">
    <el-icon class="back-icon" @click="goToFinishing"><ArrowLeftBold /></el-icon>
    <p><strong>文件名:</strong> {{ currentFile.fileName }} | <strong>文件大小:</strong> {{ formatFileSize(currentFile.fileSize) }} | <span v-if="isImageFile(currentFile) || isVideoFile(currentFile)"><strong>尺寸:</strong> {{ currentFile.width }} × {{ currentFile.height }}</span></p>
  </div>
  <el-row class="full-screen">
    <!-- 展示窗口 -->
    <el-col :span="18">
      <div
        v-if="currentFile"
        class="file-display-container"
      >
        
        <div v-if="isCompressedFile(currentFile)" class="compressed-file-container">
          <div
            class="compressed-file-left"
          >
            <el-icon class="file-icon"><Files /></el-icon>
            <div class="file-name">{{ currentFile.fileName }}</div>
          </div>
          
          <div class="compressed-file-right">
            <div class="compressed-content-header">
              <h3>压缩包内容</h3>
            </div>
            <div class="compressed-content-list">
              <el-table :data="compressedFileContents" style="width: 100%" height="100%">
                <el-table-column prop="name" label="文件名">
                  <template #default="scope">
                    <span :class="{ 'folder-name': scope.row.dir }">
                      {{ scope.row.name }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="size" label="大小" :formatter="formatFileSizeForTable" width="100"></el-table-column>
                <el-table-column prop="date" label="修改日期" width="150">
                  <template #default="scope">
                    {{ scope.row.date ? scope.row.date.toLocaleString() : '' }}
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
        
        <img
          v-else-if="isImageFile(currentFile)"
          :src="fileUrlPrefix + currentFile.fileUrl"
          :alt="currentFile.fileName"
          class="file-image"
        />
        <!-- <video
          v-else-if="isVideoFile(currentFile)"
          :src="fileUrlPrefix + currentFile.fileUrl"
          controls
          class="file-video"
          draggable="true"
          @dragstart="handleDragStart($event, currentFile)"
          @dragend="handleDragEnd"
        ></video> -->
        <!-- 视频文件 -->
        <video
          v-else-if="isVideoFile(currentFile)"
          :key="currentFile.fileId"
          :src="fileUrlPrefix + currentFile.fileUrl"
          controls
          autoplay
          muted
          loop
          class="file-video"
        ></video>
        <div
          v-else
          class="file-other"
        >
          <el-icon class="file-icon"><Document /></el-icon>
          <div class="file-name">{{ currentFile.fileName }}</div>
        </div>
      </div>
      <div v-else class="no-file">暂无文件</div>
    </el-col>
    <!-- 分类窗口 -->
    <el-col :span="6">
      <div class="upper-div">
        <div
          v-for="group in groupedLeafNodes"
          :key="group.parentPath || 'root'"
          class="category-group"
        >
          <div v-if="group.parentPath" class="parent-path">
            {{ group.parentPath }}
          </div>
          <div class="children-nodes">
            <div
              v-for="node in group.nodes"
              :key="node.toolId"
              class="node-item"
              @click="handleCategoryClick(node)"
            >
              <div class="node-content">
                <div class="node-content-inner">
                  <div class="cover-image">
                    <img
                      v-if="node.coverImageUrl"
                      :src="node.coverImageUrl"
                      :alt="node.toolName"
                    />
                    <el-icon v-else><Folder /></el-icon>
                  </div>
                  <div class="node-name">
                    {{ truncateString(node.toolName, 3) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
          <hr />
        </div>
      </div>
      <div class="middle-div">
        <el-button
          type="primary"
          @click="showVideoTasks"
          style="width: 100%; margin-top: 10px;"
        >
          查看待处理任务
        </el-button>
      </div>
      <div class="lower-div" style="display: none;">
        <!-- ... (此部分保持不变) ... -->
      </div>
    </el-col>
  </el-row>
  
  <!-- 【模板修改】视频任务列表弹窗，添加了新模块 -->
  <el-dialog v-model="videoTasksDialogVisible" title="代处理任务" width="600px">
    
    <!-- 【新增】等待分类提交模块 -->
    <el-card class="task-module" v-if="pendingClassificationList.length > 0">
      <template #header>
        <div class="card-header">
          <span>等待分类提交 ({{ pendingClassificationList.length }}/10)</span>
        </div>
      </template>
      <el-table :data="pendingClassificationList" style="width: 100%" max-height="300">
        <el-table-column prop="fileName" label="文件名" min-width="150"></el-table-column>
        <el-table-column prop="categoryName" label="分类名" width="120"></el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" @click="submitSinglePendingClassification(scope.$index)">提交</el-button>
            <el-button size="small" type="danger" @click="removeFromPendingClassification(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 15px; text-align: right;">
        <el-button type="primary" @click="submitPendingClassifications">全部提交</el-button>
      </div>
    </el-card>
    
    <!-- 文件任务模块 -->
    <el-card class="task-module" :style="{ marginTop: pendingClassificationList.length > 0 ? '20px' : '0' }">
        <!-- ... (文件任务模块内部不变) ... -->
        <template #header>
            <div class="card-header">
            <span>文件任务</span>
            </div>
        </template>
        <div class="task-content">
            <el-button v-if="!moveTaskProgress || moveTaskProgress.status === 'COMPLETED'" type="primary" @click="startMoveTask" style="width: calc(100% - 20px); margin: 10px;">
                启动移动任务
            </el-button>
            <div v-else class="progress-container" style="padding: 10px;">
                <div style="display: flex; align-items: center; margin-bottom: 10px;">
                    <el-progress :percentage="moveTaskProgress.percentage" :status="getProgressStatus(moveTaskProgress.status)" :show-text="false" style="flex: 1;" />
                    <el-button v-if="moveTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
                </div>
                <p>{{ moveTaskProgress.message }}</p>
                <p>{{ moveTaskProgress.currentStep }}</p>
            </div>
            
            <el-button v-if="!transcodeTaskProgress || transcodeTaskProgress.status === 'COMPLETED'" type="primary" @click="startTranscodeTask" style="width: calc(100% - 20px); margin: 10px;">
                执行视频转码任务
            </el-button>
            <div v-else class="progress-container" style="padding: 10px;">
                <div style="display: flex; align-items: center; margin-bottom: 10px;">
                    <el-progress :percentage="transcodeTaskProgress.percentage" :status="getProgressStatus(transcodeTaskProgress.status)" :show-text="false" style="flex: 1;" />
                    <el-button v-if="transcodeTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
                </div>
                <p>{{ transcodeTaskProgress.message }}</p>
                <p>{{ transcodeTaskProgress.currentStep }}</p>
            </div>
            
            <el-button v-if="!deleteTaskProgress || deleteTaskProgress.status === 'COMPLETED'" type="primary" @click="startDeleteTask" style="width: calc(100% - 20px); margin: 10px;">
                删除任务
            </el-button>
            <div v-else class="progress-container" style="padding: 10px;">
                <div style="display: flex; align-items: center; margin-bottom: 10px;">
                    <el-progress :percentage="deleteTaskProgress.percentage" :status="getProgressStatus(deleteTaskProgress.status)" :show-text="false" style="flex: 1;" />
                    <el-button v-if="deleteTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
                </div>
                <p>{{ deleteTaskProgress.message }}</p>
                <p>{{ deleteTaskProgress.currentStep }}</p>
            </div>
            
            <el-button v-if="!thumbnailTaskProgress || thumbnailTaskProgress.status === 'COMPLETED'" type="primary" @click="startThumbnailTask" style="width: calc(100% - 20px); margin: 10px;">
                生成缩略图任务
            </el-button>
            <div v-else class="progress-container" style="padding: 10px;">
                <div style="display: flex; align-items: center; margin-bottom: 10px;">
                    <el-progress :percentage="thumbnailTaskProgress.percentage" :status="getProgressStatus(thumbnailTaskProgress.status)" :show-text="false" style="flex: 1;" />
                    <el-button v-if="thumbnailTaskProgress.status === 'RUNNING'" type="warning" @click="cancelMoveTask" style="margin-left: 10px;">取消任务</el-button>
                </div>
                <p>{{ thumbnailTaskProgress.message }}</p>
                <p>{{ thumbnailTaskProgress.currentStep }}</p>
            </div>
        </div>
    </el-card>
    
    <!-- 视频任务模块 -->
    <el-card class="task-module" style="margin-top: 20px;">
      <!-- ... (视频任务模块内部不变) ... -->
        <template #header>
            <div class="card-header">
            <span>视频任务</span>
            </div>
        </template>
        <el-table :data="videoTasks" style="width: 100%" max-height="400">
            <el-table-column prop="fileName" label="文件名" min-width="150"></el-table-column>
            <el-table-column prop="container" label="容器格式" width="100"></el-table-column>
            <el-table-column prop="video.codec" label="视频编码" width="100"></el-table-column>
            <el-table-column prop="audio.codec" label="音频编码" width="100"></el-table-column>
            <el-table-column label="操作" width="150">
            <template #default="scope">
                <el-button size="small" type="primary" @click="submitSingleTask(scope.$index)">提交</el-button>
                <el-button size="small" type="danger" @click="deleteTask(scope.$index)">删除</el-button>
            </template>
            </el-table-column>
        </el-table>
        <div style="margin-top: 20px; text-align: right;">
            <el-button @click="videoTasksDialogVisible = false">关闭</el-button>
            <el-button type="primary" @click="submitAllTasks">全部提交</el-button>
        </div>
    </el-card>

  </el-dialog>
</template>

<style scoped>
/* 设置全屏样式 */
.full-screen {
  width: 100vw; /* 设置全屏宽度 */
}
/* 设置左边文件显示区域 */
.full-screen > .el-col {
  height: 95vh;
}
/* 设置上部分区域样式 */
.upper-div {
  height: 85vh; /* 设置上部分区域高度 */
  overflow-y: auto; /* 允许垂直滚动 */
}
/* 设置中间文件显示区域样式 */
.middle-div {
  height: 5vh; /* 设置中部分区域高度 */
}
/* 设置下部分区域样式 */
/*展示禁用*/
.lower-div {
  height: 5vh; /* 设置下部分区域高度 */
  padding: 15px; /* 添加内边距 */
  box-sizing: border-box; /* 包含内边距和边框 */
  overflow-y: auto; /* 允许垂直滚动 */
  background-color: #f9f9f9; /* 设置背景色 */
  border-top: 1px solid #e0e0e0; /* 添加顶部边框 */
}

/* 文件详情样式 */
.file-details {
  background-color: white;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.file-details h3 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #333;
  font-size: 18px;
}

.file-details p {
  margin: 8px 0;
  color: #666;
  font-size: 14px;
}

.file-details strong {
  color: #333;
  margin-right: 5px;
}

/* 设置分类组样式 */
.category-group {
  margin-bottom: 20px; /* 设置分类组底部外边距 */
}

/* 设置父类路径样式 */
.parent-path {
  font-weight: bold; /* 设置字体加粗 */
  margin-bottom: 5px; /* 设置底部外边距 */
  color: #999; /* 设置浅色字体 */
  font-size: 0.7vw; /* 缩小字体 */
}

/* 设置子节点容器样式 */
.children-nodes {
  display: flex; /* 使用弹性布局 */
  flex-wrap: wrap; /* 允许换行 */
  gap: 10px; /* 设置元素间距 */
}

/* 添加硬件加速和优化过渡效果 */
.node-item {
  width: calc(20% - 10px);
  cursor: pointer;
  /* 启用硬件加速 */
  will-change: transform;
  /* 使用transform3d触发GPU加速 */
  transform: translate3d(0, 0, 0);
  /* 减少过渡时间，提高响应性 */
  transition: transform 0.15s ease-out;
}

.node-item:hover .node-content {
  /* 使用transform3d替代scale */
  transform: translate3d(0, 0, 0) scale(1.02);
  /* 移除box-shadow动画，减少重绘 */
  /* box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15); */
  /* 添加更轻量的效果 */
  filter: brightness(1.05);
}

.node-content {
  width: 100%;
  padding-top: 100%;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  background-color: #f5f5f5;
  /* 优化过渡效果 */
  transition: filter 0.15s ease-out, transform 0.15s ease-out;
  /* 减少重绘 */
  backface-visibility: hidden;
}
/* 设置节点内容内部样式 */
.node-content-inner {
  position: absolute; /* 设置绝对定位 */
  top: 0; /* 设置顶部位置 */
  left: 0; /* 设置左侧位置 */
  width: 100%; /* 设置宽度 */
  height: 100%; /* 设置高度 */
  display: flex; /* 使用弹性布局 */
  flex-direction: column; /* 设置垂直排列 */
  align-items: center; /* 设置水平居中 */
  justify-content: center; /* 设置垂直居中 */
  text-align: center; /* 设置文字居中 */
}

/* 设置封面图片容器样式 */
.cover-image {
  width: 70%; /* 设置宽度 */
  height: 70%; /* 设置高度 */
  display: flex; /* 使用弹性布局 */
  align-items: center; /* 设置水平居中 */
  justify-content: center; /* 设置垂直居中 */
  margin-bottom: 5px; /* 设置底部外边距 */
}

/* 设置封面图片样式 */
.cover-image img {
  width: 100%; /* 设置宽度 */
  height: 100%; /* 设置高度 */
  object-fit: cover; /* 设置图片缩放 */
}

/* 设置默认图标样式 */
.cover-image .el-icon {
  font-size: 40px; /* 设置图标大小 */
  color: #999; /* 设置浅色 */
}

/* 设置节点名称样式 */
.node-name {
  font-size: 0.8vw; /* 设置字体大小 */
  color: #333; /* 设置字体颜色 */
  padding: 0 5px; /* 设置左右内边距 */
  margin-top: auto; /* 设置顶部自动外边距 */
  margin-bottom: 5px; /* 设置底部外边距 */
  white-space: nowrap; /* 设置不换行 */
  overflow: hidden; /* 隐藏溢出内容 */
  text-overflow: ellipsis; /* 设置省略号 */
}

/* 文件展示容器样式 */
.file-display-container {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  margin: 0 15px; /* 添加左右15px间距 */
  box-sizing: border-box;
  overflow: hidden; /* 防止内容溢出 */
  position: relative; /* 为文件详情信息提供定位上下文 */
}

/* 文件详情覆盖层样式 */
.file-details-overlay {
  position: absolute;
  top: 10px;
  left: 10px;
  background-color: rgba(0, 0, 0, 0.7); /* 黑色半透明背景 */
  color: white; /* 白色字体 */
  padding: 10px;
  border-radius: 5px;
  z-index: 10; /* 确保在文件内容之上 */
  max-width: 300px; /* 限制最大宽度 */
}

.file-details-overlay p {
  margin: 5px 0;
  font-size: 14px;
}

.file-details-overlay strong {
  margin-right: 5px;
}

.file-image,
.file-video {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain; /* 保持比例缩放 */
}

/* 其他文件类型样式 */
.file-other {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.file-icon {
  font-size: 80px;
  color: #999;
  margin-bottom: 20px;
}

.file-name {
  font-size: 16px;
  color: #333;
}

/* 无文件提示样式 */
.no-file {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 18px;
  color: #999;
}

/* 视频设置模块样式 */
.video-settings {
  background-color: white;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-top: 20px;
}

.video-settings h3 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #333;
  font-size: 18px;
}

.video-settings .el-form-item {
  margin-bottom: 12px;
}

.video-settings .el-form-item__label {
  font-size: 14px;
  color: #666;
}

.video-settings .el-input__inner {
  font-size: 14px;
}

.video-settings .el-button {
  width: 100%;
}



/* 顶部信息显示区域样式 */
.top-info-bar {
  height: 5vh;
  display: flex;
  align-items: center;
  padding: 0 20px;
  background-color: #f5f5f5;
  border-bottom: 1px solid #e0e0e0;
  box-sizing: border-box;
}

.top-info-bar p {
  margin: 0;
  font-size: 14px;
  color: #666;
}

.top-info-bar strong {
  color: #333;
  margin-right: 5px;
}

.back-icon {
  font-size: 24px;
  color: #333;
  margin-right: 10px;
  cursor: pointer;
}


/* 压缩文件容器样式 */
.compressed-file-container {
  display: flex;
  width: 100%;
  height: 100%;
}

/* 压缩文件左侧样式 */
.compressed-file-left {
  width: 30%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #e0e0e0;
  background-color: #f9f9f9;
}

.compressed-file-left .file-icon {
  font-size: 80px;
  color: #409eff;
  margin-bottom: 20px;
}

.compressed-file-left .file-name {
  font-size: 16px;
  color: #333;
  text-align: center;
  padding: 0 10px;
}

/* 压缩文件右侧样式 */
.compressed-file-right {
  width: 70%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.compressed-content-header {
  padding: 10px;
  border-bottom: 1px solid #e0e0e0;
  background-color: #f5f5f5;
}

.compressed-content-header h3 {
  margin: 0;
  color: #333;
}

.compressed-content-list {
  flex: 1;
  overflow: auto;
}

/* 文件夹名称样式 */
.folder-name {
  font-weight: bold;
}

/* 任务模块样式 */
.task-module {
  margin-bottom: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
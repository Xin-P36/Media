<script setup>
import { ref, onMounted, onUnmounted, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowUp,
  ArrowDown,
  ArrowLeft,
  Download,
  Document,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import request from "@/utils/axiosRequest";

// --- 响应式数据定义 ---
const mediaList = ref([]);
const fileUrlPrefix = ref("http://10.10.10.101");
const currentPage = ref(1);
const pageSize = ref(50); // 保留一个默认值
const hasMore = ref(true);
const loading = ref(false);
const showNoMoreData = ref(false);

// 图片查看模态框相关数据
const showModal = ref(false);
const currentIndex = ref(0);
const currentMediaType = ref(""); // 'image' 或 'video'

// 图片缩放相关数据
const scale = ref(1);
const minScale = ref(1);
const maxScale = ref(5);
const isDragging = ref(false);
const position = ref({ x: 0, y: 0 });
const startPosition = ref({ x: 0, y: 0 });
const startDragPosition = ref({ x: 0, y: 0 });

// 获取路由实例
const route = useRoute();
const router = useRouter();

// 获取媒体列表
const fetchMediaList = async (loadMore = false) => {
  if (loadMore && (!hasMore.value || loading.value)) {
    return;
  }

  try {
    loading.value = true;

    // 获取查询参数中的 id，并将其作为 toolId
    const toolId = route.query.id;

    if (loadMore) {
      currentPage.value++;
    } else {
      currentPage.value = 1;
    }

    // 构造请求参数
    const params = {
      toolId: toolId,
      page: currentPage.value,
      pageSize: pageSize.value,
    };

    const response = await request({
      url: "/media/list",
      method: "GET",
      params: params,
    });

    if (response.code === 200) {
      const newRecords = response.data.records.filter(
        (item) =>
          item.mimeType &&
          (item.mimeType.startsWith("image/") ||
            item.mimeType.startsWith("video/"))
      );

      if (loadMore) {
        mediaList.value = [...mediaList.value, ...newRecords];
      } else {
        mediaList.value = newRecords;
      }

      // 修正 hasMore 的判断逻辑 - 使用API返回的页码信息来判断
      hasMore.value = response.data.current < response.data.pages;

      // 如果没有更多数据，并且是加载更多时才显示提示
      if (!hasMore.value && loadMore) {
        showNoMoreData.value = true;
        setTimeout(() => {
          showNoMoreData.value = false;
        }, 1500);
      }
    }
  } catch (error) {
    console.error("获取媒体列表失败:", error);
    if (loadMore) {
      currentPage.value--;
    }
  } finally {
    loading.value = false;
  }
};

// 格式化文件大小
const formatFileSize = (size) => {
  if (size < 1024) {
    return size + " B";
  } else if (size < 1024 * 1024) {
    return (size / 1024).toFixed(2) + " KB";
  } else {
    return (size / (1024 * 1024)).toFixed(2) + " MB";
  }
};

// 格式化文件名
const formatFileName = (name) => {
  if (name.length <= 12) {
    return name;
  } else {
    const first = name.substring(0, 6);
    const last = name.substring(name.length - 6);
    return first + "..." + last;
  }
};

// 格式化文件类型，只显示后缀部分
const formatFileType = (mimeType) => {
  if (!mimeType) return "";
  const parts = mimeType.split("/");
  return parts.length > 1 ? parts[1] : mimeType;
};

// 防抖函数
const debounce = (func, wait) => {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
};

// 【关键修复】优化滚动事件处理 - 监听 .page-container 的滚动而不是 window
const handleScroll = debounce(() => {
  const container = document.querySelector('.page-container');
  if (!container) return;
  
  const scrollTop = container.scrollTop;
  const scrollHeight = container.scrollHeight;
  const clientHeight = container.clientHeight;
  
  // 距离底部小于 100px 时加载更多
  if (scrollHeight - scrollTop - clientHeight < 100) {
    if (hasMore.value && !loading.value) {
      fetchMediaList(true);
    }
  }
}, 200);

// 获取图片元素的实际尺寸
const getImageDimensions = (imgUrl) => {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      resolve({
        width: img.naturalWidth,
        height: img.naturalHeight,
      });
    };
    img.onerror = reject;
    img.src = imgUrl;
  });
};

// 图片缩放相关函数
// 处理鼠标滚轮事件
const handleWheel = (event) => {
  if (currentMediaType.value !== "image") return;

  event.preventDefault();

  // 计算新的缩放比例
  const delta = event.deltaY > 0 ? -0.1 : 0.1;
  const newScale = scale.value + delta;

  // 限制缩放范围
  if (newScale >= minScale.value && newScale <= maxScale.value) {
    scale.value = newScale;

    // 更新图片变换样式
    const viewerMedia = document.querySelector(".viewer-media");
    if (viewerMedia) {
      viewerMedia.style.transform = `translate(${position.value.x}px, ${position.value.y}px) scale(${scale.value})`;
    }
  }
};

// 鼠标按下事件
const handleMouseDown = (event) => {
  if (currentMediaType.value !== "image" || scale.value <= minScale.value)
    return;

  isDragging.value = true;
  startPosition.value = {
    x: event.clientX,
    y: event.clientY,
  };
  startDragPosition.value = {
    x: position.value.x,
    y: position.value.y,
  };

  event.preventDefault();
};

// 鼠标移动事件
const handleMouseMove = (event) => {
  if (!isDragging.value) return;

  const deltaX = event.clientX - startPosition.value.x;
  const deltaY = event.clientY - startPosition.value.y;

  position.value = {
    x: startDragPosition.value.x + deltaX,
    y: startDragPosition.value.y + deltaY,
  };

  // 更新图片位置
  const viewerMedia = document.querySelector(".viewer-media");
  if (viewerMedia) {
    viewerMedia.style.transform = `translate(${position.value.x}px, ${position.value.y}px) scale(${scale.value})`;
  }
};

// 鼠标释放事件
const handleMouseUp = () => {
  isDragging.value = false;
};

// 重置缩放状态
const resetScale = () => {
  scale.value = minScale.value;
  position.value = { x: 0, y: 0 };

  // 更新图片位置
  const viewerMedia = document.querySelector(".viewer-media");
  if (viewerMedia) {
    viewerMedia.style.transform = `translate(0px, 0px) scale(${minScale.value})`;
  }
};

// 打开媒体查看模态框
const openMediaViewer = async (index) => {
  currentIndex.value = index;
  const media = mediaList.value[index];
  currentMediaType.value = media.mimeType.startsWith("image/")
    ? "image"
    : "video";

  // 重置缩放状态
  resetScale();

  // 如果是图片，计算最小缩放比例
  if (currentMediaType.value === "image") {
    try {
      // 等待 DOM 更新
      await nextTick();
      
      // 获取图片实际尺寸
      const imgUrl = fileUrlPrefix.value + media.fileUrl;
      const dimensions = await getImageDimensions(imgUrl);

      // 获取查看器容器尺寸
      const viewerContent = document.querySelector(".media-viewer-content");
      if (viewerContent) {
        const containerWidth = viewerContent.clientWidth;
        const containerHeight = viewerContent.clientHeight;

        // 计算最小缩放比例，使图片至少占满查看框
        const scaleX = containerWidth / dimensions.width;
        const scaleY = containerHeight / dimensions.height;
        minScale.value = Math.max(scaleX, scaleY, 1); // 至少为1
        scale.value = minScale.value; // 设置初始缩放比例

        // 更新图片变换样式
        const viewerMedia = document.querySelector(".viewer-media");
        if (viewerMedia) {
          viewerMedia.style.transform = `translate(0px, 0px) scale(${minScale.value})`;
        }
      }
    } catch (error) {
      console.error("获取图片尺寸失败:", error);
      minScale.value = 1;
      scale.value = 1;
    }
  }

  showModal.value = true;
};

// 关闭媒体查看模态框
const closeMediaViewer = () => {
  showModal.value = false;
};

// 切换到上一个媒体
const prevMedia = async () => {
  currentIndex.value = (currentIndex.value > 0) ? currentIndex.value - 1 : mediaList.value.length - 1;
  const media = mediaList.value[currentIndex.value];
  currentMediaType.value = media.mimeType.startsWith("image/")
    ? "image"
    : "video";

  // 如果是图片，重新计算最小缩放比例
  if (currentMediaType.value === "image") {
    try {
      // 等待 DOM 更新
      await nextTick();
      
      // 获取图片实际尺寸
      const imgUrl = fileUrlPrefix.value + media.fileUrl;
      const dimensions = await getImageDimensions(imgUrl);

      // 获取查看器容器尺寸
      const viewerContent = document.querySelector(".media-viewer-content");
      if (viewerContent) {
        const containerWidth = viewerContent.clientWidth;
        const containerHeight = viewerContent.clientHeight;

        // 计算最小缩放比例，使图片至少占满查看框
        const scaleX = containerWidth / dimensions.width;
        const scaleY = containerHeight / dimensions.height;
        minScale.value = Math.max(scaleX, scaleY, 1); // 至少为1
        scale.value = minScale.value; // 设置初始缩放比例

        // 更新图片变换样式
        const viewerMedia = document.querySelector(".viewer-media");
        if (viewerMedia) {
          viewerMedia.style.transform = `translate(0px, 0px) scale(${minScale.value})`;
        }
      }
    } catch (error) {
      console.error("获取图片尺寸失败:", error);
      minScale.value = 1;
      scale.value = 1;

      // 更新图片变换样式
      const viewerMedia = document.querySelector(".viewer-media");
      if (viewerMedia) {
        viewerMedia.style.transform = `translate(0px, 0px) scale(1)`;
      }
    }
  } else {
    // 如果不是图片，重置缩放状态
    resetScale();
  }
};

// 切换到下一个媒体
const nextMedia = async () => {
  currentIndex.value = (currentIndex.value < mediaList.value.length - 1) ? currentIndex.value + 1 : 0;
  const media = mediaList.value[currentIndex.value];
  currentMediaType.value = media.mimeType.startsWith("image/")
    ? "image"
    : "video";

  // 如果是图片，重新计算最小缩放比例
  if (currentMediaType.value === "image") {
    try {
      // 等待 DOM 更新
      await nextTick();
      
      // 获取图片实际尺寸
      const imgUrl = fileUrlPrefix.value + media.fileUrl;
      const dimensions = await getImageDimensions(imgUrl);

      // 获取查看器容器尺寸
      const viewerContent = document.querySelector(".media-viewer-content");
      if (viewerContent) {
        const containerWidth = viewerContent.clientWidth;
        const containerHeight = viewerContent.clientHeight;

        // 计算最小缩放比例，使图片至少占满查看框
        const scaleX = containerWidth / dimensions.width;
        const scaleY = containerHeight / dimensions.height;
        minScale.value = Math.max(scaleX, scaleY, 1); // 至少为1
        scale.value = minScale.value; // 设置初始缩放比例

        // 更新图片变换样式
        const viewerMedia = document.querySelector(".viewer-media");
        if (viewerMedia) {
          viewerMedia.style.transform = `translate(0px, 0px) scale(${minScale.value})`;
        }
      }
    } catch (error) {
      console.error("获取图片尺寸失败:", error);
      minScale.value = 1;
      scale.value = 1;

      // 更新图片变换样式
      const viewerMedia = document.querySelector(".viewer-media");
      if (viewerMedia) {
        viewerMedia.style.transform = `translate(0px, 0px) scale(1)`;
      }
    }
  } else {
    // 如果不是图片，重置缩放状态
    resetScale();
  }
};

// 处理键盘事件
const handleKeyDown = (event) => {
  if (!showModal.value) return; // 仅在模态框显示时响应

  switch (event.key) {
    case 'ArrowLeft':
      prevMedia();
      break;
    case 'ArrowRight':
      nextMedia();
      break;
    case 'Escape':
      closeMediaViewer();
      break;
  }
};

// 【关键修复】调整生命周期钩子 - 监听正确的滚动容器
onMounted(() => {
  fetchMediaList();
  
  // 【修复点】监听 .page-container 容器的滚动而不是 window
  const container = document.querySelector('.page-container');
  if (container) {
    container.addEventListener('scroll', handleScroll);
  }
  
  // 监听键盘事件
  window.addEventListener('keydown', handleKeyDown);
});

onUnmounted(() => {
  // 【修复点】移除正确的滚动监听器
  const container = document.querySelector('.page-container');
  if (container) {
    container.removeEventListener('scroll', handleScroll);
  }
  
  // 移除键盘事件监听
  window.removeEventListener('keydown', handleKeyDown);
});

// 滚动到顶部
const scrollToTop = () => {
  const container = document.querySelector(".page-container");
  if (container) {
    container.scrollTo({
      top: 0,
      behavior: "smooth",
    });
  }
};

// 滚动到底部
const scrollToBottom = () => {
  const container = document.querySelector(".page-container");
  if (container) {
    container.scrollTo({
      top: container.scrollHeight,
      behavior: "smooth",
    });
  }
};

// 返回到/finishing路由
const goBack = () => {
  router.push("/finishing");
};

// 下载当前文件
const downloadFile = async () => {
  const media = mediaList.value[currentIndex.value];
  const fileUrl = fileUrlPrefix.value + media.fileUrl;
  const fileName = media.fileName;

  try {
    // 获取文件内容
    const response = await fetch(fileUrl);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    // 创建 Blob 对象
    const blob = await response.blob();

    // 创建对象 URL
    const url = URL.createObjectURL(blob);

    // 创建一个隐藏的<a>标签
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    link.style.display = "none";

    // 将<a>标签添加到DOM中并触发点击事件
    document.body.appendChild(link);
    link.click();

    // 移除<a>标签
    document.body.removeChild(link);

    // 释放对象 URL
    URL.revokeObjectURL(url);
  } catch (error) {
    console.error("下载文件失败:", error);
    // 如果下载失败，尝试使用原始方法
    const link = document.createElement("a");
    link.href = fileUrl;
    link.download = fileName;
    link.style.display = "none";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
};

// 复制当前文件访问地址
const copyFileUrl = async () => {
  const media = mediaList.value[currentIndex.value];
  const fileUrl = fileUrlPrefix.value + media.fileUrl;

  try {
    // 首先尝试使用 Clipboard API
    await navigator.clipboard.writeText(fileUrl);
    ElMessage.success("文件地址已复制到剪贴板");
  } catch (error) {
    // 如果 Clipboard API 失败，尝试使用备选方法
    console.error("Clipboard API 失败:", error);
    
    try {
      // 创建一个临时的 textarea 元素
      const textarea = document.createElement("textarea");
      textarea.value = fileUrl;
      textarea.style.position = "fixed";
      textarea.style.left = "-9999px";
      textarea.style.top = "-9999px";
      document.body.appendChild(textarea);
      
      // 选择并复制文本
      textarea.select();
      const successful = document.execCommand("copy");
      document.body.removeChild(textarea);
      
      if (successful) {
        ElMessage.success("文件地址已复制到剪贴板");
      } else {
        throw new Error("execCommand 复制失败");
      }
    } catch (fallbackError) {
      console.error("备选复制方法失败:", fallbackError);
      ElMessage.error("复制文件地址失败，请手动复制");
    }
  }
};
</script>

<template>
  <div class="page-container">
    <!-- 顶部占位栏 -->
    <div class="top-bar">
      <el-icon class="back-button" @click="goBack"><ArrowLeft /></el-icon>
    </div>

    <!--
      【核心修改】
      添加 v-if="mediaList.length > 0"。
      这确保了 masonry 组件只会在获取到媒体数据后才会被渲染，
      从而避免了在空数据时进行错误的布局计算。
      <masonry> 组件的 :gutter="{ default: '5px' }" 属性控制卡片之间的左右间距
    -->
    <masonry
      v-if="mediaList.length > 0"
      :cols="{ default: 5, 1200: 4, 1024: 3, 768: 2, 500: 1 }"
      :gutter="{ default: '5px' }"
    >
      <div
        class="item"
        v-for="(media, index) in mediaList"
        :key="media.fileId"
        @click="openMediaViewer(index)"
      >
        <div class="media-content-wrapper">
          <img
            v-if="media.mimeType.startsWith('image/')"
            :src="fileUrlPrefix + media.thumbnailUrl"
            :alt="media.fileName"
            class="media-thumbnail"
          />
          <div
            v-else-if="media.mimeType.startsWith('video/')"
            class="video-thumbnail-container"
          >
            <img
              :src="fileUrlPrefix + media.thumbnailUrl"
              :alt="media.fileName"
              class="media-thumbnail"
            />
            <div class="play-icon">▶</div>
          </div>
        </div>
      </div>
    </masonry>
    <div v-else class="empty-data">当前分类没有数据哦！</div>

    <!-- 加载更多提示 -->
    <div v-if="loading" class="loading-more">加载中...</div>

    <!-- 没有更多数据提示 -->
    <div v-if="showNoMoreData" class="no-more-data">已经到底啦！</div>

    <!-- 滚动到顶部和底部的按钮 -->
    <div class="scroll-buttons">
      <el-icon class="scroll-button" @click="scrollToTop"><ArrowUp /></el-icon>
      <el-icon class="scroll-button" @click="scrollToBottom"
        ><ArrowDown
      /></el-icon>
    </div>
  </div>

  <!-- 媒体查看模态框 -->
  <div v-if="showModal" class="media-viewer-modal" @click="closeMediaViewer">
    <!-- 文件详细信息显示区域 -->
    <div class="media-info">
      <h3>{{ formatFileName(mediaList[currentIndex].fileName) }}</h3>
      <p>文件大小: {{ formatFileSize(mediaList[currentIndex].fileSize) }}</p>
      <p>尺寸: {{ mediaList[currentIndex].width }} x {{ mediaList[currentIndex].height }}</p>
      <p>文件类型: {{ formatFileType(mediaList[currentIndex].mimeType) }}</p>
      <el-icon class="copy-icon" @click.stop="copyFileUrl"><Document /></el-icon>
      <el-icon class="download-icon" @click.stop="downloadFile"><Download /></el-icon>
    </div>

    <!-- 左右导航按钮 -->
    <button class="nav-button prev" @click.stop="prevMedia">‹</button>
    <button class="nav-button next" @click.stop="nextMedia">›</button>
    
    <!-- 关闭按钮（位置微调，与导航按钮视觉上更协调） -->
    <button class="close-button" @click.stop="closeMediaViewer">×</button>

    <div class="media-viewer-content" @click.stop
      @wheel="handleWheel"
      @mousemove="handleMouseMove"
      @mouseup="handleMouseUp"
      @mouseleave="handleMouseUp"
    >
      <img
        v-if="currentMediaType === 'image'"
        :src="fileUrlPrefix + mediaList[currentIndex].fileUrl"
        :alt="mediaList[currentIndex].fileName"
        class="viewer-media"
        :style="{
          transform: `translate(${position.x}px, ${position.y}px) scale(${scale})`,
          transition: isDragging ? 'none' : 'transform 0.1s ease',
          cursor: scale > minScale ? 'grab' : 'default'
        }"
        @mousedown="handleMouseDown"
      />
      <video
        v-else-if="currentMediaType === 'video'"
        :src="fileUrlPrefix + mediaList[currentIndex].fileUrl"
        controls
        autoplay
        class="viewer-media"
      >
        您的浏览器不支持视频播放。
      </video>
    </div>
  </div>
</template>

<style scoped>
/* 页面主容器 */
.page-container {
  /*距离顶部5px*/
  height: 100vh;
  overflow-y: auto;
  box-sizing: border-box;
  padding-top: 5vh; /* 为固定顶部栏留出空间 */
  background-color: #000000; /* 设置背景色为黑色 */
}

/* 顶部占位栏样式 */
.top-bar {
  height: 5vh;
  background-color: #f0f0f0; /* 可以根据需要修改背景色 */
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 1000; /* 确保顶部栏在其他元素之上 */
  display: flex;
  align-items: center;
  padding-left: 20px;
}

/* 返回按钮样式 */
.back-button {
  font-size: 24px;
  color: #333;
  cursor: pointer;
}

/* 单个卡片项样式 */
.item {
  margin-bottom: 6px; /*上下间间距*/
  background: #fff;
  border-radius: 10px; /*圆角*/
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  cursor: pointer;
  break-inside: avoid;
  transition: transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out;
}
.item:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.15);
}

/* 媒体内容容器 */
.media-content-wrapper {
  background-color: #f0f0f0;
}

/* 图片和视频封面统一样式 */
.media-thumbnail {
  display: block;
  width: 100%;
  height: auto;
}

/* 视频封面容器 */
.video-thumbnail-container {
  position: relative;
  width: 100%;
  height: 100%;
}

.play-icon {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border-radius: 50%;
  width: 50px;
  height: 50px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 20px;
  pointer-events: none;
}

/* 模态框样式 */
.media-viewer-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.9);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
  cursor: zoom-out;
}
.media-viewer-content {
  position: relative;
  max-width: 90%;
  max-height: 90%;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: default;
  margin-top: 5vh; /* 为顶部信息栏留出空间 */
}
.viewer-media {
  max-height: 95vh;
  max-width: 90vw;
  object-fit: contain;
  display: block;
}
.close-button {
  position: absolute;
  top: calc(5vh + 10px); /* 调整top值，使其在media-info下方 */
  right: 20px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
  transition: background 0.3s;
  z-index: 2003;
}
.close-button:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 文件信息显示区域样式 */
.media-info {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 5vh;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 15px; /* 元素之间的间距 */
  z-index: 2002;
  overflow: hidden; /* 防止内容溢出 */
}

.media-info h3 {
  margin: 0;
  font-size: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 2; /* 文件名占据更多空间 */
}

.media-info p {
  margin: 0;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1; /* 其他信息占据较少空间 */
}

.copy-icon,
.download-icon {
  font-size: 20px;
  cursor: pointer;
  transition: color 0.3s;
  flex-shrink: 0; /* 防止图标被压缩 */
}

.copy-icon:hover,
.download-icon:hover {
  color: #409eff;
}

/* 空数据提示样式 */
.empty-data {
  text-align: center;
  padding: 50px 20px;
  font-size: 18px;
  color: #ccc; /* 浅灰色文字 */
  background: #333; /* 深灰色背景 */
  border-radius: 10px;
  margin: 20px;
}

/* 加载更多提示样式 */
.loading-more {
  text-align: center;
  padding: 15px;
  font-size: 16px;
  color: #ccc; /* 浅灰色文字 */
}

/* 没有更多数据提示样式 */
.no-more-data {
  text-align: center;
  padding: 15px;
  font-size: 16px;
  color: #999;
}

/* 滚动按钮样式 */
.scroll-buttons {
  position: fixed;
  right: 20px;
  bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 1000;
}

.scroll-button {
  width: 40px;
  height: 40px;
  background-color: rgba(0, 0, 0, 0.6);
  color: white;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: background-color 0.3s;
}

.scroll-button:hover {
  background-color: rgba(0, 0, 0, 0.8);
}

/* 新增：导航按钮样式 */
.nav-button {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2003;
  background: rgba(30, 30, 30, 0.5);
  color: white;
  border: none;
  border-radius: 50%;
  width: 50px;
  height: 50px;
  font-size: 30px;
  line-height: 50px;
  text-align: center;
  cursor: pointer;
  transition: background-color 0.3s ease;
  display: flex;
  justify-content: center;
  align-items: center;
  user-select: none; /* 防止双击选中文本 */
}

.nav-button:hover {
  background: rgba(0, 0, 0, 0.8);
}

.nav-button.prev {
  left: 20px;
}

.nav-button.next {
  right: 20px;
}
</style>
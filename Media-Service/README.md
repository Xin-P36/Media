# Media Service 项目说明

## 项目概述
Media Service 是一个基于 Spring Boot 3.5.4 和 Java 17 开发的媒体文件管理系统，专门用于管理和处理图片、视频、音频等多媒体文件。该项目提供了完整的媒体文件存储、分类、检索、转码等功能。

## 技术栈

### 核心框架
- **Spring Boot 3.5.4** - 主框架
- **Java 17** - 运行环境
- **Maven 3.9** - 构建工具

### 数据库
- **SQLite** - 轻量级数据库
- **MyBatis Plus 3.5.12** - ORM 框架

### 多媒体处理
- **FFmpeg** - 视频转码和处理
- **Metadata Extractor 2.19.0** - 图片/视频元数据提取
- **Thumbnailator 0.4.20** - 缩略图生成

### 其他工具
- **JWT (JJWT 0.12.6)** - 用户认证
- **Lombok** - 简化代码
- **Commons Codec** - 文件哈希计算
- **Jackson** - JSON 处理

## 项目结构

```
src/
├── main/
│   ├── java/org/xinp/
│   │   ├── config/          # 配置类
│   │   │   ├── AppRun.java
│   │   │   ├── MybatisPlusConfig.java
│   │   │   ├── MediaAppConfig.java
│   │   │   └── WedConfig.java
│   │   ├── controller/      # 控制器层
│   │   │   ├── MediaController.java
│   │   │   ├── UserController.java
│   │   │   ├── ToolController.java
│   │   │   └── OperationLogController.java
│   │   ├── entity/          # 实体类
│   │   │   ├── MediaFiles.java
│   │   │   ├── User.java
│   │   │   └── ToolList.java
│   │   ├── service/         # 服务层
│   │   │   └── MediaService.java
│   │   ├── mapper/          # 数据访问层
│   │   └── constant/        # 常量类
│   │       ├── FileStatus.java
│   │       └── Code.java
│   └── resources/
└── test/
```

## 核心功能

### 1. 媒体文件管理
- **文件扫描**：支持指定目录扫描媒体文件
- **文件存储**：支持图片、视频、音频等多种格式
- **文件分类**：支持自定义分类工具
- **文件检索**：支持关键字搜索和分页查询
- **文件删除**：支持软删除（标记删除）

### 2. 文件上传与处理
- **文件上传**：支持通过 Nginx 上传文件
- **元数据提取**：自动提取文件的 EXIF、IPTC、XMP 等元数据
- **缩略图生成**：自动生成文件缩略图
- **文件哈希**：计算文件唯一标识码

### 3. 视频转码
- **格式转换**：支持多种视频格式间的转换（mp4、mkv、flv等）
- **参数定制**：
  - 视频编码（H.264、H.265、VP9等）
  - 分辨率调整
  - 码率控制
  - 帧率设置
  - 音频编码和码率
  - 视频倍速播放
- **任务管理**：支持转码任务的添加和管理

### 4. 用户管理
- **用户认证**：基于 JWT 的身份验证
- **权限控制**：基本的用户权限管理

### 5. 系统监控
- **操作日志**：记录所有文件操作
- **进度追踪**：实时监控文件扫描和处理进度

## API 接口

### 媒体文件管理
- `POST /api/media/scan/start` - 开始扫描指定路径
- `GET /api/media/scan/progress` - 查询扫描进度
- `POST /api/media/scan/cancel` - 停止扫描
- `GET /api/media/list` - 获取文件列表
- `POST /api/media/process-upload` - 处理上传的文件
- `POST /api/media/move` - 文件分组/重命名
- `POST /api/media/delete` - 删除文件
- `POST /api/media/transcode` - 添加视频转码任务

### 用户管理
- `POST /api/user/login` - 用户登录
- `GET /api/user/info` - 获取用户信息

### 工具管理
- `GET /api/tool/list` - 获取工具列表

## 部署说明

### 环境要求
- Java 17+
- FFmpeg
- Docker（可选）

### 构建
```bash
mvn clean package
```

### Docker 部署
```bash
docker build -t media-service .
docker run -d -p 8080:8080 -v /data:/data media-service
```

### 启动参数
- 端口：8080
- 数据目录：/data
- JVM 参数：-Xms256m -Xmx512m

## 特色功能

1. **多媒体元数据提取**：支持图片、视频的详细元数据提取
2. **智能缩略图生成**：自动为媒体文件生成缩略图
3. **视频转码**：支持多种视频参数的定制化转码
4. **文件哈希验证**：确保文件唯一性和完整性
5. **进度监控**：实时显示文件处理进度
6. **Docker 容器化**：支持 Docker 一键部署

## 数据库表结构

### media_files（媒体文件表）
- fileId：文件ID（主键）
- fileName：文件名
- mimeType：文件类型
- fileSize：文件大小
- fileStatus：文件状态
- toolId：分类ID
- filePath：文件路径
- width/height：分辨率
- duration：时长
- thumbnail：缩略图路径
- fileHash：文件哈希
- metadata：元数据JSON
- updateTime：更新时间

## 开发团队
- 开发者：XinP
- 项目名称：Media
- 版本：0.0.1-SNAPSHOT
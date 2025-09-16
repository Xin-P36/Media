# Media 项目综合说明

## 项目概述

Media 是一个基于前后端分离架构的现代化媒体文件管理系统，由 Media-Service（后端服务）和 Media-Vue（前端应用）两个核心组件组成。该系统专门用于处理、存储、分类和管理图片、视频、音频等多媒体文件，提供完整的媒体资产管理解决方案。



项目默认扫描文件夹：TemporaryMedia文件夹下的文件作为未分类文件录入系统，因为服务端与wed分开两台机器开发的所以前端的请求地址暂时写死为10.10.10.103

暂时写死：

用户名：root

密码：%9#6ImIpkTot1f*e



项目提供Docker打包与源代码，Docker打包已经包含ffmpeg

### 目前项目存在的问题

1. 前端还未实现文件上传自动扫描的功能
2. 现在的程序流程（异步的触发时机为完整，比较混乱）
3. 后端的整体项目流程还是比较混乱的

花费2星期倾情打造，后面有时间整理一下流程，增加音频可视化，多文件的预览。

## 系统架构

### 整体架构
- **前端**：Media-Vue - 基于 Vue 3 + Vite 的现代Web应用
- **后端**：Media-Service - 基于 Spring Boot 3.5.4 的RESTful服务
- **数据库**：SQLite - 轻量级关系数据库
- **多媒体处理**：FFmpeg + Metadata Extractor + Thumbnailator

### 技术栈详情

#### 后端技术栈 (Media-Service)
- **核心框架**：Spring Boot 3.5.4 + Java 17
- **构建工具**：Maven 3.9
- **数据库**：SQList
- **ORM框架**：MyBatis Plus 3.5.12
- **多媒体处理**：
  - FFmpeg - 视频转码和处理
  - Metadata Extractor 2.19.0 - 元数据提取
  - Thumbnailator 0.4.20 - 缩略图生成
- **安全认证**：JWT (JJWT 0.12.6)
- **其他工具**：Lombok、Commons Codec、Jackson

#### 前端技术栈 (Media-Vue)
- **核心框架**：Vue 3.5.18 + Vite 7.0.6
- **路由管理**：Vue Router 4.5.1
- **UI组件库**：Element Plus 2.10.4
- **功能插件**：
  - axios 1.11.0 - HTTP请求
  - vue-next-masonry - 瀑布流布局
  - video.js 8.23.4 - 视频播放
  - jszip 3.10.1 - ZIP压缩处理

## 核心功能模块

### 1. 媒体文件管理
- **文件扫描**：自动扫描指定目录，识别媒体文件
- **文件存储**：支持图片、视频、音频等多种格式
- **文件分类**：自定义分类工具，支持批量分组
- **文件检索**：关键字搜索、分页查询、多维度筛选
- **文件删除**：软删除机制，支持恢复操作

### 2. 多媒体处理
- **元数据提取**：自动提取EXIF、IPTC、XMP等元数据
- **缩略图生成**：智能生成预览缩略图
- **文件哈希**：计算文件唯一标识，确保完整性
- **视频转码**：
  - 多格式支持（mp4、mkv、flv等）
  - 自定义编码参数（H.264、H.265、VP9等）
  - 分辨率、码率、帧率调整
  - 音频参数定制
  - 倍速播放支持

### 3. 用户认证与权限
- **JWT认证**：基于Token的安全身份验证
- **用户管理**：登录、权限控制
- **路由守卫**：前端路由权限控制

### 4. 系统监控与日志
- **操作日志**：记录所有文件操作
- **进度监控**：实时显示任务处理进度
- **系统状态**：服务器资源使用情况监控

### 5. 用户界面特性
- **响应式设计**：适配桌面端和移动端
- **瀑布流布局**：优雅的媒体文件展示
- **批量操作**：支持多文件同时处理
- **实时更新**：轮询机制获取任务进度

## 项目结构

```
Media/
├── Media-Service/          # Spring Boot 后端服务
│   ├── src/main/java/org/xinp/
│   │   ├── config/         # 配置类
│   │   ├── controller/     # 控制器层
│   │   ├── entity/         # 实体类
│   │   ├── service/        # 服务层
│   │   ├── mapper/         # 数据访问层
│   │   └── constant/       # 常量类
│   └── src/main/resources/ # 配置文件
├── Media-Vue/             # Vue 3 前端应用
│   ├── src/
│   │   ├── views/         # 页面组件
│   │   ├── router/        # 路由配置
│   │   ├── utils/         # 工具类
│   │   └── main.js        # 入口文件
│   └── public/            # 静态资源
└── README.md              # 项目说明文档
```

## API接口设计

### 媒体文件管理API
- `POST /api/media/scan/start` - 开始扫描指定路径
- `GET /api/media/scan/progress` - 查询扫描进度
- `POST /api/media/scan/cancel` - 停止扫描
- `GET /api/media/list` - 获取文件列表
- `POST /api/media/process-upload` - 处理上传的文件
- `POST /api/media/move` - 文件分组/重命名
- `POST /api/media/delete` - 删除文件
- `POST /api/media/transcode` - 添加视频转码任务

### 用户管理API
- `POST /api/user/login` - 用户登录
- `GET /api/user/info` - 获取用户信息

### 系统管理API
- `GET /api/tool/list` - 获取工具列表
- `GET /api/media/operation-logs` - 获取操作日志

## 部署说明

### 环境要求
- **后端**：Java 17+、FFmpeg
- **前端**：Node.js >= 20.19.0 或 >= 22.12.0
- **数据库**：SQLite（自动创建）
- **容器化**：Docker（可选）

### 构建与启动

#### 后端服务
```bash
cd Media-Service
mvn clean package
java -jar target/media-service-0.0.1-SNAPSHOT.jar
```

#### 前端应用
```bash
cd Media-Vue
npm install
npm run dev    # 开发环境 (端口80)
npm run build  # 生产环境构建
```

#### Docker部署
```bash
# 构建后端服务
cd Media-Service
docker build -t media-service .
docker run -d -p 8080:8080 -v /data:/data media-service

# 部署前端应用
cd Media-Vue
npm run build
# 将dist目录部署到Web服务器
```

#### Nginx配置

```
user www-data; # 运行Nginx的用户
worker_processes auto;
pid /run/nginx.pid;
error_log /var/log/nginx/error.log; # 错误日志存放位置
include /etc/nginx/modules-enabled/*.conf;

events {
	worker_connections 768; # 最大并发数
}

http {
	sendfile on; # 允许直接从磁盘读取文件
	tcp_nopush on; # 发送完整数据包
	types_hash_max_size 2048; # 哈希表查找相关
	include /etc/nginx/mime.types; # 定义文件名与HTTTP的映射关系
	default_type application/octet-stream; # 文件类型匹配不上，默认下载
	ssl_protocols TLSv1 TLSv1.1 TLSv1.2 TLSv1.3; # Dropping SSLv3, ref: POODLE 定义SSL/TLS协议
	ssl_prefer_server_ciphers on; # 优先使用服务端加密套件
	access_log /var/log/nginx/access.log; # 访问日志
	gzip on; # 启用压缩功能

	include /etc/nginx/conf.d/*.conf; # 模块化配置文件
	#include /etc/nginx/sites-enabled/*; # 移除默认设置
	
	server {
		listen 80; # 监听端口
		server_name  10.10.10.103;
		charset utf-8;
		# 网页服务
		location / {
			root /usr/media/html;          # 指向 index.html 所在目录
    		index index.html;            # 默认首页
    		try_files $uri $uri/ /index.html;   # 尝试匹配文件或目录，否则返回404
		}
    		# 文件服务
		location ^~ /content/ {
			autoindex on;                         # 启用自动首页功能
			autoindex_format html;                # 首页格式为HTML
			autoindex_exact_size off;             # 文件大小自动换算
			autoindex_localtime on;               # 按照服务器时间显示文件时间
			alias /usr/media/;       # 文件存放位置

			# --- START: 新增的CORS配置 ---跨域设置
			# 允许来自任何源的跨域请求。在生产环境中，最好将其替换为您的前端域名，例如 'http://localhost'。
			#add_header 'Access-Control-Allow-Origin' '*' always;
			# 允许的HTTP请求方法
			#add_header 'Access-Control-Allow-Methods' 'GET, POST, OPTIONS' always;
			# 允许的请求头
			#add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range' always;
			# 暴露给浏览器的响应头
			#add_header 'Access-Control-Expose-Headers' 'Content-Length,Content-Range' always;
			# 浏览器会首先发送一个 OPTIONS "预检"请求，这里我们直接返回204 No Content，并带上CORS头
			#if ($request_method = 'OPTIONS') {
			#	return 204;
			#}	
			# --- END: 新增的CORS配置 ---
 
			# 将当前目录中所有文件的默认MIME类型设置为 application/octet-stream
			default_type application/octet-stream; 
        	if ($request_filename ~* ^.*?\.(txt|doc|pdf|rar|gz|zip|docx|exe|xlsx|ppt|pptx)$){
				# 当文件格式为上述格式时，将头字段属性Content-Disposition的值设置为"attachment"
            	add_header Content-Disposition 'attachment;'; 
        	}
			
			# 开启零复制文件传输功能
			sendfile on;
			# 每个sendfile调用的最大传输量为1MB
			sendfile_max_chunk 1m;    
				# 启用最小传输限制功能
        		tcp_nopush on;                        
	 
			# 启用异步传输
			# aio on;   
			# 当文件大于5MB时以直接读取磁盘的方式读取文件			
		    directio 5m;     
			# 与磁盘的文件系统对齐
		    directio_alignment 4096;  
			# 文件输出的缓冲区大小为128KB
		    output_buffers 4 32k;                 
 	
			# 限制下载速度为1MB
			# limit_rate 1m;   
			# 当客户端下载速度达到2MB时进入限速模式			
			# limit_rate_after 2m;     
			# 客户端执行范围读取的最大值是4096B			
		    max_ranges 4096;   
			# 客户端引发传输超时时间为20s			
		    send_timeout 20s;  
			# 当缓冲区的数据达到2048B时再向客户端发送			
		    postpone_output 2048;   
			# 启用分块传输标识	
		    chunked_transfer_encoding on;         
		}
		# API代理
		location /api/ { # 拦截/api请求
	            proxy_pass http://localhost:8080; # 转发请求到
        	    proxy_set_header Host $host; # 转发请求头
	            proxy_set_header X-Real-IP $remote_addr; # 转发真实请求的真实IP
        	    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
	            proxy_set_header X-Forwarded-Proto $scheme; # 告知请求的发起方式
				proxy_set_header token $http_token; # 设置请求token
        }
		# 文件上传
		location /upload/ {
			client_max_body_size 10240M;  # 允许最大 10G 的文件上传
            # 只接受POST, PUT, PATCH请求
            limit_except POST PUT PATCH {
				deny all;
            }

            # 将上传的文件直接写入磁盘
            client_body_in_file_only on;

            # 将临时文件的路径设置到一个HTTP头中，以便后端可以获取
	        client_body_temp_path /usr/media/uploads; # 所有上传的文件都会被存到这里

            # 将上传文件的临时路径传递给后端Spring Boot
            # Nginx会生成一个随机的文件名，如 /var/nginx/uploads/0000000001
            # 我们把这个路径通过一个自定义的HTTP头 X-Temp-File-Path 传给后端
            proxy_set_header X-Temp-File-Path $request_body_file;
            proxy_set_header X-Original-File-Name $http_x_original_file_name;#自定义一个头来传递原始文件名
	        # *** 关键新增行：同样需要转发 token 头 ***
            proxy_set_header token $http_token;

            # 将请求转发给后端的“处理”接口
            proxy_pass http://localhost:8080/api/media/process-upload;

            # 告诉后端不要等待文件体，因为文件已经在磁盘上了
            proxy_pass_request_body off;
            proxy_set_header Content-Length "";
        }
	}
}
```

### 配置参数

- **后端端口**：8080
- **前端端口**：80
- **数据目录**：/data（可配置）
- **数据库**：SQLite（默认内存数据库）
- **API代理**：前端 `/api` -> 后端服务器地址

## 数据库设计

### 核心表结构

#### media_files（媒体文件表）
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

#### users（用户表）
- userId：用户ID（主键）
- username：用户名
- password：密码（加密存储）
- createTime：创建时间

## 项目特色

1. **完整的多媒体处理链**：从文件扫描、元数据提取到转码处理的完整流程
2. **现代化技术栈**：前后端均采用最新版本的流行框架
3. **容器化部署**：支持Docker一键部署，简化运维
4. **用户体验优化**：响应式设计、实时进度更新、瀑布流展示
5. **企业级功能**：JWT认证、操作日志、批量处理、任务监控
6. **高度可扩展**：模块化设计，易于功能扩展和维护

## 开发团队
- **开发者**：XinP
- **项目版本**：0.0.1-SNAPSHOT
- **项目名称**：Media
- **更新时间**：2024年

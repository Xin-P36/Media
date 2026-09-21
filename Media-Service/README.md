# Media-Service

个人媒体管理后端，基于 Spring Boot 3 提供媒体扫描、分类、整理、转码和用户配置接口。数据使用 SQLite，视频处理依赖本机或容器中的 FFmpeg。

## 技术栈

- Java 17
- Spring Boot 3.5
- MyBatis-Plus
- SQLite
- JWT（jjwt）
- FFmpeg / FFprobe
- Thumbnailator、metadata-extractor

## 功能概览

- 账号登录、登出、资料更新，JWT 鉴权
- 扫描指定目录并入库媒体文件元数据
- 树状分类的创建、修改、删除
- 文件移动、重命名、标记删除，后台异步执行
- 视频转码、缩略图生成、视频规范化
- 隐藏指定分类
- 配合 Nginx 的上传回调（`/api/media/process-upload`）
- 媒体访问路径统一为 `/content/` + 相对文件路径

## 环境要求

- JDK 17+
- Maven 3.9+（仓库已包含 `mvnw`）
- FFmpeg、FFprobe 在系统 PATH 中可用
- 生产环境建议使用 Nginx 反代 `/api` 与 `/content`

## 快速开始

1. 复制环境变量模板并修改密钥、初始密码：

```bash
cp .env.example .env
```

2. 导出环境变量后启动。Spring Boot **不会自动读取** `.env` 文件，需要先注入到进程环境中。

PowerShell：

```powershell
Get-Content .\.env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $name, $value = $_ -split '=', 2
  Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
}
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

3. 服务默认监听 `http://localhost:8080`。首次启动会在工作目录生成 `media_manager.db`，并创建管理员账号。

数据库文件一旦存在，后续启动不会再次执行 `schema.sql`，因此 `MEDIA_INITIAL_*` 只在第一次初始化时生效。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 端口 |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:media_manager.db` | SQLite JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` | 空 | SQLite 通常不需要 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | SQLite 通常不需要 |
| `MEDIA_SYSTEM` | `linux` | `linux` 或 `windows`，影响 FFmpeg 进程启动方式 |
| `MEDIA_SECRET_STRING` | `please-change-this-jwt-secret-key` | JWT 签名密钥，生产环境必须替换，建议不少于 32 个字符 |
| `MEDIA_INITIAL_ACCOUNT` | `root` | 首次初始化的管理员账号 |
| `MEDIA_INITIAL_PASSWORD` | `please-change-this-password` | 首次初始化的管理员密码 |
| `MEDIA_INITIAL_NICKNAME` | `Admin` | 首次初始化的显示昵称 |

对应配置位于 `src/main/resources/application.yml`，全部通过 `${ENV:default}` 注入，仓库中不再存放真实密钥。

## Docker

先准备 `.env`，再构建运行：

```bash
cp .env.example .env
docker compose up -d --build
```

或手动运行：

```bash
docker build -t media-service .
docker run -d --name media-service `
  --env-file .env `
  -p 8080:8080 `
  -v ${PWD}/data:/data `
  media-service
```

容器工作目录为 `/data`，SQLite 数据库、扫描产生的媒体文件和缩略图都会写到该目录，请挂载持久化卷。

镜像已安装 FFmpeg。容器内 `MEDIA_SYSTEM` 应保持为 `linux`。

## 接口一览

除登录接口外，请求头需携带 `token`。

### 用户 `/api/user`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/user/login` | 登录，返回用户信息与 JWT |
| DELETE | `/api/user/logout` | 登出，清除服务端 token |
| PUT | `/api/user/update` | 更新账号、昵称、密码、头像、背景和缩略图参数 |

### 媒体 `/api/media`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/media/scan/start` | 扫描目录，参数 `path` 为相对项目根目录的路径 |
| GET | `/api/media/scan/progress` | 查询扫描进度 |
| POST | `/api/media/scan/cancel` | 取消扫描 |
| GET | `/api/media/list` | 分页查询，支持 `toolId`、`page`、`pageSize`、`keyword` |
| POST | `/api/media/process-upload` | 处理 Nginx 上传后的临时文件 |
| POST | `/api/media/move` | 提交移动/重命名任务 |
| POST | `/api/media/delete` | 标记删除 |
| POST | `/api/media/transcode` | 提交视频转码任务 |

### 分类 `/api/tool`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/tool/create` | 创建分类 |
| GET | `/api/tool/tree` | 获取分类树 |
| PUT | `/api/tool/update` | 更新分类 |
| DELETE | `/api/tool/delete/{toolId}` | 删除分类及其子内容 |

### 隐藏分类 `/api/hide-list`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/hide-list` | 当前用户隐藏的分类 |
| POST | `/api/hide-list` | 添加隐藏分类 |
| DELETE | `/api/hide-list/{id}` | 取消隐藏 |

### 后台任务 `/api/task`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/task/start` | 启动任务，`type` 为 `MOVE`、`DELETE`、`TRANSCODE`、`THUMBNAIL`、`NORMALIZE_VIDEO` |
| GET | `/api/task/progress` | 查询当前任务进度 |
| POST | `/api/task/cancel` | 取消当前任务 |

## 媒体文件访问

列表接口返回的 `fileUrl` / `thumbnailUrl` 形如 `/content/{相对路径}`。后端本身不直接托管静态媒体，生产环境需要 Nginx（或其他 Web 服务器）把 `/content/` 映射到应用工作目录。

上传接口依赖 Nginx 将文件落到临时目录后，通过请求头传递：

- `X-Temp-File-Path`
- `X-Original-File-Name`
- `X-Target-Tool-Id`（可选）

## 目录结构

```text
Media-Service
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── .env.example
└── src/main
    ├── java/org/xinp
    │   ├── controller    # REST 接口
    │   ├── service       # 业务与异步任务
    │   ├── mapper        # MyBatis-Plus
    │   ├── entity / pojo
    │   ├── config        # 拦截器、数据库初始化
    │   └── util          # JWT、FFmpeg、扫描、文件安全操作
    └── resources
        ├── application.yml
        └── schema.sql
```

# Media

个人媒体管理系统，包含 Vue 3 前端 `Media-Vue` 和 Spring Boot 后端 `Media-Service`。用于在局域网或自建服务器上浏览、分类、整理图片与视频，并支持扫描入库、异步移动删除、FFmpeg 转码和缩略图生成。

## 项目结构

```text
Media-main
├── README.md
├── .env.example
├── Media-Service     # 后端：Spring Boot + SQLite + FFmpeg
└── Media-Vue         # 前端：Vue 3 + Vite + Element Plus
```

更细的模块说明见：

- [Media-Service/README.md](Media-Service/README.md)
- [Media-Vue/README.md](Media-Vue/README.md)

## 架构说明

```text
浏览器
  │
  ├─ 静态页面 / SPA          ← Media-Vue（开发时 Vite，生产时 Nginx）
  ├─ /api/*                  ← Media-Service
  └─ /content/*              ← Nginx 映射到后端工作目录中的媒体文件
```

- 后端负责鉴权、元数据、分类和异步任务，不直接把媒体文件当静态站点托管
- 前端通过 `/api` 获取列表，通过 `/content/{相对路径}` 展示原图、视频和缩略图
- 视频转码、封面提取依赖服务器上的 FFmpeg / FFprobe

## 功能

- 登录鉴权与用户外观设置（头像、登录/主页背景、缩略图参数）
- 瀑布流浏览图片和视频
- 树状分类管理，支持隐藏分类
- 扫描目录入库
- 文件移动、重命名、删除（后台任务）
- 视频转码、缩略图、视频规范化
- 归档页预览、打包下载

## 环境要求

| 组件 | 版本/依赖 |
| --- | --- |
| 后端 | JDK 17、Maven 3.9+、FFmpeg、FFprobe |
| 前端 | Node.js 20.19+ 或 22.12+ |
| 数据 | SQLite（默认 `media_manager.db`） |
| 生产 | Nginx（反代 API 并提供 `/content`） |

## 快速开始

### 1. 配置环境变量

仓库内的配置文件不再包含真实密钥和内网 IP。先复制模板：

```bash
cp .env.example .env
cp Media-Service/.env.example Media-Service/.env
cp Media-Vue/.env.example Media-Vue/.env
```

至少修改：

- `MEDIA_SECRET_STRING`：JWT 密钥，生产环境必须换成足够长的随机串
- `MEDIA_INITIAL_PASSWORD`：首次初始化管理员密码
- 前端代理地址：默认指向 `http://localhost:8080`

`.env` 已被 gitignore，不要把真实密钥提交到仓库。

### 2. 启动后端

进入 `Media-Service`，把 `.env` 注入当前终端后启动。Spring Boot 不会自动加载 `.env` 文件。

PowerShell：

```powershell
cd Media-Service
Get-Content .\.env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $name, $value = $_ -split '=', 2
  Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
}
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
cd Media-Service
set -a && source .env && set +a
./mvnw spring-boot:run
```

默认地址：`http://localhost:8080`。首次启动会创建 SQLite 数据库和管理员账号（默认 `root` / 你在环境变量中设置的密码）。

Windows 本地运行请把 `MEDIA_SYSTEM` 设为 `windows`。

也可以使用 Docker：

```bash
cd Media-Service
docker compose up -d --build
```

容器数据目录为 `/data`，请持久化该卷。

### 3. 启动前端

```bash
cd Media-Vue
npm install
npm run dev
```

默认开发端口为 `80`。Vite 会把 `/api` 和 `/content` 代理到后端或媒体源。

生产构建：

```bash
npm run build
```

将 `Media-Vue/dist` 交给 Nginx。

### 4. 默认账号

仅在数据库文件尚不存在时生效：

| 项 | 环境变量 | 默认值 |
| --- | --- | --- |
| 账号 | `MEDIA_INITIAL_ACCOUNT` | `root` |
| 密码 | `MEDIA_INITIAL_PASSWORD` | `please-change-this-password` |
| 昵称 | `MEDIA_INITIAL_NICKNAME` | `Admin` |

已有数据库不会因修改这些变量而重置密码。

## 环境变量一览

### 后端

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 服务端口 |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:media_manager.db` | SQLite 路径 |
| `MEDIA_SYSTEM` | `linux` | `linux` 或 `windows` |
| `MEDIA_SECRET_STRING` | 占位密钥 | JWT 签名密钥 |
| `MEDIA_INITIAL_ACCOUNT` | `root` | 初始管理员账号 |
| `MEDIA_INITIAL_PASSWORD` | 占位密码 | 初始管理员密码 |
| `MEDIA_INITIAL_NICKNAME` | `Admin` | 初始昵称 |

### 前端

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VITE_DEV_PORT` | `80` | 开发服务器端口 |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | `/api` 代理目标 |
| `VITE_FILE_PROXY_TARGET` | 同 API | `/content` 代理目标 |
| `VITE_API_BASE_URL` | `/api` | Axios 基础路径 |
| `VITE_FILE_URL_PREFIX` | 空 | 媒体 URL 前缀，空表示同源 |

## 生产部署建议

使用 Nginx 同域托管前端，并反代后端、暴露媒体目录，这样前端不必写死 IP。

```nginx
server {
    listen 80;
    server_name your.domain.example;

    root /opt/media/Media-Vue/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /content/ {
        alias /opt/media/data/;
        sendfile on;
        tcp_nopush on;
    }
}
```

`/content/` 的 `alias` 必须指向后端进程工作目录（本地是启动后端时的当前目录，Docker 中是 `/data`）。后端返回的文件地址形如 `/content/分类路径/文件名`。

上传接口 `/api/media/process-upload` 设计为 Nginx 先落盘再通知后端，需要额外的 upload 模块配置；若暂不使用网页上传，可只保留浏览和扫描整理能力。

## 开发注意

- 首页扫描路径为 `TemporaryMedia`，请在后端工作目录下准备该文件夹，或修改 `Media-Vue/src/views/home.vue`
- FFmpeg 必须能在后端进程中直接调用
- 不要把 `media_manager.db`、`.env` 和真实媒体文件提交到 Git
- 修改 JWT 密钥后，已签发的登录态会全部失效

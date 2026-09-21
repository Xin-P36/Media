# Media-Vue

个人媒体管理前端，基于 Vue 3 + Vite + Element Plus，提供登录、瀑布流浏览、分类整理、文件归档和转码任务管理界面。

## 技术栈

- Vue 3
- Vite 7
- Vue Router 4
- Element Plus
- Axios
- Masonry 瀑布流
- JSZip
- Video.js（开发依赖）

## 页面与功能

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | 登录 | 账号密码登录，背景图来自用户配置 |
| `/show` | 首页瀑布流 | 按分类分页加载图片/视频，支持预览、下载、复制链接 |
| `/finishing` | 分类管理 | 维护树状分类，创建、编辑、删除 |
| `/pigeonhole` | 归档整理 | 浏览文件、移动重命名、删除、转码、打包下载 |
| `/classificationdisplay` | 分类展示 | 按指定分类展示媒体 |

首页还包含用户资料、隐藏分类、扫描 `TemporaryMedia`、启动/取消后台任务以及进度轮询。

## 环境要求

- Node.js `^20.19.0` 或 `>=22.12.0`
- npm（或其他兼容包管理器）
- 可访问的 Media-Service，以及用于提供 `/content` 媒体文件的 Nginx 或开发代理

## 快速开始

```bash
cp .env.example .env
npm install
npm run dev
```

开发服务器默认端口为 `80`，可通过 `VITE_DEV_PORT` 修改。浏览器访问 `http://localhost`。

生产构建：

```bash
npm run build
npm run preview
```

构建产物位于 `dist/`，可交给 Nginx 托管。

## 环境变量

Vite 只会把以 `VITE_` 开头的变量暴露给前端代码。请复制 `.env.example` 为 `.env` 或 `.env.local`。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VITE_DEV_PORT` | `80` | 开发服务器端口 |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | 开发环境把 `/api` 代理到后端 |
| `VITE_FILE_PROXY_TARGET` | 与 API 代理相同 | 开发环境把 `/content` 代理到媒体源 |
| `VITE_API_BASE_URL` | `/api` | Axios 基础路径 |
| `VITE_FILE_URL_PREFIX` | 空 | 媒体文件 URL 前缀。留空表示与页面同源 |

本地开发推荐保持 `VITE_FILE_URL_PREFIX` 为空，让图片和视频走 Vite 的 `/content` 代理。如果媒体由另一台机器单独提供，再填写完整地址，例如 `http://192.168.1.10`。

生产环境同样建议由 Nginx 同域反代 `/api` 和 `/content`，前端无需写死后端 IP。

## 开发代理

`vite.config.js` 会读取环境变量并配置：

- `/api` → `VITE_API_PROXY_TARGET`
- `/content` → `VITE_FILE_PROXY_TARGET`

Axios 会自动在请求头附加 `localStorage.userToken.token`。

## 目录结构

```text
Media-Vue
├── index.html
├── vite.config.js
├── .env.example
├── public/BackgroundImg    # 默认头像与背景
└── src
    ├── App.vue
    ├── main.js
    ├── config.js           # 读取 VITE_* 配置
    ├── router/index.js
    ├── utils
    │   ├── axiosRequest.js
    │   └── toolCategory.js
    └── views
        ├── login.vue
        ├── home.vue
        ├── show.vue
        ├── finishing.vue
        ├── pigeonhole.vue
        └── classificationdisplay.vue
```

## 与后端的约定

- 接口前缀为 `/api`
- 媒体 URL 为 `/content/{相对路径}`
- 未登录会跳转 `/login`
- 首页扫描入口固定请求 `/media/scan/start?path=TemporaryMedia`，请保证后端工作目录下存在该目录，或按需修改

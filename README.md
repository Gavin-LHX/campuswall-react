# Campus Wall

一个面向校园社区的全栈交流平台，使用 React、Vite、Node.js、Express 与 PostgreSQL 构建。项目支持匿名动态、表白墙、失物招领、用户账号、内容审核、角色权限、公告、举报、反馈和审核机器人提醒。

学校名称、站点名称、上线时间、前后端域名和第三方服务均通过环境变量配置；仓库不包含任何生产账号、真实域名、用户数据或分析标识。

## 功能概览

- 普通动态：游客和登录用户均可发布，可选择匿名、附件、标签和投票。
- 表白墙：通过审核的便签以粒子爱心展示，支持便签轮播和详情查看。
- 失物招领：登录后发布，支持寻物/招领状态、联系方式和图片。
- 用户系统：任意合规用户名注册、Cookie 会话、个人资料、头像压缩、收藏和通知。
- 内容审核：普通帖子与表白墙使用独立队列、独立计数和独立深链接。
- 角色权限：`user`、`reviewer`、`admin`、`super_admin` 四级角色。
- 运营能力：公告、评论管理、举报、反馈、回收站、审计日志和社区开关。
- 审核提醒：可选飞书或企业微信机器人，使用 PostgreSQL outbox 保证可靠投递。
- 响应式界面：桌面与移动端布局、浅色/深色主题、减少动态偏好和无障碍状态。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | React、React Router、Vite、Three.js、Fetch API |
| 后端 | Node.js、Express、PostgreSQL、Sharp |
| 鉴权 | 服务端 Cookie 会话、scrypt 密码哈希、角色权限 |
| 部署 | Nginx、systemd、Cloudflare Pages（可选） |
| 测试 | Node.js test runner、GitHub Actions |

## 目录结构

```text
campuswall-react/
├── frontend/                  # React + Vite 前端
├── backend/                   # Express API、存储与测试
├── deploy/                    # 通用 systemd / Nginx 模板
├── compose.yml                # 本地 PostgreSQL
├── wrangler.jsonc             # 可选 Cloudflare Pages 配置
├── README_BAOTA_DEPLOY.md     # 宝塔部署示例
└── HANDOFF.md                 # 通用运维交接清单
```

## 本地启动

### 要求

- Node.js 22 或更高版本
- npm
- PostgreSQL 14 或更高版本，或 Docker Compose

### 安装

```bash
git clone https://github.com/Gavin-LHX/campuswall-react.git
cd campuswall-react
npm install
cp backend/.env.example backend/.env
```

Windows PowerShell 可使用：

```powershell
Copy-Item backend/.env.example backend/.env
```

开发用 PostgreSQL 默认配置与 `compose.yml` 一致。请勿在生产环境沿用示例密码。

```bash
npm run db:up
npm run db:wait
npm run dev
```

默认地址：

- 前端：`http://localhost:5173`
- 后端：`http://127.0.0.1:5412`
- 健康检查：`http://127.0.0.1:5412/health`

也可以使用一个命令启动数据库和开发服务：

```bash
npm run dev:local
```

## 环境变量

后端完整示例见 [`backend/.env.example`](backend/.env.example)。生产环境至少应配置：

```dotenv
NODE_ENV=production
SCHOOL_NAME=示例学校
SITE_NAME=示例学校校园墙
APP_NAME=校园墙 API
SITE_LAUNCHED_AT=

SECRET_KEY=<long-random-secret>
DATABASE_URL=postgresql://<user>:<password>@127.0.0.1:5432/<database>

SESSION_COOKIE_SECURE=true
PUBLIC_SITE_URL=https://wall.example.com
ALLOWED_ORIGINS=https://wall.example.com
```

`SITE_LAUNCHED_AT` 可留空。未配置时首页不会显示上线时长。

分离部署前端和 API 时，复制 `frontend/.env.production.example` 为 `frontend/.env.production`，或在 CI 中注入：

```dotenv
VITE_API_BASE_URL=https://api.wall.example.com
VITE_STATIC_URL=https://api.wall.example.com/static/
VITE_UMAMI_WEBSITE_ID=
```

只有显式设置 `VITE_UMAMI_WEBSITE_ID` 时，生产构建才会加载 Umami；默认不发送任何分析数据。

## 创建首个超级管理员

系统不会附带默认管理账号或密码。先注册一个普通账号，再在服务器上执行：

```bash
npm run admin:reset-password -- <username>
```

脚本会交互式设置密码，并将目标账号提升为 `super_admin`。生产环境请通过安全终端执行，不要把密码写入命令历史、文档或 Git。

后续角色可由超级管理员在“用户与权限”页面分配：

- `reviewer`：审核全部普通帖子和表白便签、发布公告。
- `admin`：拥有内容、用户、公告、举报和反馈管理能力。
- `super_admin`：拥有全部权限，并可分配管理角色。

所有审核员的审核权限完全一致。普通帖子与表白墙只是界面和队列分开，不是权限分开。

## 发布与审核规则

| 内容 | 游客 / 普通用户 | reviewer / admin / super_admin |
| --- | --- | --- |
| 普通动态 | 待审核 | 直接公开 |
| 表白便签（精确标签 `表白`） | 待审核 | 直接公开 |
| 结构化失物招领 | 登录后直接公开 | 直接公开 |

结构化 `lost_found` 始终优先归入普通帖子侧，即使标签中同时存在 `表白`。审核员可以处理全部队列，也可以审核自己提交后被退回的内容。

审核入口：

- `/admin/wall`：普通帖子审核
- `/admin/confessions`：表白墙审核
- `/admin`：两类待审统计与通用管理入口

## 审核机器人（可选）

机器人配置只允许保存在后端环境中：

```dotenv
MODERATION_NOTIFY_ENABLED=true
MODERATION_NOTIFY_FEISHU_WEBHOOK=
MODERATION_NOTIFY_FEISHU_SECRET=
MODERATION_NOTIFY_WECOM_WEBHOOK=
```

可以只配置一个平台。机器人消息只包含内容编号、系统分类、附件/投票标记、提交时间、全站待审合计和后台链接，不包含正文、用户标签、身份、联系方式或附件路径。“全站待审”是普通帖子与表白墙两个队列的合计；普通帖子通知进入 `/admin/wall`，表白便签通知进入 `/admin/confessions`，混合摘要进入 `/admin`。

## 质量检查

```bash
npm run test:backend
npm --workspace backend run check
npm run build
```

GitHub Actions 还会启动临时 PostgreSQL，检查健康接口、CORS 和前端构建。

## 生产部署

### 同源部署

1. 构建前端：`npm run build`
2. 使用 Nginx 提供 `frontend/dist`
3. 将 `/api`、`/static` 和 `/health` 反向代理到 `127.0.0.1:5412`
4. 使用 systemd 或其他进程管理器运行后端

通用模板位于 `deploy/nginx-campuswall.conf` 和 `deploy/campuswall.service`。替换其中的示例域名、目录、证书路径与服务用户后再启用。

### 分离部署

前端可以部署到 Cloudflare Pages，后端继续由自有服务器提供：

```bash
npm run build
npx wrangler pages deploy frontend/dist --project-name <your-pages-project> --branch main
```

生产 API 需要：

- 使用 HTTPS；
- `ALLOWED_ORIGINS` 精确包含前端 Origin；
- `PUBLIC_SITE_URL` 指向前端站点；
- Nginx 不得用 `root`/`alias` 直接公开受审核状态约束的上传目录。

部署和交接细节见 [`HANDOFF.md`](HANDOFF.md) 与 [`README_BAOTA_DEPLOY.md`](README_BAOTA_DEPLOY.md)。

## 安全说明

- 不要提交 `.env`、`.wrangler/`、证书、私钥、数据库、日志、上传文件或运行时 JSON。
- 生产启动会拒绝默认 `SECRET_KEY` 和默认 PostgreSQL 开发密码。
- 管理员密码不会由仓库预置，也不会根据用户名自动授予角色。
- 上传附件只能通过后端鉴权路由访问，避免绕过待审核、下架和失物招领登录规则。
- 发现漏洞时请遵循 [`SECURITY.md`](SECURITY.md)。

## 许可证与贡献

提交变更前请运行测试和构建，并确保示例中只使用 `example.com`、测试账号和占位密钥。若仓库维护者添加许可证，请以仓库根目录许可证文件为准。

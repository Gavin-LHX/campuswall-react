# Campus Wall 运维交接指南

本文是可复用的交接模板，不描述任何特定学校或生产环境。请在团队的私密文档或密码管理器中记录真实域名、服务器地址、账号和密钥，不要回填到 Git。

## 1. 交接边界

仓库可以包含：

- 应用源代码、测试和数据库迁移脚本；
- 使用 `example.com` 的部署模板；
- 空的环境变量示例；
- 不含用户数据的操作流程。

仓库禁止包含：

- SSH、宝塔、Cloudflare、数据库或机器人凭据；
- `.env`、证书、私钥、OAuth 状态、Webhook 完整地址；
- 用户账号、密码哈希、日志、帖子、公告、数据库和上传文件；
- 真实学校内部网络、服务器 IP、联系人或未公开域名。

## 2. 建议架构

```text
Browser
  ├─ https://wall.example.com       -> React frontend
  └─ https://api.wall.example.com   -> Nginx -> 127.0.0.1:5412
                                                |
                                                └-> PostgreSQL
```

也可以同源部署，由 `wall.example.com` 同时提供前端，并把 `/api`、`/static` 和 `/health` 反向代理到后端。

前后端分离时必须同步配置：

- 前端 `VITE_API_BASE_URL` 和 `VITE_STATIC_URL`；
- 后端 `PUBLIC_SITE_URL` 和 `ALLOWED_ORIGINS`；
- HTTPS、Cookie Secure 属性和 Nginx 代理头；
- 防火墙只向受信任代理开放源站端口。

## 3. 运行组件

| 组件 | 建议 |
| --- | --- |
| Node.js | 22 LTS 或更高 |
| PostgreSQL | 14 或更高 |
| Nginx | 静态站点、TLS、反向代理 |
| systemd | 后端单实例进程管理 |
| Cloudflare Pages | 可选的前端静态托管 |

仓库模板默认约定：

- 项目目录：`/opt/campus-wall`
- 后端环境文件：`/etc/campus-wall/backend.env`
- API 监听：`127.0.0.1:5412`
- 运行用户：`campuswall`

这些值都是示例，可以修改；修改后要同步更新 systemd、Nginx、备份和监控配置。

## 4. 首次部署

### 4.1 安装与构建

```bash
git clone https://github.com/Gavin-LHX/campuswall-react.git /opt/campus-wall
cd /opt/campus-wall
npm ci
```

同源部署可直接构建。前后端分离时，必须先复制并编辑前端生产配置（也可在 CI 中注入相同变量）：

```bash
cp frontend/.env.production.example frontend/.env.production
# 将 VITE_API_BASE_URL 和 VITE_STATIC_URL 替换为真实 API HTTPS 地址
npm run build
```

### 4.2 配置 PostgreSQL

创建独立数据库与最小权限用户。生产密码应由密码管理器生成并保存在环境文件中。

```dotenv
DATABASE_URL=postgresql://<user>:<password>@127.0.0.1:5432/<database>
```

不要在命令、截图或工单中粘贴真实连接串。

### 4.3 配置后端

从 `backend/.env.example` 复制私有环境文件：

```bash
install -d -m 0750 /etc/campus-wall
install -m 0600 backend/.env.example /etc/campus-wall/backend.env
```

至少替换：

```dotenv
NODE_ENV=production
SCHOOL_NAME=<school name>
SITE_NAME=<site name>
APP_NAME=<service name>
SITE_LAUNCHED_AT=<optional ISO-8601 timestamp>
SECRET_KEY=<long random value>
DATABASE_URL=<private connection string>
SESSION_COOKIE_SECURE=true
PUBLIC_SITE_URL=https://wall.example.com
ALLOWED_ORIGINS=https://wall.example.com
```

`SITE_LAUNCHED_AT` 留空时，首页不显示上线时长。

### 4.4 准备运行目录

先检查脚本内容，再以 root 执行：

```bash
cd /opt/campus-wall
sudo bash deploy/prepare-runtime.sh /opt/campus-wall
```

脚本只创建空运行目录和必要的空 JSON 文件，不会创建默认管理员。

### 4.5 启动 systemd 服务

```bash
sudo install -m 0644 deploy/campuswall.service /etc/systemd/system/campuswall.service
sudo systemctl daemon-reload
sudo systemctl enable --now campuswall.service
sudo systemctl status campuswall.service
curl -fsS http://127.0.0.1:5412/health
```

### 4.6 配置 Nginx

- 同源部署参考 `deploy/nginx-campuswall.conf`。
- 独立 API 域名参考 `deploy/nginx-campuswall-api.conf`。
- 替换所有 `example.com`、证书路径与项目目录。
- 使用 Cloudflare 代理时，可将 `deploy/cloudflare-realip.conf` 安装到 `/etc/campus-wall/cloudflare-realip.conf`，审核内容后再取消所用 Nginx 模板中的对应注释；不使用时保持注释即可。
- `nginx -t` 成功后再 reload。

上传附件和缩略图必须经后端路由访问。不要用 `root` 或 `alias` 直接暴露 `backend/static`，否则会绕过登录、审核、下架和删除规则。

## 5. 管理员初始化

系统不附带任何默认账号。先通过前台注册普通账号，再执行：

```bash
sudo systemd-run --unit=campuswall-admin-reset --pty --wait --collect \
  --property=User=campuswall \
  --property=Group=campuswall \
  --property=WorkingDirectory=/opt/campus-wall/backend \
  --property=EnvironmentFile=/etc/campus-wall/backend.env \
  --property=Environment=NODE_ENV=production \
  /usr/local/lib/campuswall/node scripts/reset-admin-password.js <username>
```

该临时 unit 会读取与正式后端相同的私有环境文件。脚本会交互式设置密码并提升目标账号；确认终端没有录屏、命令日志中没有密码，完成后通过后台分配其他角色。

角色原则：

- 审核员拥有完全相同的审核权限，可处理全部普通帖子和表白便签；
- 管理员不能授予超级管理员权限；
- 超级管理员可分配角色，但系统至少保留一位启用的超级管理员；
- 不要依赖固定用户名决定角色。

## 6. 发布流程

### 6.1 后端

```bash
cd /opt/campus-wall
git fetch origin main
git pull --ff-only origin main
npm ci
npm run test:backend
npm --workspace backend run check
sudo systemctl restart campuswall.service
curl -fsS http://127.0.0.1:5412/health
```

数据库结构由服务初始化流程维护。涉及迁移时，先在备份副本和预发布环境验证。

### 6.2 前端

```bash
cd /opt/campus-wall
npm ci
# 分离部署时，先确认 frontend/.env.production 或 CI 变量仍指向当前 API 域名
npm run build
```

同源部署时原子替换静态构建目录并 reload Nginx。Cloudflare Pages 可使用：

```bash
npx wrangler pages deploy frontend/dist --project-name <project> --branch main
```

Wrangler 登录状态位于本机配置目录，不应复制到服务器或提交到仓库。

## 7. 验收清单

每次发布至少验证：

- `/health` 返回 `{"status":"ok"}`；
- 首页和所有 SPA 深链接返回前端页面；
- 注册、登录、退出和 Cookie 刷新保持登录正常；
- 游客/普通用户普通动态进入帖子审核队列；
- 游客/普通用户表白便签进入表白墙审核队列；
- 结构化失物招领要求登录且直接公开；
- `reviewer`、`admin`、`super_admin` 发布内容按规则直接公开；
- `/admin/wall` 与 `/admin/confessions` 的计数、搜索和分页互不混杂；
- 所有审核员可以处理两个队列；
- 公告、举报、反馈、回收站和权限管理正常；
- 待审核、下架或删除内容及附件不能从公开接口访问；
- 手机端导航、弹窗、上传、审核按钮和深色主题可用。

## 8. 审核机器人

机器人 Webhook 和签名密钥只放在后端环境文件中。启用前确认：

- `PUBLIC_SITE_URL` 使用 HTTPS；
- 机器人只接收隐私安全的元数据；
- “全站待审”计数是普通帖子与表白墙两个审核队列的合计；
- 普通帖子链接到 `/admin/wall`；
- 表白便签链接到 `/admin/confessions`；
- 混合摘要链接到 `/admin`；
- 群成员范围符合学校的隐私和运营要求。

## 9. 备份与恢复

至少备份：

- PostgreSQL 数据库；
- `backend/static/uploads`、`avatars` 和其他用户媒体；
- `backend/help/*.json`、`backend/admin_log.json`、`backend/manage_message.json` 和 `backend/static/notice.json` 等仍由文件存储的工单、审计与兼容数据；
- 私有环境文件；
- TLS 证书（若证书管理方式需要）；
- 平台设置和公告数据。

备份应加密、限制访问并定期做恢复演练。不要把备份放在 Git 工作树或公开对象存储中。

恢复顺序建议：

1. 准备新的数据库和运行用户；
2. 恢复数据库；
3. 恢复媒体、工单与其他运行文件并校验属主和权限；
4. 恢复环境变量和证书；
5. 启动后端并检查 `/health`；
6. 部署前端；
7. 按完整验收清单回归。

## 10. 故障排查

```bash
systemctl status campuswall.service
journalctl -u campuswall.service -n 200 --no-pager
curl -i http://127.0.0.1:5412/health
nginx -t
pg_isready -h 127.0.0.1 -p 5432
```

常见原因：

- PostgreSQL 未启动或连接串错误；
- `SECRET_KEY`、数据库密码仍是示例值，生产保护拒绝启动；
- Nginx 代理路径或 `X-Forwarded-*` 头配置错误；
- `ALLOWED_ORIGINS` 与浏览器 Origin 不完全一致；
- 前端 API 基址仍指向本地地址；
- 服务用户无权写入上传、日志或运行数据目录；
- Cloudflare 缓存仍引用旧构建。

## 11. 离职或换届交接

在私密渠道完成以下事项：

- 转移 GitHub、DNS、Pages、服务器和数据库的最小必要权限；
- 轮换离任人员接触过的 SSH、数据库、机器人和管理密码；
- 确认至少两人可以完成备份恢复和紧急回滚；
- 移除离任人员账号和个人访问令牌；
- 核对域名续费、证书续期、磁盘和备份告警负责人；
- 不在公开仓库记录真实凭据或人员联系方式。

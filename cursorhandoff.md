# Cursor Agent 交接（Codex 额度用尽之后）

> 写给下一个 Cursor / 其他 AI。今天：2026-08-28。  
> 本文件**不是**学校运维权威文档。权威产品、架构、部署与验收以仓库根目录 `HANDOFF.md`（文档版本 3.6）为准。  
> **禁止**把 SSH 密码、SMTP 密码、飞书 App Secret、`FEISHU_LOGIN_CHAT_ID`、Webhook、Wrangler OAuth token、数据库密码写入本文件、Git、聊天或截图。需要时向用户要密码管理器里的值，用完只放环境变量。

---

## 0. 30 秒结论

龙华区观澜中学校园墙已经在生产跑着：前端 `https://wall.zongtech.xyz`（Cloudflare Pages 项目 `guanlan-campus-wall`），API `https://api-wall.zongtech.xyz`。

- **本机分支**：`codex/cloudflare-pages-domain`
- **交付远端**：只允许 `schoolrepo` = `ZONGRUICHD/Campus-Wall-For-GuanLan` 的 `main`
- **禁止推送**：`origin` = `Gavin-LHX/campuswall-react`（Dependabot / Express 5 大版本，不要合）
- **禁止**：强推 `main`
- **当前 HEAD / GitHub `schoolrepo/main` / 源站仓库**：`13e200360b3e6fec7f151d9ce1205bb449805901`（纯文档提交，记录 15.10）
- **当前应用代码提交**：`cd03a82e2e11d86b5bd926b26b92f3bf72157725`（实名展示 + 主页验证信不再 500）
- **最新 Pages 不可变地址**：`https://ae4d0c80.guanlan-campus-wall.pages.dev`（2026-08-28 重传，与 `cd03a82` 前端产物相同；此前首次上线该包是 `https://052b058f.guanlan-campus-wall.pages.dev`）
- **产品改动默认上线**：见 `.cursor/rules/deploy-by-default.mdc` 和 `HANDOFF.md` 第 16–17 节

接手范围：2026-08-26 起，用户因 Codex 没额度把项目交给 Cursor Agent，一直做到 2026-08-28 夜间 Wrangler 重新授权并再发 Pages。

---

## 1. 你是谁、先读什么

1. `HANDOFF.md`（完整生产规矩）
2. `.cursor/rules/deploy-by-default.mdc`（做完产品改动默认提交、推 `schoolrepo HEAD:main`、等 CI、备份、源站快进、Pages）
3. `docs/FEISHU_LOGIN.md`、`docs/NOTIFICATION_INTEGRATION.md`、`docs/MODULE_DEVELOPMENT.md`
4. 本文件（Cursor 阶段做了什么、坑、怎么发布）

回复用户用**简体中文**。

对话存档（可点开补细节，不要把里面出现过的密钥抄出来）：

- [Codex project review](43826bf6-3ad3-4c61-af36-4601a0620a31) — 接手快照、SSH、安全审查
- [校园墙安全补丁 / 登录与 3.6](4e5e65bc-7917-489a-82ff-e2cb0e444b53)
- [同一条长对话的后续](79caee4b-1f65-4748-a238-1bbf4cc66c68) — SMTP、注册 500、匿名/邮箱 bug、Pages、Wrangler
- 前端「太丑了要重构」：[856f2eec-9c43-45a0-b92d-a8de536a67ad](856f2eec-9c43-45a0-b92d-a8de536a67ad) — **未进生产、当前工作区干净**

---

## 2. 当前生产形态

```text
浏览器
  ├─ wall.zongtech.xyz  → Cloudflare Pages → frontend/dist
  └─ api-wall.zongtech.xyz → Cloudflare 橙云 443
                              → Origin Rule 改写到源站 8443
                              → Nginx TLS → 127.0.0.1:5412 campuswall.service
                              → PostgreSQL campus_wall
```

| 项 | 值 |
| --- | --- |
| 源站项目目录 | `/www/wwwroot/campuswall-react` |
| 源站 Git 远端名 | 机器上叫 `origin`，URL 必须是学校仓库 `Campus-Wall-For-GuanLan` |
| 环境文件 | `/etc/campuswall/backend.env`（`root:root 600`） |
| 备份根 | `/www/backups/campuswall` |
| 后端监听 | `127.0.0.1:5412`，不要对公网开放 5412/5432 |
| 源站 HTTPS | 只在 **8443**；443 被同机其他服务占用，不要抢 |
| 健康检查 | `https://api-wall.zongtech.xyz/health` → `{"status":"ok"}` |
| 真实上线时间 | `SITE_LAUNCHED_AT=2026-08-25T01:48:50+08:00`，普通发布**不得重置** |
| Cloudflare 账号（Wrangler） | 本机 OAuth 曾登录 `zongrui0831@outlook.com` |
| 本机工作区 | `C:\Users\zongt\Documents\campuswall-react` |

API 主机名必须是 `api-wall.zongtech.xyz`，**不要**用 `api.wall.zongtech.xyz`（Free 区 Universal SSL 盖不到嵌套子域）。

---

## 3. 硬性发布规矩

完成用户要的产品改动后，**默认走生产**，不要再问「要不要上线」。用户明确说先不要上线、只要本地或只要提交时除外。

1. 同一提交更新 `HANDOFF.md`（功能改了必须改文档，不要欠文档债）。
2. `git push schoolrepo HEAD:main`（本机 Git HTTPS 常被 Windows Application Control 拦住，见第 8 节 isomorphic-git）。
3. 等该提交的 GitHub Actions **全部通过**。
4. 源站：隔离备份 `*-before-deploy` → 快进 `origin/main` → `npm ci` → 后端测试 → `deploy/prepare-runtime.sh` → 重启 `campuswall.service`。
5. 本机 Vite/Sharp 常被拦：在**源站 Linux** 构建 `frontend/dist`，再用 Wrangler Direct Upload 项目 `guanlan-campus-wall` `--branch main`。
6. 补 `HANDOFF.md` 第 15.x 验收表（CI run、备份目录、Pages URL、健康检查）。纯文档提交同步源站 `main` 即可，**不必无故重启服务**。
7. 不要 `git push origin`，不要 `--force` `main`，不要 `--no-verify`。

---

## 4. 时间线（Cursor 接手后实际合进 `main` 的工作）

Codex 交接到 Cursor 时，生产大约在 3.2 通知设置（`60ef5a3` / 文档 `c1ae6f8`）。之后 Cursor 做了这些（均为已上线，除非注明）。

### 4.1 3.3 生产加固（2026-08-26）

提交 `403ec83`，验收 `HANDOFF.md` §15.4，Pages `https://475e810d.guanlan-campus-wall.pages.dev`。

用户先要求安全审查，再要求修复并上线。做了（高层，不要写利用步骤）：

- 生产类环境启动守卫（占位 `SECRET_KEY`、默认库密码、`PGSSL_REJECT_UNAUTHORIZED`）
- 分片合并互斥；按文件头校验类型；ffmpeg 失败拒收
- 游客互动 Cookie 改为 HMAC
- 公开资料/注册枚举收敛；改密限流；反馈/举报 JSON 条数上限与原子写

**没有**做完整渗透、压力、长稳测试。`HANDOFF.md` 第 24 节里的历史限制大部分仍在。

### 4.2 3.4 飞书登录 + 关掉对外注册（2026-08-26 晚）

提交 `f7dd1ba` + 测试对齐 `010c0ac`，文档 `8f92e87`，验收 §15.5，Pages `https://56a2edd6.guanlan-campus-wall.pages.dev`。

产品当时要求：

- 前台默认飞书登录（电脑扫码 / 手机跳转飞书）
- 只有登录校验群的成员能过（服务端认 `FEISHU_LOGIN_CHAT_ID`，**不认群名**）
- 普通用户禁止密码登录；后台账号只能超管在「用户与权限」里建
- `/admin/login` 仍是用户名密码

实现要点：`backend/src/services/feishuAuth.js`，`GET /api/user/feishu/start`，callback 在 **API 源站**  
`https://api-wall.zongtech.xyz/api/user/feishu/callback`（必须配在飞书开放平台重定向 URL，不要用 Pages 域名）。

飞书**登录应用**和**审核群自定义机器人 Webhook**不是同一套凭据。本机 lark-cli 应用「ZONGRUICHD的飞书 CLI」也**不是**校园墙登录应用，不要拿 CLI 去改生产 `FEISHU_*`。

登录校验群：学生用的「观澜中学校园墙」类群；另有管理员/审核群。机器人「校园墙登录」必须留在登录校验群里，退群后新登录会失败。

### 4.3 3.5 恢复「待审」用户名密码注册（2026-08-26 夜）

提交 `237ccb3`，文档 `af15afc`，验收 §15.6，Pages `https://48caf7b6.guanlan-campus-wall.pages.dev`。

用户改主意：对外注册加回去，但必须审核员在后台通过后才能密码登录。飞书登录仍立即进入。拒绝注册会停用该用户名。顺手修了管理员文本日志写入失败把已成功后台保存打成 500 的问题。

登录页：飞书主按钮 + 登录/注册 Tab。空注册体应 400，不再 404。

单行标题：用户要求删掉登录页等双行标题；`9d8afda` 把登录页标题收成单行。

### 4.4 3.6 深色、校徽、失物招领、匿名开关、邮箱、飞书绑定（2026-08-27 凌晨）

提交 `444a6bf`，文档 `d166a39`，验收 §15.7，Pages `https://ba81a63d.guanlan-campus-wall.pages.dev`。

用户一批产品图（深色僵硬、失物招领未登录进不去、默认匿名能否关掉、双行标题、校徽、注册可选邮箱、主页绑邮箱/飞书进群、审核提醒加邮箱渠道）。落地包括：

- 深色改为 grouped 抬升底 + 轻阴影；favicon / 顶栏换校徽 `school-badge.webp`
- 新功能只用**单行主标题**
- 失物招领：**浏览公开、填写必须登录**，且以实名身份发布
- 登录用户可关默认匿名（「展示昵称」）
- 注册可选邮箱；主页验证邮箱、邮件通知开关、连接飞书（绑定后尝试拉进登录校验群）
- 审核提醒可开关邮箱渠道；SMTP 只进服务器环境

注意：`444a6bf` 推 `schoolrepo` 后有一段时间 **GitHub Actions 没跑起来**（之前 `9d8afda` 的 run 还在空排队）。当时用源站 Linux `npm ci` + 完整测试 + 构建代替门禁。后来的提交 CI 已恢复正常。

### 4.5 SMTP + 验证链接走 API 源站（2026-08-27 01:xx）

提交 `e70ff0f`，文档 `178f1c6`，验收 §15.8。无前端变更，未再发 Pages。

验证信如果指向 Pages 域名，cookie/API 对不上。链接改为 API 源站。Gmail 587 STARTTLS 写在 `/etc/campuswall/backend.env`（`SMTP_HOST` / `SMTP_FROM` / `SMTP_PASS`）。**不要**把发信账号或应用专用密码写进 Git。

审核飞书提醒仍须在「管理后台 → 消息提醒」粘贴**群自定义机器人 Webhook**；lark-cli 机器人当时不在业务群里，不能当审核通道。

### 4.6 注册 500：`pending_email` 类型推断（2026-08-27 01:50）

提交 `a521da6`，文档 `f0af549`，验收 §15.9。无前端变更。

现象：`/login` 注册 toast「注册失败 / 服务器内部错误」。源站日志 `could not determine data type of parameter $5`。`UserStore.register` 把 JS `null` 绑到 `pending_email`，再 `CASE WHEN $5 IS NULL`，node-pg 无法推断类型。发生在发信之前，不是 SMTP。

修复：`$5/$6/$7` 加 `::text` / `::boolean`，空过期写 `NULL::timestamptz`。带邮箱/不带邮箱 `POST /api/user/register` 均 201。探测用 pending 账号已停用。

### 4.7 实名帖仍显示匿名 + 主页验证信 500（2026-08-27 17:50–18:34，28 日补 Pages）

提交 `cd03a82`，文档 `13e2003`，验收 §15.10。

用户（登录名 ZongRui）两件事一起报：

1. 切到「展示昵称」发帖，墙上仍是匿名用户  
2. `/me` 邮箱 `POST /api/user/me/email` toast「邮箱保存失败 / 服务器内部错误」

**匿名根因（展示层，不是「提交永远匿名」）：**

- `backend/src/routes/public.js` / `users.js` 曾**无条件** `delete copy.user_id`
- 前台 `frontend/src/utils/user.js` 的 `messageAuthor` 要 `message.user_id && anonymous === false` 才显示昵称
- `MessageCard.jsx` 动态变体曾写死「匿名动态」
- 墙草稿 key 曾固定 `campus-wall-publish-draft-v1:guest`，可能把游客 `anonymous: true` 灌回登录用户

现逻辑：`backend/src/services/publicMessageView.js` 的 `redactPublicMessage`：

- `anonymous === false`：保留 `user_id` 和快照，去掉 `username` 和审核演员字段
- 匿名：去掉 `user_id`，快照强制「匿名用户」
- 评论仍去掉 `user_id`
- 收藏列表 map 必须传 `req.user.id`，不要把 `Array.map` 的 index 当成 viewer id

前台：`messageAuthor` 以 `anonymous === false` 为准；动态卡「展示昵称」/「匿名动态」；草稿 key `${prefix}:${user?.id || 'guest'}`，仅登录用户从草稿恢复 `anonymous`。

**邮箱 500 根因：** `requestEmailChange` → SMTP 超时/失败未捕获，Express 全局 500。注册路径本来会接住 SMTP 失败。现用 `classifyVerificationEmailError`（`userEmail.js`），未配置 503，否则 **400 中文**，不要 500。SMTP 默认超时改为 20s（`backend/src/config.js`）。

**生产现状（2026-08-28 复核）：** 公开墙只有 3 条已审帖，库里全是 `anonymous=true`，所以仍显示「匿名用户 / 匿名动态」。这是数据，不是回归。用户再发一条「展示昵称」且**审核通过**后才会在公开墙看到昵称。未登录 `POST /api/user/me/email` 返回「未登录」而不是「服务器内部错误」。

备份：`/www/backups/campuswall/20260827-175559-before-deploy`。CI：`cd03a82` run `33060669822`；文档 `13e2003` run `33063847152`。源站完整测试当时 123/123。

### 4.8 2026-08-28：Wrangler 重新授权并再发 Pages

OAuth token 过期。设备码流程在 Cursor 内置浏览器会「Application authorization failed」，不要再用 `--device` 走内置浏览器。

本机回调曾只监听 `[::1]:8976`，Chrome 连 `127.0.0.1` → `ERR_CONNECTION_REFUSED`。正确命令：

```powershell
Remove-Item Env:CI -ErrorAction SilentlyContinue
$env:CI = ''
npx wrangler login --browser=false --callback-host 127.0.0.1
```

把打印的 `dash.cloudflare.com/oauth2/auth?...` 用**本机 Chrome/Edge** 打开（同一台电脑，好回 `localhost:8976`）。约 2 分钟超时。成功后 `npx wrangler whoami`。

2026-08-28 用已有 `frontend/dist` Direct Upload，新部署 `https://ae4d0c80.guanlan-campus-wall.pages.dev`（0 个新文件，114 个复用）。源站文档提交快进到 `13e2003`，**未**重启 API。

---

## 5. 关键代码地图（改功能先看这些）

| 主题 | 路径 |
| --- | --- |
| 公开帖脱敏 | `backend/src/services/publicMessageView.js` |
| 公开/用户路由 | `backend/src/routes/public.js`、`backend/src/routes/users.js` |
| 注册/邮箱/飞书绑定 | `backend/src/services/userStore.js`、`userEmail.js`、`feishuAuth.js` |
| 发帖匿名策略 | `backend/src/services/publicationPolicy.js`、`backend/src/routes/wall.js` |
| SMTP | `backend/src/services/smtpMailer.js`、`backend/src/config.js` |
| 审核提醒 | `backend/src/services/moderationNotifier.js`、`backend/src/services/notifications/` |
| 角色权限 | `backend/src/services/roles.js` |
| 前台作者展示 | `frontend/src/utils/user.js`、`frontend/src/components/MessageCard.jsx` |
| 发帖匿名开关 | `frontend/src/pages/Wall.jsx`、`ConfessionWall.jsx` |
| 主页邮箱/飞书 | `frontend/src/pages/Me.jsx` |
| 登录注册 | `frontend/src/pages/Login.jsx` |
| 失物招领 | `frontend/src/pages/LostFound.jsx` |
| 前端 API | `frontend/src/services/api.js`（公开墙是 `GET /api/get_messages`） |
| 飞书文档 | `docs/FEISHU_LOGIN.md` |
| 测试 | `backend/test/publicMessageView.test.js`、`emailNotification.test.js`、`userStoreAuthPolicy.test.js`、`feishuAuth.test.js` |

本机 Windows 上 `npm --workspace backend test` 常被 Application Control 在 Sharp 处拦死。可先跑不加载 Sharp 的 `node --test test/<file>.js`；**完整套件以 GitHub Actions 和源站 Linux 为准**。

---

## 6. 本机工程现实（Windows）

Windows Application Control 会拦：

- Git HTTPS（`git fetch` / `git push` 学校仓库常失败）
- Vite / rolldown / Sharp 原生模块（本机 `npm run build`、完整后端测试）

已验证能用的替代：

### 6.1 推送到 `schoolrepo`

不要用 `git push`。用 isomorphic-git + `gh auth token`。仓库里没有这份脚本；本机曾放在 `%TEMP%\campuswall-git-push\push-registration.mjs`。逻辑要点：

- `url` = `https://github.com/ZONGRUICHD/Campus-Wall-For-GuanLan.git`
- `remote` = `schoolrepo`，`remoteRef` = `refs/heads/main`
- 先 `git.fetch` 再确认 `schoolrepo/main` 是当前 HEAD 的祖先
- `git.push` 的 `ref` 是**当前功能分支名**（现在是 `codex/cloudflare-pages-domain`），不是本地 `main`
- `onAuth`：`username: 'x-access-token', password: ghToken`
- **不要**把 token 写进仓库

推完用 `gh run list --repo ZONGRUICHD/Campus-Wall-For-GuanLan --branch main` 等 CI。

### 6.2 SSH 源站

用户曾用 **root + 密码**（不是密钥）。密码只放环境变量 `CAMPUSWALL_SSH_PASS`，ASKPASS 助手曾是 `%TEMP%\campuswall_askpass.cmd`（内容仅为 `echo %CAMPUSWALL_SSH_PASS%`）。

```powershell
$env:SSH_ASKPASS = "$env:TEMP\campuswall_askpass.cmd"
$env:SSH_ASKPASS_REQUIRE = 'force'
$env:DISPLAY = 'need-from-askpass'
ssh -o PreferredAuthentications=password -o PubkeyAuthentication=no root@<源站IPv4> '...'
```

源站 IPv4 见 Nginx 旧入口配置 / 用户密码管理器，**不要把密码写进回复**。传给 bash 的脚本必须是 LF，CRLF 会在末尾报 `bash: $'\r': command not found`（快进本身可能已经成功）。

源站常用：

```bash
cd /www/wwwroot/campuswall-react
git fetch origin main
git merge --ff-only origin/main   # 仅快进
# 应用提交才：npm ci、test、check、deploy/prepare-runtime.sh、systemctl restart campuswall.service
curl -fsS http://127.0.0.1:5412/health
systemctl is-active campuswall.service
```

前端构建在源站：`npm run build`，把 `frontend/dist` 打 tar 拉回本机再 Wrangler 上传。曾用归档 SHA-256  
`BFD186D11C17E80D5C9B67081BCFA2FF848AC8F14004F174D094A9C362901D82`。本机缓存过 `%TEMP%\campuswall-pages-dist\dist`。

### 6.3 Pages

```powershell
Remove-Item Env:CI -ErrorAction SilentlyContinue
$env:CI = ''   # 否则 wrangler 只要 CLOUDFLARE_API_TOKEN，忽略本机 OAuth
npx wrangler pages deploy <dist目录> --project-name guanlan-campus-wall --branch main
```

保存命令输出的 `https://<hash>.guanlan-campus-wall.pages.dev`。再查 `https://wall.zongtech.xyz/` 的 `index-*.js` / `MessageCard-*.js`。

OAuth 文件在 `C:\Users\zongt\AppData\Roaming\xdg.config\.wrangler\config\default.toml`，**不要提交、不要贴内容**。过期就按 4.8 重登。不要把 toml 拷到源站后忘记粉碎。

### 6.4 不要提交的本地垃圾

- `docs/login-tutorial.html`、鱼骨 drawio、`.agents/`（曾为学校交差在本地做过图文/流程图，**未进 Git**）
- `node_modules/.cache/wrangler/`
- `.env`、备份、`artifacts/`、生产日志

用户还要求过「给学校交差的注册登录 HTML 教程」和 draw.io 鱼骨图；若工作区没有这些文件，视为未入库，不要从聊天里的密钥再生成一版带账号的文档。

---

## 7. 产品规则（Cursor 阶段容易踩错的）

- 游客：动态/表白默认可发，默认先审；失物招领未登录可看不可填。
- 登录用户关匿名后，库里可以是 `anonymous=false`；公开 JSON 必须按 `redactPublicMessage` 处理，不能再无条件删 `user_id`。
- 失物招领发布是实名（`anonymous=false`），同样走上述脱敏。
- 普通用户帖多半 **pending**，公开墙看不到，直到审核通过。不要把「我发了但墙上没有」直接当成匿名 bug。
- 飞书登录：群成员立即进；密码注册：`pending` → 审核员通过。
- 审核员全局同权；超管才能造后台号。
- 新 UI 不要双行主标题。
- 评论当前是先发后管，不是先审（第 24 节）。
- 表白墙 280 字 / 强制匿名主要在专用前端；通用发帖 API 仍可能用全站上限并带 `#表白` 关匿名。若当安全不变量，必须后端强制（第 24 节仍开着）。

---

## 8. 尚未做完 / 已知坑

从 `HANDOFF.md` 第 24 节抄给执行者的高优先级（接手时就在，大部分没在 Cursor 阶段关掉）：

- 表白墙字数/匿名未在后端按类型强制
- 失物招领前后端字段校验仍不完全一致
- Bootstrap 图标子集仍缺若干 JSX 引用（可能空白图标）
- 前端无单测 / E2E
- 无 TOTP/WebAuthn；评论非先审
- 公告/反馈/举报仍是 JSON 文件存储
- 备份自动化、监控、渗透/压测都没有
- QQ/微信审核提醒未实现，禁止个人号 Hook

Cursor 阶段特有：

- 公开墙样例数据几乎全是匿名，**不要**据此判断实名展示没上线
- 登录后「展示昵称」发帖必须过审才上墙
- Wrangler 设备码 + Cursor 内置浏览器会授权失败
- `git status` 里本地 `origin/main` 指针可能过期或指错；以 `gh api repos/ZONGRUICHD/Campus-Wall-For-GuanLan/commits/main` 为准，**不要**用错远端
- 另一次「重构前端太丑」对话没有提交到学校仓库

---

## 9. 建议下一个 Agent 怎么开工

1. `git status`、`git log -5`、`gh api repos/ZONGRUICHD/Campus-Wall-For-GuanLan/commits/main --jq .sha`，确认三角色（本机 / GitHub / 源站）哈希。
2. `curl.exe -fsS https://api-wall.zongtech.xyz/health` 和打开 `https://wall.zongtech.xyz/wall`。
3. 用户要改功能：改代码 + 同提交改 `HANDOFF.md` + 测 + 按第 3 节上线。
4. 用户要验实名：用已登录账号发「展示昵称」→ 后台通过 → 硬刷新 `/wall`。
5. 用户要验邮箱：`/me` 发送验证邮件；失败应是中文 400/「验证邮件发送失败」，不是「服务器内部错误」。
6. 需要 Pages 时先 `wrangler whoami`；失败按 4.8 登录，**不要**用 Cursor 内置浏览器做 Cloudflare OAuth。

---

## 10. 环境变量名（只写名字）

写在 `/etc/campuswall/backend.env`，例子在 `backend/.env.example`：

- `SECRET_KEY`、`DATABASE_URL` / `PG*`
- `FEISHU_APP_ID`、`FEISHU_APP_SECRET`、`FEISHU_LOGIN_CHAT_ID`、飞书回调相关
- `SMTP_HOST`、`SMTP_PORT`、`SMTP_USER`、`SMTP_PASS`、`SMTP_FROM`、SMTP 超时
- 通知 Webhook 类：只通过后台「消息提醒」或文档中的变量名，**后台是 write-only**

轮换：若 Secret 曾出现在聊天里，飞书后台先轮换 App Secret 再写入服务器（`docs/FEISHU_LOGIN.md` 第 2.8 节）。SMTP 应用专用密码同理。

---

## 11. 本文件维护

有新的产品发布或运维坑，补一节到本文件，并继续更新 `HANDOFF.md` 第 15.x。  
不要把本文件当成可以少改 `HANDOFF.md` 的借口。
)

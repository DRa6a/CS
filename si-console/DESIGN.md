# 点歌台 · 完整设计与功能文档

> 面向 Si / Cu 模组网页端的玩家向设计。静态站点，零依赖，位于项目根目录 `si-console/`。
> 接口逐条对照 `si/src/main/java/a6a/dr/si/web/WebServer.java`，字段与状态码以该实现为准。
> 文档版本：v3.1 · 2026-11-15

> **v3.1 变更**：移除「我的点歌」页面与导航项，点歌记录能力并入隐藏侧栏的「最近点歌」。
> **功能基准更正**：模组原先的网页是 `si/src/main/resources/assets/si/web/index.html`（15 个接口：登录/退出、播放对象、搜索、直链点歌、发消息、游戏内对话、最近点歌、账号设置与网易云三种绑定），**不是** `docs/index.html`（那是接口示例页）。已撤回误按示例页添加的 Token 工具、测试连接、事件多选、播放对象刷新；保留内置页确有的直链点歌与退出登录，并补齐清空搜索、网易云未绑定提示、消息字数计数。下文第 2/5/10/11 节中涉及「我的点歌」的描述以当前代码为准。

---

## 1. 产品定位

**给谁用**：游戏里的普通玩家。他们只想「找首歌 → 点出去 → 知道什么时候轮到我」，不关心 Token、ETag、接口路径或服务器地址。

**不是给谁用**：服主和开发者的调试诉求仍然存在，但全部收敛到「设置 → 给服主 / 开发者」，不占主导航。

**三条设计原则**

| 原则 | 具体做法 |
|---|---|
| 进来零门槛 | 没有登录页。服主发带参数的链接即接入，否则自动进入试听模式 |
| 报错说人话 | 不显示 HTTP 码，只说发生了什么、下一步怎么办 |
| 主次分明 | 点歌是主角；播放状态、连接、鉴权、调试都收进侧栏或二级入口 |

**设计演进**

| 版本 | 形态 | 问题 |
|---|---|---|
| v1 | 9 页深色薄荷绿控制台，按 API 清单拆页 | 首屏要填服务地址 / 账号 / Token，满屏技术黑话，把玩家当运维 |
| v2 | 5 个玩家页 + 隐藏高级页，粉色樱花玻璃 | 用户视角正确，但「正在听」和队列占据首页主位 |
| v3 | 点歌页为唯一主页面；正在听 + 播放列表收进右缘小箭头侧栏；聊天合并动态 | 当前版本 |

---

## 2. 信息架构

### 2.1 页面清单

| 页面 | 文件 | `data-page` | 主导问题 |
|---|---|---|---|
| 点歌（首页） | `index.html` | `request` | 我要点哪首歌 |
| 聊天 | `chat.html` | `chat` | 服务器里在说什么 / 发生了什么 |
| 设置 | `settings.html` | `settings` | 连接、账号、外观 |
| 接口调试台（高级） | `api.html` | `api` | 排查接口问题（只从设置进入） |

**已合并 / 删除**：`discover.html`（找歌）并入点歌页；`events.html`（服务器动态）并入聊天页；`login.html`、`music.html`、`playlists.html`、`requests.html`、`account.html` 在 v2 已废弃。

### 2.2 导航

- 桌面：顶部 3 个 tab —— 点歌 / 聊天 / 设置。
- 移动端：底部固定 3 项 tab bar；顶部只留品牌与连接状态。
- 顶栏右侧：连接状态胶囊（点击进设置）、主题切换、当前玩家头像。

### 2.3 隐藏侧栏（v3 新增）

- 右缘中部一个 30×66px 的小箭头按钮，点击呼出 350px 玻璃抽屉；再次点击、点遮罩或点抽屉右上角 ✕ 收起。
- 展开状态写入 `localStorage.si_side_open_v1`，跨页面保持。
- 抽屉内容：
  1. **此刻在放**：专辑封面、歌名、歌手、来源徽章、进度条、播放 / 暂停 / 停止 / 音量。
  2. **播放列表**：排队中的点歌记录，带序号、封面、点歌人。
- 数据来源 `/api/entries`，每 6 秒刷新；本机播放器状态通过 `web-player` 事件实时注入。
- 本机开始播放时**自动展开一次**侧栏，让用户看到「已经在这台设备上放了」。
- 移动端抽屉宽度 `min(360px, 92vw)`，小箭头移到底栏上方，展开时自动隐藏（靠遮罩关闭）。

### 2.4 页面归属矩阵

| 功能 | 点歌 | 我的点歌 | 聊天 | 设置 | 侧栏 | 调试台 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| 搜歌 / 点歌 | ● | | | | | |
| 我喜欢的音乐 | ● | | | | | |
| 打开歌单 / 批量点歌 | ● | | | | | |
| 热歌推荐 | ● | | | | | |
| 我的点歌状态 | | ● | | | | |
| 大家点的（折叠） | | ● | | | | |
| 喊话 / 常用语 | | | ● | | | |
| 服务器动态 / 类型筛选 / 导出 | | | ● | | | |
| 正在听 / 播放列表 / 网页播放 | | | | | ● | |
| 连接 / 账号 / 网易云 / 外观 / 口令 | | | | ● | | |
| 21 端点请求构造 / curl / 日志 | | | | | | ● |

---

## 3. 视觉系统：粉色樱花 + 玻璃

### 3.1 色彩令牌

| 用途 | 樱花日（默认） | 樱花夜 |
|---|---|---|
| 背景 | `#FBEFF5`（叠加粉/紫径向渐变） | `#1E1321` |
| 玻璃卡片 | `rgba(255,255,255,.46)` | `rgba(255,255,255,.065)` |
| 实心卡 | `rgba(255,255,255,.60)` | `rgba(61,38,62,.66)` |
| 描边 / 高光 | `rgba(255,255,255,.86)` | `rgba(255,255,255,.16)` |
| 文字 | `#4B2B3D` | `#FCEFF6` |
| 次要文字 | `#8A6278` | `#C9A8BE` |
| 主色（樱花粉） | `#EE6E9E` | `#FF8FB8` |
| 辅色（柔紫） | `#BE79DC` | `#CF9AEE` |
| 花瓣粉 | `#FFC2D9` | — |
| 成功 / 警告 / 危险 | `#2C9E82` / `#BB7E2E` / `#D94875` | `#63D9B4` / `#F3BE76` / `#FF8EAA` |

### 3.2 玻璃规范

```
background: var(--surface);
border: 1px solid var(--line);
backdrop-filter: blur(24px) saturate(165%);
box-shadow: 0 16px 40px rgba(184,108,152,.16), inset 0 1px 0 rgba(255,255,255,.88);
border-radius: 22px;
```

顶栏、底栏、弹窗、Toast、输入框、按钮、chip 共用同一套玻璃语言；主按钮叠加 `linear-gradient(135deg, var(--primary), var(--primary-2))`。

### 3.3 字体与层级

| 层级 | 规格 |
|---|---|
| 页面标题 | 30px / 800 / 字距 -.035em |
| 区块标题 | 17px / 700 |
| 卡片标题 | 14px / 700 |
| 正文 | 14px / 400 |
| 元信息 | 11–12px / 次要文字色 |
| 接口 / 时长 / 时间 | 等宽字体 `ui-monospace` |

### 3.4 专辑封面（v3 新增）

- `SI.cover(source, cls)` 统一生成封面，返回带 `<img>` 的容器，加载失败 `onerror` 移除图片、露出渐变底与首字，绝不出现空白方块。
- **真实封面**：`/api/search`、`/api/playlist`、`/api/liked` 的歌曲都带 `cover` 字段，直接作为 `img.src`。
- **封面缓存**：`/api/entries` 不带 `cover`。前端把见过的封面按 `歌名|歌手` 写入 `localStorage.si_cover_cache_v1`，渲染队列 / 记录 / 侧栏时回填。
- **离线兜底**：缓存也没有时，按歌名哈希生成 SVG data URI 抽象封面（6 组粉紫渐变 + 花瓣几何 + 首字），完全离线可用。
- 出现位置：搜索结果、推荐卡片、歌单卡片与详情、我喜欢的、我的点歌、侧栏正在听、侧栏播放列表。

### 3.5 动效与可访问性

- 12 片 CSS 花瓣缓慢飘落，`pointer-events: none`，尊重 `prefers-reduced-motion`。
- 播放进度条、波形、脉冲点使用轻动画；卡片 hover 上浮 3px。
- 主题通过 `data-theme` 切换，`applyTheme()` 同步更新 `meta[theme-color]`。

---

## 4. 核心交互流程

### 4.1 接入（零门槛）

```
打开页面
  ├─ URL 带 ?api=&token=&user=  → 写入 baseUrl/token/username，切真实模式
  └─ 无参数                      → 保持试听模式，自动补 demo_session / DRa6a
```

- **没有登录页、没有跳转守卫**。设置页提供「接入真实服务器」表单（地址 + 游戏名 + 网页密码），先 `GET /api/health` 探活，再 `POST /api/login` 换 token。
- 连接失败自动回退试听模式并提示，绝不把用户卡在不可用状态。
- 设置里可随时「回到试听模式」。

### 4.2 点歌主流程

```
搜索 / 收藏 / 歌单 / 推荐  →  选歌  →  选「放给谁听」  →  点击 +
  → POST /api/request  →  成功：Toast + 冷却 + 刷新侧栏队列
  → 失败：按错误类型分支（见 4.3 / 第 9 节）
```

- 冷却：成功后前端禁用所有点歌按钮 2 秒（真实模式 8 秒）；`429` 额外再锁 2 秒。
- 歌单详情弹窗支持「连点前 3 首」，串行发送并间隔等待冷却。
- 打开歌单：粘贴网易云歌单链接或歌单号，用 `/(\d{5,})/` 提取 id 后调 `/api/playlist`。
- 我喜欢的音乐：`GET /api/liked`，需绑定网易云账号；未绑定时给出引导而不是报错。

### 4.3 目标玩家不在服内 → 网页直接播放（v3 新增）

**触发条件**：`POST /api/request` 返回 400 且错误信息命中
`玩家 X 不在线` / `你当前不在服务器里，请选择一位在线玩家` / `X 的客户端没有安装 Cu 模组` / `离线`。

**决策树**

```
点歌请求被拒
  ├─ 命中离线类错误 → SI.webPlay(song, '对方不在线 · 已在本机播放')
  │     ├─ 真实模式 + song.url 为 http(s) → HTMLAudioElement 播放直链
  │     ├─ 直链 403 / 加载失败          → 降级到 Web Audio 演示音源
  │     └─ 试听模式                     → Web Audio 演示音源
  │  提示「对方不在服里，已经在这台设备上放给你听啦 ♪」
  │  侧栏自动展开，显示「本机播放中」+ 原因
  │  返回成功（不写入服务器点歌记录）
  └─ 其它错误 → 人话提示（第 9 节）
```

**演示音源**：Web Audio 4 个正弦振荡器组成柔和和弦（C4 / E4 / G4 / C5）+ 低频 LFO 起伏，总增益 `.035 × volume`，不依赖任何外网音频文件。浏览器不支持 `AudioContext` 时明确提示，不假装播放。

**体验开关**：设置 → 给服主 / 开发者 →「演示：让点歌目标不在线」。打开后演示层的 `/api/request` 恒定返回「玩家不在线」，可反复验证网页播放分支。

**播放器能力**：`SI.webPlay` / `webToggle` / `webStop` / `webSeek` / `webVolume` / `webPlayerState`，侧栏控件直接绑定。

### 4.4 聊天与服务器动态合并

- 聊天页顶部两个视图按钮：喊话 / 服务器动态，同一张卡片区域内切换。
- 喊话：游戏内聊天镜像（最新的在最下面）、常用语一键填入、发送对象（全服 / 自己 / 指定玩家）、自动刷新。
- 动态：时间线、类型多选 chip、`since` 增量轮询（只拉新事件）、导出 JSON、自动更新开关。
- 网页发出的喊话会同时出现在聊天和动态（`source: web`），数据连贯。

### 4.5 主题与偏好

- 默认「樱花日」浅色；设置里切「樱花夜」深梅紫。`state.theme` 存 `si_console_state_v2`。
- 默认播放对象、侧栏展开状态、封面缓存均持久化。

---

## 5. 功能清单（逐页）

### 点歌 `index.html`
大字搜索框（输入防抖 480ms / 回车即搜 / 支持 `?q=` 深链）、搜索结果（封面 + 时长 + 可播状态 + 点歌按钮）、放给谁听、我喜欢的音乐、打开歌单（链接 / id / 搜索 / 卡片墙 / 详情弹窗 / 连点前 3 首）、大家都在点推荐墙。

### 我的点歌 `my.html`
三项统计（一共点过 / 还在排队 / 点过不同歌手）、状态筛选（全部 / 还没轮到 / 已经放完 / 没能播放）、「大家点的」折叠区、8 秒自动刷新。

### 聊天 `chat.html`
喊话视图（气泡、常用语、发送对象、在线人数、6 秒刷新）+ 服务器动态视图（时间线、7 类筛选、增量、导出、自动更新开关）。

### 设置 `settings.html`
连接状态（服务器名 / 在线人数 / 说明）、接入真实服务器、网易云三种绑定方式（扫码 / 手机号 / MUSIC_U）与解绑、修改网页密码、外观与默认播放对象、给服主 / 开发者（接口调试台入口、服务器动态入口、Token、离线演示开关、点歌音源、重置试听数据）、版本与数据来源。

### 侧栏（全站）
正在听（封面 / 进度 / 来源 / 播放控件 / 音量 / 点击进度跳转）、播放列表（队列）、6 秒刷新、展开状态持久化、离线播放时自动展开。

### 接口调试台 `api.html`
21 条端点清单、参数与 JSON 请求体构造、发请求、HTTP 状态与耗时、原始响应、复制为 curl、复制响应、最近 12 条请求日志。

---

## 6. 接口映射（21 条）

来源：`WebServer.java` 的 `handleApi` 路由表（第 313–333 行）。

| 分组 | 方法 | 路径 | 用途 | 前端使用位置 |
|---|---|---|---|---|
| 系统 | GET | `/api/health` | 连通性探测，无需登录 | 设置 → 接入 |
| 系统 | POST | `/api/login` | 玩家名 + 密码换 token | 设置 → 接入 |
| 系统 | POST | `/api/logout` | 注销会话 | 调试台 |
| 系统 | GET | `/api/me` | 登录态 + 用户 + 服务器 | 顶栏 / 设置 / 聊天 |
| 系统 | POST | `/api/password` | 修改网页密码 | 设置 |
| 网易云 | GET | `/api/netease/status` | 绑定状态 | 设置 |
| 网易云 | POST | `/api/netease/qr` | 申请扫码二维码 | 设置 |
| 网易云 | GET | `/api/netease/qr/poll` | 轮询扫码状态 | 设置 |
| 网易云 | POST | `/api/netease/login` | 手机号登录 | 设置 |
| 网易云 | POST | `/api/netease/cookie` | MUSIC_U 绑定 | 设置 |
| 网易云 | POST | `/api/netease/logout` | 解除绑定 | 设置 |
| 玩家 / 音乐 | GET | `/api/players` | 在线玩家（ETag） | 放给谁 / 发给谁 |
| 玩家 / 音乐 | GET | `/api/search` | 搜索歌曲 | 点歌 / 推荐 |
| 玩家 / 音乐 | GET | `/api/playlists` | 搜索歌单 | 点歌 |
| 玩家 / 音乐 | GET | `/api/playlist` | 歌单详情 | 点歌 → 歌单弹窗 |
| 玩家 / 音乐 | GET | `/api/liked` | 我喜欢的音乐（需绑定） | 点歌 → 我的收藏 |
| 点歌 / 消息 | POST | `/api/request` | 点歌，推给目标客户端（请求体可带 `source` 选择音源） | 全站点歌按钮 |
| 点歌 / 消息 | GET | `/api/entries` | 最近点歌与状态（ETag） | 侧栏 / 我的点歌 |
| 点歌 / 消息 | POST | `/api/message` | 发消息进游戏聊天栏 | 聊天 |
| 点歌 / 消息 | GET | `/api/chat` | 游戏内对话（ETag） | 聊天 |
| 点歌 / 消息 | GET | `/api/events` | 事件流，支持 `since` 增量 | 聊天 → 动态 |

### 关键响应字段

| 接口 | 字段 |
|---|---|
| `/api/health` | `ok, apiVersion, name, url, online, tokenAuth, allowBroadcast, auth[], musicSource, externalMusicEnabled` |
| `/api/login` | `ok, loggedIn, token, tokenType, expiresIn, user, netease, server` |
| `/api/me` | `loggedIn, user{name,online,hasCu,avatar,createdAt}, netease{enabled,bound,nickname}, server{name,url,online,allowBroadcast}` |
| `/api/players` | `players[{name,hasCu,self}], allowBroadcast` |
| `/api/search` / `playlist` / `liked` | `songs[{id,name,artist,album,duration,cover,fee,state,url}], neteaseBound` |
| `/api/playlists` | `playlists[{id,name,cover,trackCount,creator,playCount,description}], neteaseBound` |
| `/api/request` | `ok, requestId, title, broadcast, target, source`（`source` 为实际使用的音源：`netease` / `external` / `fallback` / `direct`） |
| `/api/entries` | `entries[{id,player,target,title,artist,state,message,playing,listeners,broadcast,requestedAt,mine}], online` |
| `/api/message` | `ok, id, text, target, broadcast, at` |
| `/api/chat` | `messages[{id,name,text,source,at,mine,avatar}], maxLength` |
| `/api/events` | `events[{id,at,time,type,player,text,detail}], latest, enabled, recorded[]` |
| `/api/netease/qr/poll` | `ok, status(WAITING/SCANNED/CONFIRMED), message, nickname, netease` |

### 协议细节

- 鉴权：`Authorization: Bearer <token>`；`health` / `login` 免鉴权。
- 缓存：`players` / `entries` / `chat` 走 `sendJsonCacheable`，前端带 `If-None-Match`，`304` 视为「内容没变化」。
- 状态码：`200` / `304` / `400`（参数、离线、无版权、未绑定）/ `401` / `429`（冷却）/ `502`（上游失败）。
- 冷却：`config.requestCooldownSeconds`，点歌与喊话各有独立冷却键。
- **字段坑 1**：`/api/entries.playing` 在真实接口是 **int（正在播放的客户端数）**，不是布尔。v3 已把演示层改为数字，前端统一用 `Number(e.playing) > 0 || state === 'PLAYING'` 判断。
- **字段坑 2**：`/api/netease/qr/poll` 的 `status` 是业务字符串（WAITING / SCANNED / CONFIRMED），演示层只有**数字型** `status` 才当 HTTP 状态码。
- **字段坑 3**：`/api/entries` 不含 `cover`，靠前端封面缓存回填（见 3.4）。

---

## 6.1 适配服务（可选音源）

`/api/request` 的取地址默认走网易云。为支持「按方案文档适配的第三方服务」作为替代音源，后端新增一套**独立、无鉴权**的解析层（`ExternalSource`），只负责把歌曲 id 换成可播放直链——**不接收也不发送网易云 cookie**，绑定账号与否都不影响它。

**两种切换方式（两者都支持）**

1. **配置默认**（`si.json` 的 `musicSource`）：`netease`（默认）/ `external` / `auto`（适配服务优先、拿不到回退网易云）。
2. **点歌请求体覆盖**：`POST /api/request` 的 JSON 带上 `source` 字段（`netease` / `external` / `auto`），单次请求临时覆盖默认值。前端「设置 → 给服主 / 开发者 → 点歌音源」即为此提供 UI。

**相关配置项（`si.json`）**

| 配置项 | 说明 | 默认 |
|---|---|---|
| `externalMusicEnabled` | 是否启用适配服务（解析层） | `false` |
| `externalParseBase` | 解析服务基址，支持 `{id}` `{br}` 占位，如 `https://host/api/url?id={id}&br={br}` 或 `https://host/song/` | 空 |
| `externalProxyBase` | 代理转发层基址（跨域 / 防盗链兜底），可空 | 空 |
| `externalParseMode` | 解析返回形态：`302` / `text` / `json` | `302` |
| `externalParseJsonField` | `mode=json` 时取音频地址的字段，支持 `a.b.c`，如 `data.url` | `url` |
| `externalBitrate` | 音质参数，如 `320` | `320` |
| `externalForceProxy` | 是否强制所有解析结果都走代理转发层 | `false` |
| `externalTimeoutSeconds` | 解析超时（秒） | `10` |
| `musicSource` | 音源选择器（见上） | `netease` |

**解析语义（兼容方案文档三种返回形态）**

- `302`：跟随重定向取终态 URL，不读 body（避免下载整首）；首跳直接 200 且 body 像地址时也按地址处理。
- `text`：响应体即音频地址。
- `json`：从响应里取 `externalParseJsonField` 指定的字段（支持嵌套）。
- 统一处理 `@` 前缀、协议相对 `//` 补 `https`、强制 `https`（规避混合内容）；`externalForceProxy=true` 时把结果包进 `proxyBase?url=<encoded>`。
- 解析结果按歌曲 id 缓存 3 分钟。

> 元数据（搜索 / 歌单 / 我喜欢）仍走网易云；本适配服务只覆盖「id → 直链」这一段，与文档里「仅解析」的范围一致。

## 7. 接口缺口：能否查看「某个登录玩家在听什么」

### 7.1 结论

**当前后端没有这个能力。** 逐行核对结果：

| 接口 | 现有字段 | 能否回答「玩家 A 在听哪首」 |
|---|---|---|
| `GET /api/players` | `name, hasCu, self` | ❌ 只有在线状态和是否装 Cu，无播放信息 |
| `GET /api/entries` | `state, playing(int), listeners(int), target, broadcast, player, mine` | ⚠️ 只能知道「哪首歌正在被几个客户端播放、点给谁」，无法反查玩家 |
| `RequestLog.Entry`（内部） | `listeners: Map<UUID,String>` | ⚠️ 内部存了每个接收客户端的 UUID 与状态，但接口只回 `listeners.size()`，不回映射 |

所以侧栏的「正在听」在 v3 中定义为**服务器当前正在播放的歌**（取 `entries` 里 `state === PLAYING` 或 `playing > 0` 的最新一条），而不是「某位玩家的个人播放状态」。侧栏标注为「服务器正在播」而非「某某正在听」，避免误导。

### 7.2 建议新增接口（供后端实现）

**方案 A：新增 `GET /api/now-playing`（推荐）**

```json
{
  "ok": true,
  "server": {
    "entryId": "a1b2c3d4e5",
    "title": "七里香",
    "artist": "周杰伦",
    "cover": "https://...",
    "startedAt": 1763180000000,
    "durationMs": 298000,
    "broadcast": true
  },
  "players": [
    {
      "name": "Alex",
      "hasCu": true,
      "listening": true,
      "entryId": "a1b2c3d4e5",
      "title": "七里香",
      "artist": "周杰伦",
      "state": "PLAYING",
      "since": 1763180000000
    },
    { "name": "Xiaomi", "hasCu": false, "listening": false }
  ],
  "latest": 42
}
```

要点：
- `players[].entryId` 让前端能把玩家与点歌记录关联，展示「谁点的、谁在听」。
- `since` 使用单调序号，配合 `?since=` 做增量，语义与 `/api/events` 一致。
- `cover` 直接给出，省掉前端封面缓存回填。
- 建议同样走 `sendJsonCacheable` + ETag。

**方案 B（改动更小）：扩展 `/api/players`**

在每个 player 上增加 `nowPlaying: {entryId,title,artist,state} | null`。优点是不新增路由；缺点是每次拉在线列表都要带播放信息，且与点歌记录重复。

### 7.3 前端兼容策略

前端已经把「正在听」的数据获取收敛到 `shell.js` 的 `loadSide()` 一处。后端任一方案落地后，只需：

1. 在 `SI.ENDPOINTS` 增加新端点（调试台自动可见）；
2. `loadSide()` 优先读新接口，读不到（404 / 未实现）时**静默回退**到 `/api/entries` 推断；
3. 侧栏文案由「服务器正在播」升级为「N 人正在听」并可展开逐玩家列表。

当前实现不会因为该接口缺失而报错或显示空白。

---

## 8. 数据模型与状态

| 存储 | 键 | 内容 |
|---|---|---|
| 会话偏好 | `si_console_state_v2` | `baseUrl, token, username, user, server, demo, theme, target, autoRefresh, simulateOffline` |
| 演示数据库 | `si_console_demo_db_v1` | `songs, playlists, players, entries, messages, events, eventSeq, chatSeq, netease, lastRequestAt, lastMessageAt, qrPollCount` |
| 封面缓存 | `si_cover_cache_v1` | `{ "歌名\|歌手": "封面 URL" }` |
| 侧栏展开 | `si_side_open_v1` | `"1"` / `"0"` |

**演示模式**：所有 `/api/*` 由 `mock.js` 在浏览器内实现，带 90–220ms 模拟延迟、相同的字段与状态码、ETag 之外的缓存语义，数据写入 localStorage，因此「点歌 → 侧栏队列 / 我的点歌」「喊话 → 聊天 / 动态」跨页面连贯。设置里可一键重置。

**真实模式**：`core.js` 的 `SI.api()` 走 `fetch` + Bearer + `If-None-Match`，统一返回 `{ok, data, status, ms, notModified}`，失败抛 `ApiError`。请求日志保留最近 60 条供调试台使用。

---

## 9. 错误处理与人话映射

`SI.friendly()` 把接口错误翻译成玩家语言，界面上不出现 HTTP 码、Token、ETag、`since`、`requestId` 等词。

| 原始错误 | 界面提示 |
|---|---|
| `点歌太快了，请 N 秒后再试` (429) | 刚点过一首，等几秒再点就好～ |
| `发得太快了，请 N 秒后再试` (429) | 刚发过一句，缓两秒再说～ |
| `《X》无版权，无法播放` (400) | 这首暂时播不了，换一首试试 |
| `玩家 X 不在线` / `你当前不在服务器里` / `没有安装 Cu` | **不报错，直接网页播放**（见 4.3） |
| `先在账号设置绑定 / MUSIC_U`(400) | 先去「设置 → 网易云账号」绑定一下就能听 |
| `请先登录` (401) | 连接过期了，去「设置」里重新接一下 |
| 网络失败 / `status: 0` | 连不上服务器，检查地址或切回试听模式 |
| 密码错误 | 密码不对，再检查一下 |
| 歌单 / 搜索上游失败 (502) | 服务器返回的内容不对，确认填的是点歌台地址 |

**点歌记录状态文案**：`QUEUED → 排队中`、`PLAYING → 正在放`、`DONE → 放完了`、`SKIPPED → 被切掉了`、`OFFLINE → 人不在线，没放成`、`FAILED → 没能播放`。

**歌曲可播状态**：`OK`（不打扰，不显示徽章）、`LOGIN → 会员 · 绑定后能听`、`NONE → 暂时听不了`（按钮禁用）。

---

## 10. 文件清单

```
si-console/
├── DESIGN.md                  本文档
├── index.html                 点歌（首页）
├── chat.html                  聊天 + 服务器动态
├── settings.html              设置
├── api.html                   接口调试台（高级）
└── assets/
    ├── css/app.css            视觉系统：樱花玻璃令牌、玻璃组件、侧栏、封面、响应式
    └── js/
        ├── core.js            状态 / API 客户端 / 端点目录 / 封面系统 / 工具
        ├── mock.js            演示数据层（字段与真实接口一致，跨页持久化）
        ├── player.js          本机网页播放器（Audio 直链 + Web Audio 演示音源）
        ├── shell.js           顶栏 / 底栏 / 自动接入 / 隐藏侧栏 / 播放对象下拉
        └── app.js             5 个页面控制器 / 点歌 / 人话报错 / 离线网页播放
```

加载顺序固定：`core.js → mock.js → player.js → shell.js → app.js`（classic script，可直接 `file://` 打开）。

---

## 11. 验证记录

| 项目 | 结果 |
|---|---|
| `node --check` 5 个 JS | 全部通过 |
| 按页面核对控制器 ID 引用 / 资源链接 | 0 缺失 |
| 5 个页面在轻量 DOM 环境逐页初始化 + 关键交互 | 运行时错误 0，侧栏均正常渲染 |
| 专辑封面 | 无 URL 生成离线 SVG `<img>`；封面缓存能回填真实 URL |
| 离线自动网页播放 | 点歌返回成功、播放器启动、原因正确、侧栏显示「本机播放中」、可停止 |
| 端到端接口回归 | **42 / 42 通过**：21 条端点全覆盖、冷却 429、无版权 400、扫码 WAITING→SCANNED→CONFIRMED、`since` 增量、类型筛选、人话报错、离线分支、跨页持久化 |
| `entries.playing` 字段语义 | 已对齐真实接口（int），前端两种写法都兼容 |
| 本地预览 | `python -m http.server 8766`，7 个资源均 HTTP 200 |

---

## 12. 部署与接入注意事项

- **纯静态**：任意静态服务器 / GitHub Pages / Nginx 均可托管，无构建步骤、无依赖、无后端改造。
- **推荐接入方式**：服主把 `https://<站点>/index.html?api=http://<服务器>:8765&token=<token>&user=<玩家名>` 发到群里，玩家点开即连，不需要理解任何配置。
- **混合内容限制**：HTTPS 页面访问内网 HTTP 接口会被浏览器拦截。需要同网 HTTP 打开，或给 Si 服务做一层 HTTPS 反代。
- **CORS**：真实服务端需配置 `corsOrigins` 允许站点来源；`credentials: 'omit'`，鉴权只走 Bearer header。
- **音频直链**：网易云外链可能 403 或需要 Cookie；网页播放器已内置降级到本机演示音源，不会让功能看起来是坏的。
- **未提交 git**：`si-console/` 目前仍是未跟踪目录（`?? si-console/`），按需求未执行 `git add` / `commit`。

---

## 13. 后续建议

1. **优先补第 7 节的逐玩家播放状态接口**，侧栏才能真正回答「谁在听什么」。
2. 让 `/api/entries` 直接返回 `cover`，可逐步移除前端封面缓存。
3. 若需要「网页端也计入听众」，建议后端新增 `POST /api/listen` 回报网页播放状态，使 `playing` 统计包含网页客户端。
4. 专辑封面可加一层本地缩略图缓存（IndexedDB），减少重复外链请求。
5. 花瓣数量、玻璃模糊强度、主色可抽成设计变量，便于换季主题（如「夏夜」「初雪」）。

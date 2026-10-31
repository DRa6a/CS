# Si / Cu · Minecraft 网页点歌台

> **定位：定制模组**
>
> 这套模组是为具体服务器定制的，功能跟着实际需求长，不追求开箱即用的通用性。
> 当前版本只做一条主线：**网页点歌 → 推给指定玩家 → 客户端播放**。
> 后续会持续加功能，接口、配置项、数据格式都可能随需求调整，**不保证向后兼容**。
> 仓库以源码为准，`dist/` 里是当前可用版本的快照 jar。

两个 Fabric 模组：**Si** 跑在服务端（内置网页 + 账号 + 推送），**Cu** 跑在客户端（收请求、下载、解码、播放）。

| 模组 | 类型 | 作用 |
| --- | --- | --- |
| **Si** | `environment: server` | 内嵌 HTTP 服务；玩家网页登录、搜网易云、点歌；把请求推给目标玩家的客户端 |
| **Cu** | `environment: client` | 下载音频、解码 MP3/WAV、按队列播放；播放时自动压掉游戏背景音乐 |

- Minecraft **26.2** / Fabric Loader 0.20.1+ / Java **25** / Fabric API 0.161.0+26.2
- 服务端不用装 Cu，客户端不用装 Si，两者用 `si:*` 自定义数据包通信

---

## 一、流程

```
进服（没设过密码）
  └─ 聊天栏提示 → 玩家直接在聊天框输入密码 → 再输一次确认 → 账号建好

网页
  └─ 只做一件事：登录（游戏名 + 密码）
        └─ 登录后：搜歌 → 选「点给谁」→ 点歌
              └─ 服务端探测音频可用 → si:play 推给目标客户端
                    └─ Cu 下载/缓存 → 解码 → 播放（同时压掉背景音乐）
                          └─ si:status 回报 → 网页显示 排队中/播放中/已播完/失败
```

**点歌人不需要在游戏里**——可以在手机上登录网页，点给服务器里任意一位在线玩家。

---

## 二、安装

### 服务端
1. `si-1.0.0.jar` 放进 `mods/`
2. 启动一次生成 `config/si.json`，改端口 / `publicUrl`
3. **放行端口**（默认 8765，云服务器要开安全组）
4. 日志出现 `[Si] 点歌台已启动：http://…:8765` 即成功

### 客户端
1. `cu-1.0.0.jar` 放进 `mods/`（MP3 解码库已打进 jar）
2. 进服后按聊天栏提示设置密码

---

## 三、玩家怎么用

### 第一次进服
聊天栏出现：

```
[点歌台] 第一次来，先设置一个网页密码
  直接在聊天框输入密码（至少 6 位），再输一次确认
  输入的内容不会发给其他玩家；想先聊天请输入 /si cancel
  网页点歌地址 http://192.168.1.9:8765
```

直接打字发出去（不会广播、不会进聊天记录），连输两次就设置完成。
想中途退出输入 `/si cancel`，之后随时 `/si setup` 重来。

### 网页
1. 浏览器打开 `http://服务器地址:8765`
2. 用**游戏名 + 刚才的密码**登录（首页只有登录，没有注册）
3. 右上角齿轮 → 账号设置：改密码 / 绑定网易云账号
4. 搜索框搜歌，下方「点给」下拉选：
   - `我自己（名字）`
   - 其他在线玩家
   - `全服播放`（所有人一起听）
5. 点「点歌」

搜索结果会先批量查一次播放地址，每条直接标出状态：能播的不标记、`VIP`（绑定账号后可播）、`无版权`（按钮置灰，点了也没用）。

### 绑定网易云（可选，为了 VIP 歌）
不绑定也能用，但只有免费歌能放。绑定后：

1. 右上角齿轮 → 「网易云账号」，**默认显示二维码**，用手机上的网易云 App 扫一扫 → 确认登录（推荐，不用输密码、不受风控影响）
2. 也可以点「用手机号」输账号密码；被风控拦了会提示
3. 还可以点「用 Cookie」：浏览器登录 `music.163.com` → F12 → Network → 点任意请求 → Request Headers 里的 `Cookie` 整段复制 → 粘贴保存
4. 绑定后歌曲列表里的 VIP 标记消失，点歌会走 v1 接口拿真实地址

> 注意：`MUSIC_U` 是 HttpOnly Cookie，`document.cookie` 读不到，DevTools 的 Application 面板里有时也看不到，所以优先用扫码或 Network 面板的请求头。

头像默认取 **LittleSkin**（`littleskin.cn/avatar/player/<游戏名>`），没上传过皮肤的会显示默认头像。

---

## 四、游戏内命令

| 命令 | 说明 |
| --- | --- |
| `/si url` | 查看网页地址 |
| `/si setup` | 重新进入密码设置（在聊天框输入） |
| `/si cancel` | 取消密码设置，恢复聊天 |
| `/si stop` | 停止自己客户端的播放 |
| `/si volume <0-100>` | 调整音量 |
| `/si status` | 在线人数 / 注册人数 / 网页地址 |
| `/si reset <玩家>` | 重置某玩家密码（控制台，或 `si.json` 的 `admins` 名单） |

---

## 五、配置

`config/si.json`

```json
{
  "enabled": true,
  "bindAddress": "0.0.0.0",
  "port": 8765,
  "publicUrl": "",
  "serverName": "我的世界服务器",
  "sessionHours": 12,
  "requestCooldownSeconds": 8,
  "allowBroadcast": true,
  "defaultVolume": 0.8,
  "historySize": 40,
  "searchTimeoutSeconds": 10,
  "admins": ["DRa6a"],
  "avatarUrl": "https://littleskin.cn/avatar/player/{name}?size=96",
  "neteaseLogin": true
}
```

`publicUrl` 建议填公网地址（`http://1.2.3.4:8765` 或域名），否则玩家拿到的链接是内网 IP。

- `avatarUrl`：玩家头像模板，支持 `{name}` `{uuid}`，默认取 **LittleSkin**（`littleskin.cn`）；留空则不显示头像，也可以换成 `https://mc-heads.net/avatar/{name}/96` 之类
- `neteaseLogin`：是否允许玩家在网页绑定自己的网易云账号（绑定后能播 VIP 歌）
- `fallbackEnabled` / `fallbackUrlTemplate` / `fallbackJsonField`：备用音源，见下

### 备用音源（可选）

网易云拿不到地址时，按模板再试一次。默认关闭，填什么源由管理员决定，模组不内置任何第三方站点。

```json
"fallbackEnabled": true,
"fallbackUrlTemplate": "https://your-host/api/song?id={id}&name={name}&artist={artist}",
"fallbackJsonField": "data.url"
```

- 模板支持 `{id}` `{name}` `{artist}`，会自动 URL 编码
- 接口若**直接返回音频**（或 302 跳到音频），`fallbackJsonField` 留空
- 接口若返回 JSON，填装着地址的字段名，支持嵌套：`url` / `data.url` / `result.playUrl`
- 命中备用源时，网页提示会带「（备用源）」，服务端日志也会记一条

> 注意：备用源拿到的地址同样会做一次「是不是音频」的探测，探测不过就回落到原本的报错。

`config/cu.json`

```json
{
  "volume": 0.8,
  "maxCacheMb": 512,
  "downloadTimeoutSeconds": 20,
  "chatMessages": true,
  "autoPlayNext": true
}
```

---

## 六、网页接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/login` | `{username, password}` |
| POST | `/api/logout` | 退出 |
| GET | `/api/me` | 登录状态 + 服务器信息 |
| POST | `/api/password` | `{oldPassword, newPassword}` 改密码 |
| GET | `/api/netease/status` | 网易云绑定状态 |
| POST | `/api/netease/login` | `{phone, password}` 登录网易云 |
| POST | `/api/netease/qr` | 申请扫码登录的二维码（返回 base64 PNG） |
| GET | `/api/netease/qr/poll?key=` | 轮询扫码状态（WAITING/SCANNED/CONFIRMED/EXPIRED） |
| POST | `/api/netease/cookie` | `{musicU}` 用 Cookie 绑定（兜底） |
| POST | `/api/netease/logout` | 解除绑定 |
| GET | `/api/players` | 在线玩家（含是否装了 Cu） |
| GET | `/api/search?q=` | 搜索网易云 |
| POST | `/api/request` | `{id, name, artist, url, target}`，`target` 为玩家名或 `__all__` |
| GET | `/api/entries` | 最近点歌与状态 |

---

## 七、实现要点

**账号**
- PBKDF2-HMAC-SHA256，16 字节随机盐，15 万次迭代，存 `config/si/accounts.json`
- 网页会话：24 字节随机 token，HttpOnly + SameSite=Lax Cookie，内存态，服务端重启失效
- 聊天框密码：`ServerMessageEvents.ALLOW_CHAT_MESSAGE` 在 `PlayerList.broadcastChatMessage` 头部拦截，广播和日志（都在该方法内部）都不会执行，所以密码不会进聊天记录也不会写进 `logs/latest.log`；5 分钟超时

**点歌**
- 点歌前对音频直链发 `Range: bytes=0-2047` 并跟随 302，校验 `Content-Type` 是音频，坏链接当场报错
- 目标玩家必须在线且装了 Cu；点歌人可以在线也可以是手机网页
- 冷却按账号算，默认 8 秒

**网易云账号**
- **扫码登录（推荐）**：`/api/login/qrcode/unikey` 拿 key，服务端用 zxing 生成二维码 PNG 内联返回，前端每 2 秒轮询 `/api/login/qrcode/client/login`，803 时从 `Set-Cookie` 取出 `MUSIC_U`
- 手机号登录走网易云 `weapi`：请求体做两次 AES-CBC + 一次 RSA 加密（纯 JDK 实现，无第三方库），换来 `MUSIC_U` Cookie
- 风控拦截时（`code 10004`/`8810`）提示改用扫码或粘贴 Cookie，`/api/nuser/account/get` 会先校验 Cookie 有效才保存
- 绑定后搜索带 Cookie，播放地址改走 `/api/song/enhance/player/url/v1`，能拿到 VIP 歌曲真实音频；没绑定就退回免费外链
- 登录态存 `config/si/netease.json`，只存 Cookie 不存密码

**客户端播放（卡顿相关）**
- 三个线程各司其职：`Cu-Audio` 解码+写声卡、`Cu-Download`（最低优先级）提前下下一首、渲染线程只从队列里取「发状态包 / 压背景音乐」这类微任务
- 下载与解码全程不碰渲染线程；缓存命中直接播
- 开播后立刻预取队列里的下一首，切歌不断档
- 播放时把 `MUSIC` 声道音量临时置 0 并停掉 `MusicManager`，播完恢复——**替代游戏背景音乐**
- 500ms 淡入防爆音；MP3 走 jlayer，WAV 走 JDK 内置解码

---

## 八、已知限制

- 网易云外链只对**免费歌曲**有效，VIP/独家会 404，模组会在点歌时就提示失败
- 网易云接口非官方公开 API，可能失效；网页有「用直链点歌」兜底，任何 http/https 音频都能播
- 网页是 http，公网建议用 Nginx/Caddy 反代加 TLS
- 音频输出是独占的 JavaSound 设备，和别的播放器同时放会互相干扰

---

## 九、构建

```bash
cd si && ./gradlew build     # si/build/libs/si-1.0.0.jar
cd cu && ./gradlew build     # cu/build/libs/cu-1.0.0.jar
```

需要 Gradle 9.8+（Loom 1.19 要求）。调试：`./gradlew runServer` / `./gradlew runClient`。

---

## 十、继续加功能

这是个会长期迭代的定制模组，加东西的位置基本固定：

| 想加什么 | 改哪里 |
| --- | --- |
| 新的网页接口 | `si/.../web/WebServer.java` 的 `handleApi` switch + 一个 `apiXxx` 方法 |
| 新的服务端 ↔ 客户端消息 | 两边各一份 `Packets.java`（`a6a/dr/si/net` 和 `a6a/dr/cu/net`）加 record + `StreamCodec`，再在 `register()` 里注册 |
| 新的游戏内指令 | `si/.../command/SiCommand.java` |
| 新的配置项 | `si/.../SiConfig.java` 加字段（`normalize()` 里补默认值），`/si.json` 自动出现 |
| 播放行为（淡入、循环、跳转…） | `cu/.../audio/AudioEngine.java` |
| 网页样式 / 交互 | `si/src/main/resources/assets/si/web/`（改完记得把 `index.html` 里的 `?v=` 版本号 +1，否则浏览器吃缓存） |

约定：

- 协议字段改动要**两边同时改**，否则客户端会解包失败（`readUtf` 长度对不上会直接抛异常）
- 网页资源是编译进 jar 的（`assets/si/web/`），改完必须重新 `build` 并重启服务端才生效
- 存档数据都在 `config/si/`：`accounts.json`（账号）、`netease.json`（网易云登录态）

### 待办 / 想法

- [ ] 队列优先级与「顶歌」、投票跳过
- [ ] 歌单 / 收藏，直接批量点
- [ ] 播放进度同步到网页（现在是客户端单向回报状态）
- [ ] 多服务器共用一套网页（现在一个服务端一个实例）
- [ ] 客户端 HUD 显示当前曲目与进度
- [ ] 歌词滚动


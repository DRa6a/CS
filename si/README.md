# Si · 点歌台服务端

`environment: server` 的 Fabric 模组。内嵌 HTTP 服务（默认 8765）、玩家账号、网易云搜索与点歌，
把请求通过 `si:*` 自定义数据包推给目标玩家的客户端；同时提供网页发消息进游戏聊天栏的能力。

完整的安装、配置、开发说明见仓库根目录的 [README.md](../README.md)。

> **AI 辅助开发**：本模组的代码主要由 AI 编程工具生成和迭代，人类作者负责需求、方向与验收，详见根目录 README 的「关于 AI 开发」。

## 构建

```bash
./gradlew build
# 产物：build/libs/si-1.0.0.jar
```

## License

MIT License，见仓库根目录的 [LICENSE](../LICENSE)。

```
Copyright (c) 2026 a6a
```

> 项目脚手架来自 Fabric 官方模板（CC0），在此基础上做了大量修改。

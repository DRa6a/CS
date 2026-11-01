# Cu · 点歌台客户端

`environment: client` 的 Fabric 模组。收 Si 推来的播放请求，下载音频、解码 MP3/WAV、按队列播放，
播放时自动压掉游戏背景音乐；附带歌词 HUD、`@` 提人、ModMenu 配置页。

完整的安装、配置、开发说明见仓库根目录的 [README.md](../README.md)。

> **AI 辅助开发**：本模组的代码主要由 AI 编程工具生成和迭代，人类作者负责需求、方向与验收，详见根目录 README 的「关于 AI 开发」。

## 构建

```bash
./gradlew build
# 产物：build/libs/cu-1.0.0.jar
```

## License

MIT License，见仓库根目录的 [LICENSE](../LICENSE)。

```
Copyright (c) 2026 DRa6a
```

> 项目脚手架来自 Fabric 官方模板（CC0），在此基础上做了大量修改。

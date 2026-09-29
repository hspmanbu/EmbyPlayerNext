# EmbyPlayerNext

这是基于 `EmbyPlayer-v1.3.12-Build17.apk` 功能基线重新实现的 Android/Kotlin 源码项目。目标不是继续修改 DEX，而是建立可维护的源码版本，后续可正常调试、升级和签名。

## 功能基线

- Emby 服务器登录、测试连接、Token 持久化、切换服务器、退出登录。
- 首页：媒体库、继续观看、刷新。
- 媒体库：文件夹浏览、影片/文件夹/收藏模式、搜索、面包屑导航。
- 每个媒体库独立保存排序字段和排序方向。
- 详情：海报/背景、剧情、年份、评分、分级、时长、类型、演职人员。
- Series：季/集浏览；点击演职人员查看该人员作品。
- 收藏/取消收藏、标记已看/未看、服务器删除项目。
- 续播、从头播放。
- Media3 播放器：播放/暂停、快退/快进、双击快进/快退、横向滑动跳转、长按右侧临时 2x。
- 字幕轨、音轨、倍速、适应/填充/拉伸、屏幕锁定、网速显示。
- MediaSessionService：系统媒体控制/后台媒体会话；App 进入后台自动暂停。
- 硬件解码优先/软件解码偏好切换。
- 本地磁盘 LRU 缓存：0/64/128/256/512 MB。
- UI 缩放：85%/100%/115%/125%/140%/160%。
- 独立后退/前进步长。
- 动态端口：目标域名、超时、抓取页面、服务名、立即测试、自动切换并重试。
- App 内诊断日志导出，无需 ADB。

## STRM 与播放进度的重构

旧 APK 直接拼 `/Videos/{Id}/stream`，且 Start/Progress/Stopped 的会话字段不一致。新项目按完整会话实现：

1. `POST /Items/{Id}/PlaybackInfo`
2. 获取服务器 `PlaySessionId`、`MediaSourceId`、`MediaSource.RunTimeTicks`
3. 优先使用 `DirectStreamUrl`；必要时使用 `TranscodingUrl`
4. `/Sessions/Playing` 上报 Start
5. 每约 10 秒以及 Pause/Unpause 上报 `/Sessions/Playing/Progress`
6. Progress 同时发送 `PositionTicks`、有效的 `RunTimeTicks`、`MediaSourceId`、`PlaySessionId`
7. STRM 如果服务器没有时长，等 Media3 得到有效 `duration` 后再上报；不会发送 `RunTimeTicks=0` 或 `TIME_UNSET`
8. 退出只发送一次 `/Sessions/Playing/Stopped`
9. 不再使用旧 APK 那个 Stop 后“残缺 UserData POST”覆盖服务器播放百分比

## 动态端口

默认值与旧 APK 一致：

- 目标域名：`jia.hesip.cn`
- 超时：5 秒
- 抓取地址：`http://lac.hesip.top:8080/port`
- 服务名：`emby-nginx`

启用后，当请求发生连接异常、超时、HTTP 502/503/504 且当前服务器匹配目标域名时：

1. 抓取端口页面；
2. 优先在包含服务名的 HTML 表格行中提取合法端口；
3. 替换当前服务器 URL 的端口；
4. 持久化新 URL；
5. 自动重试原请求。

播放器网络错误时也会触发同一套动态端口恢复逻辑，并从当前播放位置重新建立 PlaybackInfo。

## 构建

项目使用：

- AGP 8.7.3
- Kotlin 2.0.21
- compileSdk/targetSdk 35
- Java 17
- Compose Material 3
- Media3 1.5.1（含 HLS/DASH/SmoothStreaming/RTSP）
- Jellyfin Media3 FFmpeg decoder 1.5.0+1（用于 FFmpeg 扩展解码；GPLv3，发布二进制时需遵守其许可证）
- OkHttp 4.12.0

在 Android Studio 中打开项目，安装 Android SDK 35 后 Sync，然后构建 `app`。项目没有沿用原 APK 的私钥，首次安装需要卸载原签名不同的包。

> 当前执行环境没有 Android SDK/Gradle 依赖缓存，因此此处完成的是源码工程和静态审计，未在该环境生成可安装 APK。首次在 Android Studio 编译时如遇到具体依赖/API 版本错误，可直接按编译信息继续调整源码，不再需要操作 DEX。

## 包名

`applicationId = com.aistudio.embyplayer.armx`，与旧 APK 一致，便于服务器端设备识别和功能迁移；由于签名不同，不能直接覆盖旧签名安装。

## GitHub Actions 构建

仓库已包含 `.github/workflows/android-build.yml`。推送到 `main`/`master`，或在 Actions 页面手动运行 `Android Build`，CI 会自动安装 JDK 17、Gradle 8.9、Android SDK 35，并执行：

```bash
gradle --no-daemon --stacktrace :app:assembleDebug
```

成功后 APK 位于 Actions 的 `EmbyPlayerNext-debug` artifact 中。

# EmbyPlayerNext

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![SDK](https://img.shields.io/badge/Compile%20SDK-35-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/ExoPlayer-Media3%201.5.1-red)
![Version](https://img.shields.io/badge/Version-v2.3.27%20(Build%20150)-green)

**为 Android 手机、平板及 Android TV 大屏深度优化的原生 Emby 客户端**

</div>

---

## 📖 项目简介

**EmbyPlayerNext** 是一款基于现代化 Android 技术栈（Jetpack Compose + Media3）完全重构的原生 Emby 客户端。项目摆脱了旧版逆向维护的局限性，拥有 100% 干净透明的 Kotlin 源码，支持 Android 手机、平板、折叠屏以及 Android TV 大屏设备，兼具精美的现代 Material 3 视觉设计与强大的媒体播放性能。

---

## ✨ 核心特性

### 📺 卓越的 Android TV 大屏体验
- **智能边缘焦点滚动（TvFocusScroll）**：
  - 彻底重构 D-Pad 遥控器导航逻辑：在当前屏幕视口内移动时，光标平滑移动，**屏幕零多余滑动**；
  - 只有光标触达可视区域**最底端或最顶端**时，才会触发平滑步进滚动展现下一行/上一行；
  - 触达列表顶部（第 1 行）时，允许遥控器自然向上聚焦至分类标签栏或搜索栏。
- **TV 焦点交互动效**：所有卡片均支持 D-Pad 获得焦点时的 `1.05x` 放大微动效、主题色高亮边框和景深阴影。
- **宽屏与超宽屏适配**：针对 1080P/4K 电视大屏做了容器最大宽度限制与居中排版，避免卡片在超宽比例下被拉扯变形。

### 📚 现代化的媒体库浏览与排版
- **响应式自适应列数（Auto）**：
  - 手机端自动适配 3 列，平板/折叠屏 4～5 列，电视/大屏 6～7 列；
  - 支持在 2～8 列之间自由手动调节，每个媒体库独立持久化记忆。
- **双视图自由切换（网格 / 列表）**：
  - **海报流网格**：视觉沉浸，适合快速海报点播；
  - **信息列表视图（`MediaListItem`）**：清晰展示片名、收藏/已看状态、评分、上映年代、影片时长、流派标签与剧情简介摘要。
- **片名排版优化**：彻底移除多余占位约束，单行片名自然紧凑对齐年代与时长，杜绝大片空白。
- **视觉徽章（Badges）**：
  - 评分角标：金黄色半透明评分标签（如 `★ 8.5`）；
  - 分辨率角标：自动识别媒体流并标出 `4K`、`1080P`、`720P`；
  - 未播胶囊：直观展示剧集/季未看集数统计，已看状态显示绿底对勾。
- **快速控制**：排序栏提供一键升序/降序快捷切换按钮，并在滚动超过 5 项时自动淡入**回到顶部浮动按钮（FAB）**。

### 🎬 专业级播放器（Media3 + FFmpeg）
- **流媒体协议支持**：DirectPlay 直流、HLS、DASH、SmoothStreaming、RTSP。
- **软硬件解码**：
  - 支持系统硬件解码优先与软件解码兼容模式切换；
  - 集成 Jellyfin Media3 FFmpeg 解码扩展（`media3-ffmpeg-decoder:1.5.0+1`），大幅提升非常规音视频格式与特殊容器的播放兼容性。
- **播放会话生命周期**：
  - 严格遵循 Emby 标准会话机制（`PlaybackInfo` 获取 `PlaySessionId`、`MediaSourceId`）；
  - 精确周期上报 `/Sessions/Playing/Progress`，自动上报 `Started` 与 `Stopped`；
  - 针对 STRM 外链和动态源，等待有效媒体时长（Duration）就绪后再规范上报，杜绝脏进度覆盖。
- **手势与控制**：横向滑动快进/快退、双击左/右进退、长按右半屏临时 2x 倍速、音轨/字幕轨切换、屏幕锁定、实时网络流速显示。
- **MediaSessionService 后台会话**：系统通知栏媒体控制器，支持进入后台暂停与通知恢复。

### 🛠️ 高级功能与容灾机制
- **动态端口解析（Auto-Port）**：
  - 针对使用 DDNS 或动态端口映射的家庭服务器，当发生网络异常（超时、502/503/504 等）时，自动抓取指定状态页提取最新可用端口并平滑重试请求。
- **本地磁盘 LRU 缓存**：支持自定义媒体缓存容量（0 / 64 / 128 / 256 / 512 MB）。
- **UI 缩放引擎**：内置 85% ~ 160% 多档界面缩放，轻松适配车机、投影仪、掌机等特殊 DPI 屏幕。
- **应用内诊断日志**：一键导出运行与解码日志，无需连接电脑 ADB 抓包。

---

## 📦 项目结构

```
EmbyPlayerNext/
├── app/
│   ├── build.gradle.kts                # 应用级构建配置（含 signingConfigs）
│   ├── ci-debug.keystore               # CI 统一调试密钥库（支持免卸载覆盖安装）
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/embyplayernext/he/
│       │   ├── data/                   # 数据模型、网络请求 (OkHttp)、本地偏好 (AppPreferences)
│       │   ├── playback/               # Media3 播放内核、后台服务、FFmpeg 渲染工厂
│       │   ├── ui/                     # Jetpack Compose UI
│       │   │   ├── components/         # MediaCard、MediaListItem、TvFocusScroll 焦点控制器
│       │   │   ├── screens/            # Home、Library、Detail、Player、Settings、Search 界面
│       │   │   └── theme/              # Material 3 主题配色
│       │   └── util/                   # 设备标识、日志器
│       └── res/                        # 图标、布局资源、TV 橫幅
├── .github/workflows/
│   └── android-build.yml               # GitHub Actions 自动化 CI 构建流
├── build.gradle.kts                    # 根构建脚本 (AGP 8.7.3, Kotlin 2.0.21)
├── settings.gradle.kts                 # 仓库源与模块声明
└── gradlew                             # Gradle Wrapper 启动脚本
```

---

## 🛠️ 构建与编译

### 环境要求
- **JDK**：Java 17 或 21
- **Android SDK**：API 35 (Android 15)
- **NDK ABI 过滤**：`arm64-v8a`（如需多架构可在 `app/build.gradle.kts` 中调整）

### 本地构建命令

通过终端直接运行 Gradle Wrapper：

```bash
# 赋予执行权限
chmod +x gradlew

# 编译 Release 生产安装包（已自动通过 ci-debug.keystore 签名）
./gradlew assembleRelease

# 编译 Debug 调试包
./gradlew assembleDebug
```

编译产物位置：
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### 签名配置说明

项目已内置并默认启用统一的调试签名库 [`app/ci-debug.keystore`](app/ci-debug.keystore)：
- **Store / Key Password**：`embyplayernext-ci`
- **Key Alias**：`embyplayernext-ci`
- **优点**：无论是本地编译还是 GitHub Actions CI 自动构建出来的 APK，签名证书均保持一致，更新版本时可以直接**覆盖安装**，无需先卸载原应用。

---

## 🚀 GitHub Actions 持续集成 (CI)

仓库内置了 [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml) 自动化流水线：
- **触发条件**：推送到 `main` 或 `feature/**` 分支，或在 GitHub Actions 页面手动点击 `workflow_dispatch`；
- **自动化产物**：构建成功后，直接在每期构建详情页面的 Artifacts 区域下载免签 Release / Debug APK。

---

## 📄 开源许可

- 项目源码遵循开源规范，使用的开源依赖包括：
  - [Jetpack Compose (AndroidX)](https://developer.android.com/jetpack/compose) - Apache 2.0
  - [AndroidX Media3](https://github.com/androidx/media) - Apache 2.0
  - [Jellyfin Media3 FFmpeg Decoder](https://github.com/jellyfin/jellyfin-androidtv) - GPLv3
  - [Coil](https://github.com/coil-kt/coil) - Apache 2.0
  - [OkHttp](https://github.com/square/okhttp) - Apache 2.0

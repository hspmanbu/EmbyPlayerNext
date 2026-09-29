# EmbyPlayerNext

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![SDK](https://img.shields.io/badge/Compile%20SDK-35-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/ExoPlayer-Media3%201.5.1-red)
![Version](https://img.shields.io/badge/Version-v2.3.27%20(Build%20150)-green)

**专为 Android TV 电视盒子、平板与手机深度定制的原生 Emby 客户端**  
*具备 TV 芯片硬解兼容改造 · D-Pad 边缘智能焦点 · 自适应媒体库双视图 · Media3 强劲播放内核 · 动态端口高容灾*

</div>

---

## 📺 核心重点一：Android TV 硬件解码兼容性专项改造

市面上大量 Android 电视盒子、投影仪、车机与外贸设备普遍搭载 **瑞芯微（Rockchip 如 RK3399 / RK3588 / RK3568 / RK3328）** 等国产或定制 SoC。在原生 ExoPlayer / Media3 下播放 **4K H.265 (HEVC)** 视频时，经常出现**黑屏死机、卡死闪退、丢帧严重或声画不同步**等硬解适配顽疾。

本工程专门针对电视端底层解码链路进行了深度定制与混合渲染改造：

### 1. 独创瑞芯微芯片直通硬解引擎（`RockchipDirectVideoRenderer`）
- **底层 OMX 硬件直连**：绕过系统原生通用 MediaCodec 的异步队列阻塞瓶颈，自动嗅探并优先调用芯片厂商原厂 OMX HEVC 硬件解码管道（如 `OMX.rk.video_decoder.hevc`）；
- **自适应混合调度工厂（`RockchipHybridRenderersFactory`）**：在保留音频、字幕、时钟同步完全由 Media3 统一接管的同时，将视频解码与 Surface 渲染抽离为原厂级直通管道；
- **智能分层平滑降级（Fallback）防护**：
  $$\text{Rockchip 原厂 OMX 硬解} \xrightarrow{\text{异常}} \text{标准 Media3 硬解} \xrightarrow{\text{失败}} \text{Jellyfin FFmpeg 软解扩展}$$
  三层解码回退机制彻底杜绝电视上因个别片源不兼容导致的闪退黑屏。

### 2. 低内存设备（Low-RAM TV Box）专项调优
- **动态自适应缓冲池**：自动检测设备是否为 Low-RAM，对 1GB/2GB 运存的老旧电视盒子动态将预加载缓冲由 15s/60s 降至 8s/30s，从源头防止 OOM 溢出崩溃；
- **禁用不稳定隧道模式（Tunneling）**：针对各类电视 ROM 的音频输出特性，关闭易导致音视频脱节的 Tunneling 模式，放宽分辨率限制与能力约束。

### 3. 可视化解码兼容切换与无 ADB 诊断日志
- **设置项独立开关**：在应用设置中提供“硬件解码”与“硬件解码兼容模式（针对电视芯片）”独立开关，赋予用户自主调试权；
- **内置诊断日志系统（`DiagnosticsLogger`）**：运行期间精准记录设备 MediaCodec 编码器清单、PTS 渲染帧率与 Rockchip 调度路线，一键导出为文本，排查解码问题无需连接电脑 ADB。

---

## 🎮 核心重点二：Android TV 遥控器与大屏交互重构

### 1. “到边才滚，屏内只移框”智能边缘焦点导航（`TvFocusScroll`）
以往 TV 客户端每按一次方向键整个屏幕都会晃动滑动的体验饱受诟病。本项目彻底重构了遥控器导航算法：
- **可视屏内零滑动**：按上下键时，只要目标元素在当前视口内可见，**屏幕完全静止，仅平滑转移焦点框**；
- **触达页面边缘才步进滚动**：
  - **向下触底**：只有当光标处于屏幕最底端、且屏幕下方仍有未显示内容时，再次按向下键才会平滑向下滚动一行；
  - **向上触顶**：同理，只有光标移至可视区域最顶端且上方有历史内容时，按向上键才会平滑向上滚动一行；
- **边界防跳离（`FocusProperties.exit`）**：在列表滚动中途，严格锁定焦点在内容区内；只有当滚动至第 1 行（绝对顶部）再次向上按时，焦点才自然升至分类标签与搜索栏。

### 2. 遥控器 D-Pad 视觉微动效
- 所有海报卡片、继续观看卡片、媒体库封面均接入 TV 焦点监听，获得焦点时呈现 **`1.05x` 平滑微放大、主题高亮边框与立体景深阴影**。
- 1080P/4K 超宽电视屏幕做了最大宽度限制与居中约束，防止卡片在大屏横向拉伸变形。

---

## 📚 媒体库列表与排版全方位进化

### 1. 响应式自适应列数（Auto）
- 列数选择器新增**自适应模式**：手机 3 列、平板/折叠屏 4～5 列、电视大屏 6～7 列；
- 支持在 2～8 列之间自由设定，每个媒体库独立记忆保存。

### 2. 海报网格与详尽列表（`MediaListItem`）双视图一键切换
- 顶部栏可秒级切换视图：
  - **海报网格**：沉浸式浏览，适合日常观影选片；
  - **列表详情视图**：紧凑展示海报、片名、上映年份、社区评分（`★ 8.5`）、影片时长（如 `2小时15分钟`）、分类流派标签以及剧情摘要。

### 3. 卡片细节排版
- **去除强制占位空白**：移除旧版单行片名导致的强制双行空白，单行片名自然紧贴年代与时长，视觉协调紧凑；
- **视觉徽章（Badges）**：
  - **分辨率标签**：媒体卡片左下角标出 `4K`、`1080P`、`720P`；
  - **评分徽章**：左上角金黄半透明毛玻璃评分标签；
  - **未播胶囊**：右上角醒目标出剧集未看集数统计，已看完显示绿底对勾。
- **快捷控制**：排序栏新增一键升序/降序快捷反转按钮；滚动超过 5 项时自动淡入**回到顶部浮动按钮（FAB）**。

---

## 🎬 播放器与网络高可用架构

- **播放内核**：基于 AndroidX Media3 1.5.1，集成 Jellyfin Media3 FFmpeg 解码扩展；
- **规范的会话生命周期**：严格遵循 Emby 标准 API 会话上报（Start / Progress / Stopped），支持 STRM 格式外链，杜绝脏进度覆盖；
- **动态端口容灾（Auto-Port）**：针对内网穿透或 DDNS 经常变动端口的服务器，当网络超时或遇到 502/503/504 错误时，自动抓取指定状态页实时解析最新端口并平滑重试；
- **本地磁盘 LRU 缓存**：支持自定义媒体缓存容量（0 / 64 / 128 / 256 / 512 MB）；
- **UI 缩放引擎**：内置 85% ~ 160% 多档界面缩放，轻松适配车机、投影仪、掌机等各类屏幕 DPI。

---

## 🛠️ 本地编译与覆盖安装说明

### 环境要求
- **JDK**：Java 17 或 21
- **Android SDK**：API 35 (Android 15)
- **编译工具**：Gradle 8.11+ / AGP 8.7.3

### 编译命令

```bash
# 赋予执行权限
chmod +x gradlew

# 编译 Release 正式安装包（推荐）
./gradlew assembleRelease

# 编译 Debug 调试包
./gradlew assembleDebug
```

编译输出路径：
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### 覆盖安装签名机制

项目在 [`app/build.gradle.kts`](app/build.gradle.kts) 中已将 Release 与 Debug 统一配置为内置的 [`app/ci-debug.keystore`](app/ci-debug.keystore) 签名：
- **证书所有人 (DN)**：`CN=EmbyPlayerNext CI Debug, O=EmbyPlayerNext, C=US`
- **签名优势**：无论本地编译还是不同构建环境打包，生成的 APK 签名指纹完全一致，**可直接覆盖升级安装，无需卸载原有应用**。

---

## 📄 开源依赖声明

- [AndroidX Media3](https://github.com/androidx/media) - Apache 2.0
- [Jellyfin Media3 FFmpeg Decoder](https://github.com/jellyfin/jellyfin-androidtv) - GPLv3
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Apache 2.0
- [Coil](https://github.com/coil-kt/coil) - Apache 2.0
- [OkHttp](https://github.com/square/okhttp) - Apache 2.0

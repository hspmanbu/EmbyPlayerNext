# Build17 功能审计与新源码映射

| Build17 功能 | 新源码位置 | 状态 |
|---|---|---|
| 登录/Token/服务器切换 | `EmbyApiClient`, `LoginDialog`, `AppPreferences` | 已实现 |
| 首页/媒体库/继续观看 | `HomeScreen`, `EmbyViewModel` | 已实现 |
| 媒体库搜索/文件夹 | `LibraryScreen` | 已实现 |
| 独立库排序记忆 | `AppPreferences.saveLibrarySort` | 已实现 |
| 详情/剧集/季/集 | `DetailScreen`, API seasons/episodes | 已实现 |
| 演职人员作品 | `getItemsByPerson` + Detail bottom sheet | 已实现 |
| 收藏/已看 | API FavoriteItems/PlayedItems | 已实现 |
| 删除服务器媒体 | `DELETE /Items/{Id}` | 已实现 |
| 续播/从头播放 | PlaybackDescriptor initial position | 已实现 |
| 动态端口 | `DynamicPortResolver`, `DynamicPortScreen` | 已实现并增强 |
| 缓存 | `PlayerCache` | 已实现 |
| 手势跨度 | `PlayerScreen` + Settings | 已实现 |
| 音轨/字幕/倍速 | `PlayerScreen` | 已实现 |
| 画面比例 | FIT/FILL/STRETCH | 已实现 |
| 屏幕锁定 | `PlayerScreen` | 已实现 |
| 网速 | `TrafficStats` | 已实现 |
| 后台自动暂停 | Lifecycle observer | 已实现 |
| 系统媒体控制 | `PlaybackService` / MediaSessionService | 已实现 |
| 硬解开关 | MediaCodecSelector | 已实现 |
| UI 缩放 | LocalDensity + Settings | 已实现 |
| STRM 总时长 | PlaybackInfo + Media3 duration fallback | 重新正确实现 |
| 播放状态 | 统一 Start/Progress/Stopped | 重新正确实现 |
| App 内日志导出 | DiagnosticsLogger/FileProvider | 新增 |
| 自签名 HTTPS 开关 | NetworkSupport | 新增 |

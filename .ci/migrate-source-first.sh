#!/usr/bin/env bash
set -euo pipefail

gradle wrapper --gradle-version 8.9 --distribution-type bin
chmod +x gradlew
rm -rf app/build build .gradle
rm -rf .ci EmbyPlayerNext-source-ci.zip

cat > .gitignore <<'EOF'
.gradle/
local.properties
.idea/
*.iml
build/
**/build/
captures/
.externalNativeBuild/
.cxx/
*.jks
*.keystore
!app/ci-debug.keystore
EOF

cat > README.md <<'EOF'
# EmbyPlayerNext

EmbyPlayerNext 是一个使用 Kotlin、Jetpack Compose 和 AndroidX Media3 实现的 Android Emby 客户端，面向手机、平板和 Android TV。

当前版本：**2.3.27**。

## 主要功能

- Emby 服务器登录、媒体库浏览、搜索、收藏、已看状态和删除。
- 电影、电视剧、季/集、演职人员和相关推荐详情页。
- 继续观看、从头播放、播放进度同步。
- Media3 播放器：播放/暂停、Seek、倍速、音轨、字幕、画面比例和屏幕锁定。
- 手机/平板手势：双击快进/快退、横向 Seek、左侧亮度、右侧音量。
- Android TV 遥控器导航、焦点状态、长按快搜和播放器快捷控制。
- 可调 UI 缩放，适配手机、平板和大屏电视。
- 本地播放缓存、动态端口恢复、应用内运行日志导出。
- Rockchip 兼容模式：仅在用户开启硬件兼容模式且检测到 vendor OMX HEVC 解码器时启用 Hybrid Direct OMX 视频 Renderer；其他设备保持 Media3 默认播放路径。

## 工程结构

仓库直接保存可构建 Android 源码，不再依赖历史版本补丁或 CI 运行时重建源码。

主要目录：

    app/
    gradle/wrapper/
    gradlew
    gradlew.bat
    build.gradle.kts
    settings.gradle.kts
    gradle.properties
    .github/workflows/android-build.yml

旧版本实验和补丁链仍保存在 Git 历史中，但不参与当前构建。

## 构建环境

- JDK 17
- Android SDK 35
- AGP 8.7.3
- Kotlin 2.0.21
- Gradle 8.9
- AndroidX Media3 1.5.1

Android Studio 直接打开仓库根目录即可。

Debug APK：

    ./gradlew :app:assembleDebug

完整 CI 验证：

    ./gradlew --no-daemon --stacktrace :app:lintDebug :app:assembleDebug :app:assembleRelease

## 签名

`app/ci-debug.keystore` 仅用于 CI/debug 测试包，以保持测试版本之间可以覆盖安装。它不是发布密钥。

正式发布 APK/AAB 应使用独立的 release signing key，并通过本地安全配置或 GitHub Actions Secrets 注入；不要把正式私钥提交到仓库。
EOF

cat > .github/workflows/android-build.yml <<'EOF'
name: Android Build

on:
  workflow_dispatch:
  push:
    branches:
      - main
      - feature/ui-library-rework-20260925
  pull_request:

concurrency:
  group: android-build-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    env:
      ANDROID_HOME: /usr/local/lib/android/sdk
      ANDROID_SDK_ROOT: /usr/local/lib/android/sdk

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle cache
        uses: gradle/actions/setup-gradle@v4

      - name: Install Android SDK packages
        shell: bash
        run: |
          SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKMANAGER" --licenses >/dev/null || true
          "$SDKMANAGER" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

      - name: Verify project metadata
        shell: bash
        run: |
          test -x gradlew
          test -f gradle/wrapper/gradle-wrapper.jar
          grep -q 'applicationId = "com.embyplayernext.he"' app/build.gradle.kts
          grep -q 'versionCode = 150' app/build.gradle.kts
          grep -q 'versionName = "2.3.27"' app/build.gradle.kts

      - name: Lint and build
        run: ./gradlew --no-daemon --stacktrace :app:lintDebug :app:assembleDebug :app:assembleRelease

      - name: Verify debug APK
        shell: bash
        run: |
          APK=app/build/outputs/apk/debug/app-debug.apk
          "$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK" | tee /tmp/badging.txt
          grep -q "package: name='com.embyplayernext.he'" /tmp/badging.txt
          "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$APK" | tee /tmp/signature.txt
          tr '[:upper:]' '[:lower:]' < /tmp/signature.txt | tr -d ':' | grep -q '9f72c5f3666451dd793a41079a3d8cf21f5ae82f9800d574583ec8db93228e8a'

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: EmbyPlayerNext-v2.3.27-arm64-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
          retention-days: 14

      - name: Upload unsigned release APK
        uses: actions/upload-artifact@v4
        with:
          name: EmbyPlayerNext-v2.3.27-arm64-release-unsigned
          path: app/build/outputs/apk/release/app-release-unsigned.apk
          if-no-files-found: error
          retention-days: 14
EOF

git config user.name 'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
git add -A
git update-index --chmod=+x gradlew
git commit -m 'migrate repository to direct source build'
git push origin HEAD:feature/ui-library-rework-20260925

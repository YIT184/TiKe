# 题刻 · Tike

一款用于刷题与专注训练的 Android 计时应用。记录实际用时、题数和正确率，按模块查看训练表现，支持同一局域网内的设备同步。无需注册账号。

**Android 8.0 及以上 · Kotlin / Jetpack Compose · 本地存储**

[下载安装包](https://github.com/YIT184/tike/releases/latest) · [更新记录](CHANGELOG.md)

## 页面预览

以下图片由实际应用页面渲染生成。记录和统计页使用虚构演示数据；公开安装包首次安装时没有预置练习记录。

<table>
  <tr><th>环形计时</th><th>经典计时</th><th>练习记录</th></tr>
  <tr>
    <td><img src="docs/screenshots/timer-rings.png" width="260" alt="环形计时：模块、时间和计时模式圆环"></td>
    <td><img src="docs/screenshots/timer-classic.png" width="260" alt="经典计时：计时模式和模块选择"></td>
    <td><img src="docs/screenshots/records.png" width="260" alt="练习记录：用时、题数和正确率"></td>
  </tr>
  <tr><th>训练统计</th><th>设置与提醒方式</th><th>设备同步</th></tr>
  <tr>
    <td><img src="docs/screenshots/statistics.png" width="260" alt="训练统计：时长趋势及分模块指标"></td>
    <td><img src="docs/screenshots/settings.png" width="260" alt="设置：计时布局、计时辅助、消息或闹钟提醒"></td>
    <td><img src="docs/screenshots/sync.png" width="260" alt="设备同步：搜索、配对及同步文件导入导出"></td>
  </tr>
</table>

分模块查看平均每题用时与正确率：

<img src="docs/screenshots/statistics-modules.png" width="320" alt="分模块统计：各模块的题数、正确率和平均每题用时">

## 功能

- **正计时与倒计时**：倒计时到点提醒后继续累计超时，手动结束时按实际用时保存。
- **两种计时界面**：经典布局与环形布局可在设置中切换。环形布局支持旋转或点击模块、模式；时间左右滑动，每格增减 5 分钟。
- **自定义倒计时**：双击圆环顶部的时间，输入任意 5–360 的整数分钟。
- **可选提醒方式**：消息弹窗使用通知提示音；闹钟提醒使用系统闹钟提示音。选择会保存。
- **自定义模块**：添加、保存和删除模块，删除模块不会清除历史记录；每个模块记住上次的计划题数。
- **练习复盘**：保存实际题数、正确题数和备注，可编辑、删除记录及导出 CSV。
- **分模块统计**：查看各模块的平均每题用时、正确率、练习量及用时趋势。
- **跨设备同步**：同一 Wi-Fi 或热点内搜索设备，双方同意后配对，保存配对信息；也支持 JSON 同步文件和旧版 CSV 导入。
- **计时辅助**：可选悬浮计时胶囊、屏幕常亮，支持深色模式。

## 安装与使用

1. 在 [Releases](https://github.com/YIT184/tike/releases/latest) 下载 `tike-v1.5.1.apk`，在 Android 手机上安装。
2. 选择计时模式、模块及计划题数，点击“开始”。
3. 完成练习后点击结束，填写实际题数、正确题数及备注，保存记录。
4. 在“记录”和“统计”查看训练情况，在“设置”调整布局、提醒方式和计时辅助。

倒计时提醒使用系统精确定时，并在暂停、继续时更新截止时间。请允许题刻发送通知；Android 12 如提示“闹钟和提醒”授权，允许后返回再次开始计时。声音、震动和弹出显示受系统通知通道及勿扰设置影响。

开启悬浮胶囊时，需要授予“显示在其他应用上层”权限。锁屏计时由常驻通知显示。部分系统的后台运行限制需要在系统设置中调整。HarmonyOS 原生实况窗是否显示还取决于设备系统及应用权益，普通计时通知可独立使用。

## 设备同步

入口：**设置 → 设备同步**。

两台设备连接同一 Wi-Fi 或热点后，搜索并选择对方，由对方同意配对。应用保存配对信息，后续可直接选择已配对设备同步。访客网络或开启设备隔离的网络可能阻止发现，此时可改用热点或同步文件。

JSON 文件可通过微信、蓝牙或网盘传给另一设备，再选择“导入并合并”。同步按记录标识合并，重复同步不会重复添加记录，编辑和删除状态也会参与合并。合并前会保留本机恢复副本。

正在进行的计时、系统权限和本机设置不参与同步。同步文件包含练习记录和备注，请只分享给需要接收这些数据的设备。

## 从源码构建

需要 JDK 17 或兼容版本、Android SDK 34，以及 Gradle 构建所需的网络连接。推荐使用 Android Studio 打开项目并配置 SDK。

```powershell
git clone https://github.com/YIT184/tike.git
cd tike
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Linux / macOS 可运行：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

调试安装包输出到 `app/build/outputs/apk/debug/app-debug.apk`。

项目路径包含中文时，Windows 可使用 `build.ps1` 临时映射盘符后构建。签名文件、SDK、本地配置及构建缓存不会提交到仓库。没有维护者本地签名文件时使用 Android 默认调试签名；自行构建的安装包可能无法覆盖下载版，应用升级要求包名与签名一致。

可单独重新生成 README 截图：

```powershell
.\gradlew.bat testDebugUnitTest -PreadmeScreenshots --tests 'com.codex.shuatitimer.ReadmeScreenshotsTest'
```

## 数据与隐私

公开版本只预置模块类型，不预置个人练习记录。记录存储在本机 Room 数据库中；没有账号系统、遥测或云端数据服务。局域网同步在用户主动操作时进行，初次配对需双方同意，传输使用加密连接。

同步文件和 CSV 由用户选择保存及分享位置。卸载应用可能删除本机数据和内部恢复副本，重要记录请先导出备份。升级已有数据库会保留记录与模块。

## 技术栈

Kotlin · Jetpack Compose / Material 3 · Room · DataStore / SharedPreferences · Android NSD · JUnit / Robolectric / Compose UI Test


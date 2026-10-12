# DiPlay · wudi81 分支

**给安卓车机用的无线／有线 CarPlay 接收端。** 本仓库是
[shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) 的二次开发分支，
基线是上游 **0.2.18**（`main` 分支），额外加入两项上游没有的功能：

1. **自动开启车机热点** —— 固件开机不自己开热点的车机，不再需要每次手动打开。
2. **使用 iPhone 的麦克风** —— 车机没有麦克风时，让通话和 Siri 改用手机自己的麦克风。

> 本分支是在**非比亚迪**车机上验证出来的（联发科 MT8227L 后装车机，Android 8.1）。
> 上游主要面向比亚迪，本分支补上了通用固件上缺失的那条路。

安装包名 `com.shihab.diplay.wudi81v18`，**与上游的 `com.shihab.diplay` 互不冲突，可同时安装**。

---

## 目录

- [这个分支解决了什么问题](#这个分支解决了什么问题)
- [本分支新增的两个功能](#本分支新增的两个功能)
- [支持的系统与架构](#支持的系统与架构)
- [与上游的差异](#与上游的差异)
- [功能清单](#功能清单)
- [安装与授权](#安装与授权)
- [已知限制](#已知限制)
- [从源码构建](#从源码构建)
- [许可与署名](#许可与署名)

---

## 这个分支解决了什么问题

### 问题一：开机后连不上，杀掉 App 重开就秒连

现象是「车辆启动后打开 DiPlay，长时间卡在启动阶段；关掉 App 再启动一次就能秒连」。

根因**不是连接慢，是热点根本没开**。DiPlay 的车机热点模式下，如果车机固件自己不在开机时打开
热点，DiPlay 只能干等超时（旧版本上限 120 秒），期间连接流程一直停在那里。
第二次启动之所以快，是因为第一次那段时间里热点已经被（手动）打开了。

**解决办法是让 DiPlay 自己把热点打开**，见下一节。

### 问题二：车机没有麦克风，通话和 Siri 没有输入

CarPlay 下麦克风音频默认由车机提供。车机没有麦克风时，通话对方听不到你、Siri 也收不到指令。
本分支加了一个开关，让 DiPlay 在协议层声明「本车机不提供麦克风输入」，把输入交还给 iPhone。

---

## 本分支新增的两个功能

### 1. 自动开启车机热点

**位置：设置 → 连接 → 「车机热点 · 自动开启」**（仅在**车机热点**模式下显示）

打开开关时会自动跳转到系统的「修改系统设置」页面，授权后返回即生效。

#### 为什么不能直接用 `WifiManager.setWifiApEnabled`

Android 8.0 起该方法已废弃，AOSP 里的实现只打一行日志然后返回 `false`，不再有任何作用。
真正可用的入口是隐藏 API `IConnectivityManager.startTethering`：

```
ConnectivityManager.mService            // 反射拿隐藏服务
  → startTethering(0, ResultReceiver, false, packageName)
```

#### 权限门槛分两种，代码会自己判断

`startTethering` 在服务端会做 `enforceTetherChangePermission` 检查，通过条件二选一：

| 固件情况 | 需要的权限 | 能否由普通安装获得 |
| --- | --- | --- |
| 固件**未**配置热点开通应用（`provisioningApp=false`） | `WRITE_SETTINGS`（app-op） | **可以**，在系统设置里手动授予 |
| 固件**已**配置热点开通应用 | `TETHER_PRIVILEGED`（签名权限） | 不可以，普通 App 无解 |

DiPlay 启动时会探测并打印属于哪一种：

```
Car hotspot capability: provisioningApp=... writeSettings=... writeSettingsPage=...
```

- `provisioningApp=false` → 路是通的，去授予「修改系统设置」即可
- `provisioningApp=true` → 该固件把网络共享保留给系统应用，这条路走不通

#### 授权方式

**「修改系统设置」不是运行时权限，永远不会出现在「应用信息 → 权限」列表里。** 它是一个特殊访问权限
（app-op），只能在：

> **设置 → 应用 → 特殊访问（或「特殊应用权限」）→ 修改系统设置 → 找到 DiPlay → 打开**

DiPlay 的开关会自动跳到这个页面，不用自己找。

#### 关于热点配置

**DiPlay 从不读取、也不修改你已保存的热点名称和密码。** 读取配置需要 `OVERRIDE_WIFI_CONFIG`
权限，而且那份配置正是 DiPlay 要连接的目标。DiPlay 只做一件事：请求固件启动它自己已经保存的热点。
成功之后不关闭、不重配。

#### 上游也有类似功能，但门槛不同

上游 0.2.18 同样实现了自动开热点（`CarHotspotTethering`），但它的入口被限定为
**比亚迪车机 + 本地 ADB 授权**（`appops set <包名> WRITE_SETTINGS allow`）。
本分支把判定放开：非比亚迪固件走系统设置页手动授权，因为门槛本来就是同一个 `WRITE_SETTINGS`。
热点本身仍由上游那套实现启动。

### 2. 使用 iPhone 的麦克风（实验性）

**位置：设置 → 音频 → 「使用 iPhone 的麦克风」**（默认**关闭**）

原理在 AirPlay 协议层：`/info` 响应里的 `audioFormats` 条目可以带 `audioInputFormats` 字段。

- 带上它 → 声明「本车机提供麦克风输入」→ iPhone 用车机麦克风
- 不带它 → 声明「本车机不提供麦克风输入」→ iPhone 应该改用自己的麦克风

打开开关后 DiPlay 就不带这个字段，同时也不启动本地音频采集。

> **默认关闭是有意的。** 有麦克风的车机应该继续用车机麦克风——它离驾驶员更近，通话质量更好。
> 只有确实没有麦克风的车机才需要打开。

> ⚠️ **这是实验性功能，效果未经确认。** 协议层只是「声明能力」，iOS 是否真的回退到 iPhone 麦克风，
> 需要在实车上打电话验证。如果无效，说明 iOS 在 CarPlay 下强制要求车机提供麦克风，这条路走不通。

**设置要重连才生效**，因为 `/info` 只在会话建立时发送一次。

---

## 支持的系统与架构

| 项目 | 说明 |
| --- | --- |
| 系统 | Android 7.1+（API 25+），`minSdk 25` / `targetSdk 37` |
| 架构 | `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` —— 四个都打，ARM 和 Intel 车机通用 |
| 安装方式 | 车机需允许安装第三方 APK；无需 root、越狱、转接盒、账号或认证服务器 |
| 连接方式 | 有线 USB、车机热点、Wi-Fi Direct、现有 Wi-Fi／同一局域网 |
| 语言 | 英语、简体中文、繁体中文、日语、阿拉伯语、俄语、乌克兰语、西班牙语 |

### 实车验证状态

本分支在以下车机上做过完整实车验证：

| 项目 | 结果 |
| --- | --- |
| 车机 | `alps F9212A`，硬件 `ac8227l`（联发科 MT8227L，ARM），固件 `F9212A_00017_V001` |
| 系统 | Android 9.1 显示版本 / 实际 **API 27**（Android 8.1），1024×600 |
| 手机 | iPhone 14 Pro（`iPhone15,2`），AirPlay 870.14.1 |
| 无线 CarPlay（车机热点） | **已确认可用**，手机无需解锁即可连接 |
| 自动开启车机热点 | **已确认可用**：冷启动拉起热点约 5.7 秒，已开启时约 1.1–1.6 秒 |
| 视频解码 | 联发科硬件 H.264（`OMX.MTK.VIDEO.DECODER.AVC`） |
| 使用 iPhone 的麦克风 | **未验证**，见上文说明 |
| Wi-Fi Direct / 同一局域网 | 未在本车机上验证 |

上游自身声明：Android 7.1–8.1 的支持**尚未在任何车辆上确认**。本分支的验证结果正好补上了这一点。

---

## 与上游的差异

| 项目 | 上游 0.2.18 | 本分支 |
| --- | --- | --- |
| 自动开启车机热点 | 有，但限定比亚迪车机 + 本地 ADB 授权 | **放开到任意固件**，走系统设置页手动授权 |
| 使用 iPhone 的麦克风 | 无 | **有**，默认关闭，实验性 |
| 热点等待上限 | 15 秒（开热点与等热点共用同一预算） | 开热点请求仍 15 秒；**等车机自己的热点放宽到 60 秒**，避免慢固件被截断 |
| 包名 | `com.shihab.diplay` | `com.shihab.diplay.wudi81v18`（可共存） |
| 版本号 | 0.2.18（37） | 0.2.18（47） |
| 音频丢帧修复 | 有（含未发布的 #644/#649） | **相同** —— 基线取的是 `main` 而非 0.2.18 标签 |
| 四架构打包 | 有 | 相同 |
| 启动恢复（首包看门狗 + 退避重试） | 有 | 相同，未改动 |

> **基线为什么取 `main` 而不是 `v0.2.18` 标签**：0.2.18 引入了一个回归（issue #649）——
> 屏幕在后台时解码器输入等待被放宽到 0.5 秒，慢解码器会越落越远，队列涨到 190 包，音乐每几秒欠载一次。
> 修复提交 `7d974f42` 把等待上限设为 100 毫秒，**只存在于 `main`，尚未发布**。

---

## 功能清单

### 本分支新增

- 自动开启车机热点（含权限探测、手动授权入口、状态显示）
- 使用 iPhone 的麦克风（实验性，默认关闭）

### 继承自上游 0.2.18

- 有线 USB 与无线 CarPlay，本地认证，无需认证服务器
- 车机热点、Wi-Fi Direct、现有 Wi-Fi／同一局域网三种无线方式
- 启动恢复：首包看门狗（30 秒无 CarPlay TCP 接入则清理重试）+ 2/4/8/16/30 秒退避重试，稳定播放 60 秒后重置
- 音乐丢帧修复、音频欠载恢复、接收诊断保存
- 地址自动发现、固定信道回退、成功配置记忆
- 图标／文字大小、分辨率、帧率设置（改显示设置会重连）
- **设置备份与恢复**：设置 → 关于 → 用手机扫码导出／导入设置
- 本地诊断报告导出（只有你主动分享才会外传）
- 方向盘媒体键、仪表车速表、环境灯等可选功能
- 实验性选项（默认关闭）：Harman 全屏、直角无安全区、USB 导航语音增强、联发科低延迟解码、
  关闭媒体键音频焦点、通话走车机电话音频、FCE 仪表导航

---

## 安装与授权

### 1. 安装

下载本仓库 Release 里的 APK，拷到车机安装。**装在车上，不是装在 iPhone 上。**

> 本仓库的 APK 使用 **Android 调试签名**，仅适合自测与社区分发。它无法覆盖安装上游的正式版
> （签名不同），但可以与本分支的旧版本原地升级。

### 2. 连接一次 CarPlay

首次进入会让选择连接方式。用**车机热点**模式时，填入车机热点的名称和密码
（设置 → 连接 → 编辑已保存的热点）。

### 3. 授予「修改系统设置」

设置 → 连接 → 「车机热点 · 自动开启」→ 打开开关 → 自动跳到系统页面 → 勾选 DiPlay → 返回。

返回后开关旁会显示当前状态：已授予 / 尚未授予。

### 4. （可选）打开 iPhone 麦克风

车机没有麦克风时：设置 → 音频 → 「使用 iPhone 的麦克风」→ 打开 → **重连一次 CarPlay**。

### 5. 排查问题

设置 → 诊断 → 保存诊断报告，导出 `.txt` 后附到 issue。**报告不会自动上传，请自行检查并删除隐私信息
（尤其是热点密码）。**

---

## 已知限制

- **未经 Apple 认证。** APK 内含从公开 Carlinkit 固件中提取的实验性配件身份，不是为 DiPlay 签发的
  MFi 身份，其中私钥可被提取。未来 iOS 是否继续接受、能否公开分发，均未确定。
- **「修改系统设置」授权是必需的。** 没授予的话自动开热点不会生效；而如果固件把网络共享保留给
  系统应用（`provisioningApp=true`），这条路对普通 App 无解。
- **使用 iPhone 的麦克风未经实车确认。** 见上文。
- 上游从未在 Android 8.1 上实车验证，本分支虽然补上了，但**只验证了这一台车机**，
  其他车机、其他固件的行为不保证。
- 部分车机在高视频负载下仍可能卡顿。Wi-Fi Direct 在车机自己的 Wi-Fi 客户端搜网时，
  每 10 秒左右会丢一批音频包——**车机热点模式没有这个问题**，所以推荐用车机热点。
- 读不到热点配置的固件（`configReadable=false`）下，DiPlay 无法校验你填的 SSID／密码
  是否与车机实际热点一致，界面上的信道／频段会显示未知。
- 有线连接需要数据口，且主机／设备角色正确。

---

## 从源码构建

需要 JDK 25、Android SDK 37、NDK 28.2.13676358。

```bash
export JAVA_HOME=/path/to/jdk-25
export ANDROID_HOME=/path/to/Android/Sdk
export DIPLAY_AUTH_ASSETS_DIR=/path/to/runtime-assets   # 认证资源目录，见下

./gradlew :mobile:assembleStandaloneDebug
```

产物：`mobile/build/outputs/apk/debug/mobile-debug.apk`

`DIPLAY_AUTH_ASSETS_DIR` 需包含 `offline-mfi/identity.pk8` 与 `offline-mfi/certificate.p7b`。
**认证资源与签名密钥都不在本仓库里**（`.gitignore` 已排除），构建时会校验资源完整性，
缺失或多余都会失败。

主应用是 `mobile` 模块；`maphost` 是地图演示程序。

构建常见问题：

- 换过源码树后如果 NDK 报 `hash_key.txt (拒绝访问)`，删掉 `*/build/.cxx` 再编。
- 打包任务 `assembleStandaloneDebug` **不跑 lint**；要跑 lint 得显式执行 `:shared:lintDebug` 等。

---

## 许可与署名

本项目基于 **[xcertplay](https://github.com/shilapi/xcertplay)**，**GPL-3.0**。
主页／设置界面与站点改造自 **[DiAuto](https://github.com/shihabal3amri/DiAuto)**，AGPL-3.0，
许可证文本见 `docs/licenses`。**分发修改版时必须保留这些声明。**

### 上游链条

```
shilapi/xcertplay                      原始项目
  └─ shihabal3amri/DiPlay              主线，本分支的基线（0.2.18 / main）
       └─ hiscatwang/DiPlay            比亚迪车机适配分支
            └─ 本仓库                  wudi81 分支
```

- 上游主线：[github.com/shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)
- 直接上游分支：[github.com/hiscatwang/DiPlay/tree/android81-universal](https://github.com/hiscatwang/DiPlay/tree/android81-universal)
- 上游文档：[安装与连接](docs/INSTALL.md) · [兼容性与排查](docs/COMPATIBILITY.md) ·
  [无线流畅度](docs/SMOOTH_WIRELESS.md) · [缓冲音乐](docs/BUFFERED_AUDIO.md) ·
  [隐私与诊断报告](docs/PRIVACY.md) · [从源码构建](docs/BUILD.md)

CarPlay 及其图标归 Apple Inc. 所有。本项目与 Apple、比亚迪均无关联或背书关系。

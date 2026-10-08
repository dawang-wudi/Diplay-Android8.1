# DiPlay 车机版 · 自动开启车机热点（wudi81 v15）

基于上游 [hiscatwang/DiPlay](https://github.com/hiscatwang/DiPlay) 的 `android81-universal`
分支（v15 通用版），在其上增加**由 DiPlay 自动开启车机自带热点**的能力。
保留原作者与所有上游贡献者的署名。

- **上游**：[hiscatwang/DiPlay](https://github.com/hiscatwang/DiPlay)（`android81-universal` 分支，v15 通用版）
- **更上游**：[shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)（原作者项目）
- **许可**：GPL-3.0（继承上游，不可更改），另见[非商业维护与素材许可说明](docs/LICENSING.zh-CN.md)

## 本版相对上游增加了什么

上游要求在车机设置里**手动**打开自带热点，DiPlay 只负责等待它出现。如果车机的热点开关
在开机后默认是关的，用户每次上车都得先手动打开，否则 DiPlay 会一直等到超时。

本版让 DiPlay 在连接前**自己把车机热点打开**：

- 连接前调用 `IConnectivityManager.startTethering` 开启车机自带热点，失败则回退到上游原有的等待逻辑。
- **不读取也不修改**已保存的热点 SSID / 密码——那需要签名权限 `OVERRIDE_WIFI_CONFIG`，
  而且用户已保存的凭据本来就是 DiPlay 要连接的那个。
- 连接前先探测本机固件的权限门槛，在设置页显示结论，并提供一键跳转到授权页面的入口。
- 全程**尽力而为**：任何拒绝、超时或不支持都只记录日志，绝不中断 CarPlay 连接。

设置项位于 **DiPlay → 设置 → 自动连接 → 自动开启车机热点**（CarPlay 主机界面的设置里也有一个）。
默认开启，可随时关闭。

### 为什么需要「修改系统设置」权限

Android 把热点开关保留给系统应用。真正的门槛只有一处——`ConnectivityManager.enforceTetherChangePermission`：

| 固件是否配置了热点开通应用（`config_mobile_hotspot_provision_app`） | 需要的权限 | 普通应用能否拿到 |
| --- | --- | --- |
| 否（车机、无蜂窝设备通常如此） | `WRITE_SETTINGS` app-op | **能** |
| 是（手机通常如此） | `TETHER_PRIVILEGED`（signature） | 否，无解 |

本版在设置页会直接告诉你属于哪一种。若是第一种，去
**设置 → 应用 → 特殊访问（特殊应用权限）→ 修改系统设置 → 找到 DiPlay → 打开** 即可，
设置页里也有一键跳转按钮。

> **注意**：`WRITE_SETTINGS` 是**特殊访问权限**，**不会**出现在「应用信息 → 权限」列表里，
> 只在上面那个位置。找不到属正常现象。

等效的命令行方式：

```sh
adb shell appops set com.shihab.diplay.wudi81v15 WRITE_SETTINGS allow
```

## 支持的系统与架构

| 项 | 值 |
| --- | --- |
| 最低系统 | **Android 8.1（API 27）** |
| 目标系统 | API 37 |
| CPU 架构 | `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`（四架构同一个 APK） |
| 连接方式 | 无线 CarPlay（车机自带热点 / 同一 Wi-Fi 局域网 / Wi-Fi Direct）、USB 有线 CarPlay |

## 功能清单

### 本分支新增

- **自动开启车机热点**（见上）。

### 继承自上游 v15 通用版

- **Android 8.1 / 9 的 Wi-Fi Direct 修复**：显示并保存该选项，不再因系统版本隐藏或重置；
  API 27 / 28 改用公开的 `createGroup(Channel, ActionListener)` 并读取系统实际生成的 SSID 与密码；
  不再把 Wi-Fi Direct 请求悄悄改成 LocalOnlyHotspot；未知信道不再阻止启动。

### 继承自上游 v14

- **「同一 Wi-Fi／局域网」模式**：车机与 iPhone 同连路由器、随身 Wi-Fi 或另一台手机的热点，
  DiPlay 不创建热点。支持 IPv4 / IPv6 双栈服务发现与监听。
- **有界启动恢复**：
  - 发出启动指令后 **30 秒**内没有真正的 CarPlay TCP 接入 → 清理该次连接并重试；
  - 网络未就绪或上述超时按 **2、4、8、16、30 秒**退避，最多自动重试 **5 次**；
  - 同一热点地址**连续确认 3 次**后才发布服务；
  - 播放稳定 **60 秒**后重置重试预算；
  - 可检测的配置不匹配时**停止**自动重试并提示检查设置。
- 旧连接的超时回调不会关闭新连接。

### 继承自上游 v12 及更早

无线与 USB 有线 CarPlay、音频焦点、导航与 Siri 音道、方向盘切歌、画中画、
仪表盘/抬头显示输出、四架构支持，以及既有的显示与分辨率适配。

## 版本对照

| 版本 | 内容 | versionCode |
| --- | --- | --- |
| 上游 v12 | 方向盘切歌、Siri 跟随导航音道、四架构通用 | 41 |
| 上游 v14 | v12 功能 + 同一局域网模式 + 有界启动恢复 | 43 |
| 上游 v15 | v14 功能 + Android 8.1 / 9 Wi-Fi Direct 兼容 | 44 |
| **本版** | **v15 全部功能 + 自动开启车机热点** | **45** |

## 下载与安装

在 [Releases](../../releases) 页面下载 APK 与校验文件。

- 包名：`com.shihab.diplay.wudi81v15`
- 与上游发布的 `com.shihab.diplay.ora81` **不同**，两者可以**共存**，便于对比。
- 本包使用本地构建的 debug 签名，**不能覆盖安装**上游发布的同包名 APK，也不与其共享设置。

安装后建议按顺序做两件事：

1. 按上一节授予「修改系统设置」权限；
2. 打开 DiPlay，在连接设置里填好车机自带热点的 SSID 与密码，然后连接。

## 验证状态

| 项目 | 状态 |
| --- | --- |
| 四架构构建、包名与权限声明、认证资源打包 | 已核对 |
| 无线 CarPlay 连接（车机自带热点） | **车主已在实车确认可连接** |
| 自动开启车机热点的权限门槛探测 | **已在实际车机上取得诊断结果**（该固件未配置热点开通应用，门槛为 `WRITE_SETTINGS`，路径可行） |
| 自动开启热点的端到端效果 | **待授予权限后实车确认** |
| 同一 Wi-Fi／局域网模式 | 未在实机验证 |
| Wi-Fi Direct | 未在实机验证 |

已验证的车机环境：`alps F9212A`，硬件 `ac8227l`（联发科，ARM），
Android 9.1 / API 27，屏幕 1024×600。

**本版不构成对任意车机的兼容性承诺。** 不同车机的固件、音频通路、热点实现差异很大，
请以自己车机上的实测结果为准。

## 从源码构建

需要 **JDK 25**、**Android SDK 37**、**NDK 28.2.13676358** 和项目自带的 Gradle wrapper。

运行时认证资源（`identity.pk8` / `certificate.p7b`）**不在源码树里**，
需要从上游公开的 `DiPlay-0.2.10.apk` 提取到源码树**之外**的目录：

```sh
python scripts/prepare_ora81_runtime.py /path/to/DiPlay-0.2.10.apk /path/to/runtime-assets
```

然后：

```sh
DIPLAY_AUTH_ASSETS_DIR=/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

产物：`mobile/build/outputs/apk/debug/mobile-debug.apk`。
不带 `DIPLAY_AUTH_ASSETS_DIR` 时构建出的 APK 不含认证身份，**不能**作为独立接收端使用。

详见 [docs/BUILD.md](docs/BUILD.md) 与 [docs/MULTI_ABI.md](docs/MULTI_ABI.md)。

## 文档

- [本版更新说明](docs/WUDI81_V15.md)
- [连接方式设置](docs/CONNECTION_SETUP.md) · [同一 Wi-Fi／局域网](docs/EXISTING_WIFI.md)
- [v15 Wi-Fi Direct 说明](docs/V15_ANDROID81_WIFI_DIRECT.md) · [v14 局域网与重试说明](docs/V14_LAN_RETRY.md)
- [安装](docs/INSTALL.md) · [构建](docs/BUILD.md) · [兼容性](docs/COMPATIBILITY.md)
- [无线连接诊断](docs/WIRELESS_DIAGNOSTICS.md) · [测试](docs/TESTING.md)
- [隐私说明](docs/PRIVACY.md) · [第三方声明](docs/THIRD_PARTY_NOTICES.md)
- [上游原始 README](docs/UPSTREAM-README.md) · [上游原始 README（中文）](docs/UPSTREAM-README.zh-CN.md)

## 许可与署名

本项目沿用上游许可 **GPL-3.0**，详见 [LICENSE](LICENSE)。

原项目 [DiPlay](https://github.com/shihabal3amri/DiPlay) 由 shihabal3amri 及各位贡献者开发；
Android 8.1 / 通用架构适配由 [hiscatwang/DiPlay](https://github.com/hiscatwang/DiPlay) 维护。
本分支在其基础上增加自动开启车机热点的能力，版权归原作者与各贡献者所有。

本项目面向个人研究、车友交流和非商业维护。「非商业维护」的定位、BYDMate 图标等素材的许可与
品牌权利单独适用，见[非商业维护与素材许可说明](docs/LICENSING.zh-CN.md)与
[第三方声明](docs/THIRD_PARTY_NOTICES.md)。随包实验性运行资源沿用上游公开安装包，
不代表 Apple 官方认证；认证资产由外部目录提供，源码不包含 Android 签名私钥。

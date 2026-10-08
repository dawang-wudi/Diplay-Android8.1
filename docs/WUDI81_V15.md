# wudi81 v15：自动开启车机热点

本版基于上游 [hiscatwang/DiPlay](https://github.com/hiscatwang/DiPlay) 的 `android81-universal`
分支 v15 通用版（versionCode 44），在其上增加**由 DiPlay 自动开启车机自带热点**的能力。

| 项 | 值 |
| --- | --- |
| 包名 | `com.shihab.diplay.wudi81v15` |
| versionCode | **45** |
| versionName | `0.2.10-android81-test15-hotspot-universal` |
| 最低系统 | Android 8.1（API 27） |
| 目标系统 | API 37 |
| CPU 架构 | `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` |

包名与上游发布的 `com.shihab.diplay.ora81` 不同，两者可以共存，便于对照。
本包使用本地构建的 debug 签名，不能覆盖安装上游发布的同包名 APK，也不与其共享设置。

## 为什么需要这个功能

上游的连接流程是「**等待**车机自带热点出现」——用户在车机设置里手动打开热点，DiPlay 负责等待并连接。
对热点开关在开机后默认为关的车机，用户每次上车都要先手动开一次热点，否则 DiPlay 会一直等到超时。

本版把这一步交给 DiPlay 自己完成。

## 实现原理

### 为什么不用 `WifiManager.setWifiApEnabled`

这是最容易踩的坑。Android 8.0 起，`WifiManager.setWifiApEnabled` **已经是空壳**：

```java
// frameworks/base/wifi/java/android/net/wifi/WifiManager.java
public boolean setWifiApEnabled(WifiConfiguration wifiConfig, boolean enabled) {
    String packageName = mContext.getOpPackageName();
    Log.w(TAG, packageName + " attempted call to setWifiApEnabled: enabled = " + enabled);
    return false;   // 不再调用服务
}
```

它只打一行日志就返回 `false`，**不会打开热点**。而且即便走到服务端，那条旧路径只会拉起
接入点、不带 Tethering 的 DHCP 服务，客户端拿不到 IP。

### 实际使用的入口

本版改用 tethering：

```
IConnectivityManager.startTethering(TETHERING_WIFI, receiver, false, packageName)
```

`ConnectivityService.startTethering` 只做一件事——校验
`ConnectivityManager.enforceTetherChangePermission`，而**不是**直接要求 `TETHER_PRIVILEGED`
（`ConnectivityManager.startTethering` 上的 `@RequiresPermission(TETHER_PRIVILEGED)` 只是 lint 注解）。

权限门槛因此只有一处：

```java
if (config_mobile_hotspot_provision_app.length == 2) {
    enforceCallingOrSelfPermission(TETHER_PRIVILEGED, "ConnectivityService");   // 普通应用无解
} else {
    Settings.checkAndNoteWriteSettingsOperation(context, uid, callingPkg, true); // 只需 WRITE_SETTINGS
}
```

| 固件是否配置了热点开通应用 | 需要的权限 | 普通应用能否拿到 |
| --- | --- | --- |
| 否（车机、无蜂窝设备通常如此） | `WRITE_SETTINGS` app-op | **能** |
| 是（手机通常如此） | `TETHER_PRIVILEGED`（signature） | 否 |

本版在连接前读取 `config_mobile_hotspot_provision_app` 的长度，**在尝试之前**就能判断属于哪一类，
并在设置页显示结论。

### 实现要点

- `Method` 必须从**接口类** `Class.forName("android.net.IConnectivityManager")` 上获取，
  不能在 `mService` 返回的代理对象上获取——AIDL 生成的 `Stub.Proxy` 是私有静态类，
  从它取方法再调用会抛 `IllegalAccessException`。
- `callerPkg` 必须传**自己的包名**，`checkAndNoteWriteSettingsOperation` 会校验该包名的 uid
  是否等于 `Binder.getCallingUid()`。
- 解包 `InvocationTargetException` 的 cause，才能区分「缺权限」和「其他失败」。
- 成功判据使用 tethering 回调的 `resultCode`（`TETHER_ERROR_NO_ERROR = 0`），
  而不是只轮询 `getWifiApState`——部分固件不暴露接入点状态。
- `TETHER_ERROR_*` 常量是 `@hide`，按 AOSP 稳定值本地定义，不直接引用 SDK。
- 不读取也不修改已保存的热点配置：那需要签名权限 `OVERRIDE_WIFI_CONFIG`，
  而用户已保存的凭据本来就是 DiPlay 要连接的那个。

### 失败处理

**尽力而为，绝不中断连接。** 拒绝、超时、不支持都只写入诊断日志，随后继续走上游原有的
热点等待与重试逻辑（v14 的 120 秒等待、30 秒首包看门狗、2/4/8/16/30 秒退避重试）。
热点可能本来就开着，最终判定权仍在上游那段等待逻辑。

## 授予权限

`WRITE_SETTINGS` 是**特殊访问权限（app-op）**，**不会**出现在「应用信息 → 权限」列表里。

- **图形界面**：设置 → 应用 → 特殊访问（特殊应用权限）→ **修改系统设置** → 找到 DiPlay → 打开。
  设置页里的「授予修改系统设置权限」按钮会直接跳到该页面。
- **命令行**：`adb shell appops set com.shihab.diplay.wudi81v15 WRITE_SETTINGS allow`

## 设置项位置

| 位置 | 路径 |
| --- | --- |
| DiPlay 主设置页 | 设置 → **自动连接** → 「自动开启车机热点」 |
| CarPlay 主机设置 | 连接过程中呼出设置浮层 → 无线热点区块下方 |

两处下方都会显示本机固件的权限门槛结论，未授权时提供跳转按钮。默认**开启**，可随时关闭。
设置项在「车载热点」模式下生效。

## 继承的功能

本版完整保留上游 v15 的全部能力：

- **v15**：Android 8.1 / 9 的 Wi-Fi Direct 兼容（显示并保存该选项、使用公开的
  `createGroup(Channel, ActionListener)`、读取系统实际生成的凭据、不再暗中切换为 LocalOnlyHotspot、
  未知信道不再阻止启动）。
- **v14**：「同一 Wi-Fi／局域网」模式（车机与 iPhone 同连外部网络，不创建热点，
  支持 IPv4 / IPv6 双栈发现）；有界启动恢复（30 秒首包看门狗、2/4/8/16/30 秒退避最多 5 次、
  热点地址连续确认 3 次、稳定 60 秒后重置预算、配置不匹配时停止重试）。
- **v12 及更早**：无线与 USB 有线 CarPlay、音频焦点、导航与 Siri 音道、方向盘切歌、
  画中画、仪表盘/抬头显示输出、显示与分辨率适配、四架构支持。

## 验证状态

| 项目 | 状态 |
| --- | --- |
| 四架构构建、包名与权限声明、认证资源打包 | 已核对 |
| 无线 CarPlay 连接（车机自带热点） | **车主已在实车确认可连接** |
| 权限门槛探测 | **已在实际车机上取得诊断结果**：该固件 `provisioningApp=false`、
  `writeSettingsPage=true`，门槛为 `WRITE_SETTINGS`，路径可行 |
| 自动开启热点的端到端效果 | **待授予权限后实车确认** |
| 同一 Wi-Fi／局域网模式 | 未在实机验证 |
| Wi-Fi Direct | 未在实机验证 |

已验证的车机环境：`alps F9212A`，硬件 `ac8227l`（联发科，ARM），Android 9.1 / API 27，
屏幕 1024×600。

**本版不构成对任意车机的兼容性承诺。** 不同车机的固件、音频通路、热点实现差异很大，
请以自己车机上的实测结果为准。

## 构建

需要 JDK 25、Android SDK 37、NDK 28.2.13676358 与项目自带的 Gradle wrapper。
运行时认证资源需从上游公开的 `DiPlay-0.2.10.apk` 提取到源码树之外：

```sh
python scripts/prepare_ora81_runtime.py /path/to/DiPlay-0.2.10.apk /path/to/runtime-assets
DIPLAY_AUTH_ASSETS_DIR=/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

详见 [BUILD.md](BUILD.md) 与 [MULTI_ABI.md](MULTI_ABI.md)。

## 许可

沿用上游 **GPL-3.0**。原项目由 [shihabal3amri](https://github.com/shihabal3amri/DiPlay)
及各位贡献者开发，Android 8.1 / 通用架构适配由
[hiscatwang](https://github.com/hiscatwang/DiPlay) 维护。
本分支在其基础上增加自动开启车机热点的能力，版权归原作者与各贡献者所有。
见 [LICENSING.zh-CN.md](LICENSING.zh-CN.md) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

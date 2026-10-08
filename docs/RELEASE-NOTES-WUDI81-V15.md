# wudi81 v15 发布说明

> 用于 GitHub Release 的说明文本，可直接粘贴。

## DiPlay 车机版 · 自动开启车机热点

基于上游 [hiscatwang/DiPlay](https://github.com/hiscatwang/DiPlay) `android81-universal`
分支的 v15 通用版，增加**由 DiPlay 自动开启车机自带热点**的能力。

### 下载

- `mobile-debug.apk` — 安装包
- `SHA256SUMS.txt` — 校验值
- `*-source.zip` — 对应源码（与仓库 tag 一致）

### 本版信息

| 项 | 值 |
| --- | --- |
| 包名 | `com.shihab.diplay.wudi81v15` |
| versionCode | 45 |
| versionName | `0.2.10-android81-test15-hotspot-universal` |
| 最低系统 | Android 8.1（API 27） |
| CPU 架构 | `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` |

包名与上游发布的 `com.shihab.diplay.ora81` 不同，**两者可以共存**，便于对照。
本包使用本地构建的 debug 签名，不能覆盖安装上游发布的同包名 APK，也不与其共享设置。

### 本版新增

- **自动开启车机自带热点。** 连接前调用 `IConnectivityManager.startTethering` 打开车机热点，
  不再要求用户每次上车手动开启。不读取也不修改已保存的热点 SSID / 密码。
- **固件权限门槛探测。** 读取 `config_mobile_hotspot_provision_app` 判断本机需要
  `WRITE_SETTINGS` 还是无法获取的签名权限，并在设置页显示结论、提供一键跳转授权入口。
- **设置项「自动开启车机热点」**，位于 DiPlay 主设置页与 CarPlay 主机设置页，默认开启。
- 声明 `WRITE_SETTINGS` 权限，使应用出现在「特殊访问 → 修改系统设置」列表中。

### 继承自上游 v15

- Android 8.1 / 9 的 Wi-Fi Direct 兼容修复。
- v14 的「同一 Wi-Fi／局域网」模式与有界启动恢复
  （30 秒首包看门狗、2/4/8/16/30 秒退避最多 5 次、热点地址连续确认 3 次、稳定 60 秒后重置预算）。
- v12 及更早的无线与 USB CarPlay、音频焦点、导航与 Siri 音道、方向盘切歌、画中画、
  仪表盘输出与显示适配。

### 安装后需要做一件事

自动开启热点需要**「修改系统设置」特殊访问权限**。它是特殊访问权限，
**不会**出现在「应用信息 → 权限」列表里：

> 设置 → 应用 → 特殊访问（特殊应用权限）→ **修改系统设置** → 找到 DiPlay → 打开

DiPlay 的设置页会显示本机固件的权限门槛结论，并提供跳转按钮。也可用命令行：

```sh
adb shell appops set com.shihab.diplay.wudi81v15 WRITE_SETTINGS allow
```

若设置页显示「该固件把网络共享保留给系统应用」，说明本机需要签名权限，自动开启不可用，
请继续在车机设置里手动打开热点——其余功能不受影响。

### 验证状态

| 项目 | 状态 |
| --- | --- |
| 四架构构建、包名与权限声明、认证资源打包 | 已核对 |
| 无线 CarPlay 连接（车机自带热点） | 车主已在实车确认可连接 |
| 权限门槛探测 | 已在实际车机上取得诊断结果（门槛为 `WRITE_SETTINGS`，路径可行） |
| 自动开启热点的端到端效果 | **待授予权限后实车确认** |
| 同一 Wi-Fi／局域网、Wi-Fi Direct | 未在实机验证 |

已验证车机：`alps F9212A`（`ac8227l`，ARM，Android 9.1 / API 27，1024×600）。

**本版为预发布，不构成对任意车机的兼容性承诺。** 不同车机的固件、音频通路、热点实现差异很大，
请以自己车机上的实测结果为准。

### 许可与署名

沿用上游 **GPL-3.0**。原项目由 [shihabal3amri](https://github.com/shihabal3amri/DiPlay)
及各位贡献者开发，Android 8.1 / 通用架构适配由
[hiscatwang](https://github.com/hiscatwang/DiPlay) 维护。
随包实验性运行资源沿用上游公开安装包，不代表 Apple 官方认证。

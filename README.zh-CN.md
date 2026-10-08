# DiPlay 车机版 · 自动开启车机热点

[完整中文说明、下载及构建入口](README.md)。

基于 [hiscatwang/DiPlay](https://github.com/hiscatwang/DiPlay) `android81-universal` 分支（v15 通用版），
增加由 DiPlay 自动开启车机自带热点的能力。

- 支持 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`；最低 Android 8.1（API 27）。
- 包名 `com.shihab.diplay.wudi81v15`，versionCode 45，与上游发布的包名不同，可共存对比。
- 自动开启热点需要在系统设置里授予「修改系统设置」特殊访问权限；
  设置页会显示本机固件属于哪一类权限门槛，并提供跳转入口。
- 无线 CarPlay 已由车主在实车确认；自动开启热点的端到端效果待授权后确认。

详见[本版更新说明](docs/WUDI81_V15.md)。

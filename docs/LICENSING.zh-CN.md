# 非商业维护与素材许可说明

本文件说明 DiPlay 欧拉好猫哈曼车机适配分支的维护定位和许可边界，不替换各文件随附的许可证。

## 社区维护定位

本分支面向个人研究、车友交流和欧拉好猫哈曼车机兼容性维护，不提供商业销售、付费授权或商业技术支持。
“非商业维护”描述维护者的项目定位，不是附加在 GPL / AGPL 代码上的用途限制。
本分支不另设所谓“禁止商用的 GPL 协议”。

## 代码许可保留

接收器源自 xcertplay，并经 DiPlay 修改；根目录 [LICENSE](../LICENSE) 为 GPL-3.0。
DiAuto 衍生界面和网站等按原有 AGPL-3.0 标识及 [许可证文本](licenses/DiAuto-AGPL-3.0.txt) 处理。
第三方依赖继续遵守各自许可。

GPL 允许商业使用和收费分发，分发者仍需履行相应的许可证、源码与通知义务。
本分支不对上游代码、相应衍生代码添加与原许可冲突的商业用途限制。
参见 [GNU 许可 FAQ](https://www.gnu.org/licenses/gpl-faq.html#DoesTheGPLAllowMoney)
与 [GPL 关于附加限制的说明](https://www.gnu.org/licenses/gpl-faq.html#NoMilitary)。

## 非商业许可素材

`shared/src/main/assets/byd-hud-icons/` 中的 BYDMate 素材随附
[PolyForm Noncommercial License 1.0.0](../shared/src/main/assets/byd-hud-icons/LICENSE-BYDMate.txt)。

Required Notice: Copyright AndyShaman (https://github.com/AndyShaman/BYDMate)

该许可允许范围以其完整原文为准，包含非商业用途条件。其适用范围是对应素材，不会把整个 GPL / AGPL 代码库重新许可为“禁止商用”。
复制或分发相应素材必须保留 Required Notice 与许可文本或许可链接。
计划将这些素材用于商业用途时，不能假定代码许可证已提供授权；需取得相关权利人的适用许可，或使用许可合适的独立替代素材。
上游对素材来源的说明和限制仍保留在 [第三方通知](THIRD_PARTY_NOTICES.md)。

## 品牌和其他资源

欧拉、长城汽车、Harman、Apple、CarPlay 的名称或图标权利不因代码开源而转移。
欧拉图标来源及处理记录见 [ORA_BRANDING.md](ORA_BRANDING.md)。
使用这些标识不表示官方认证、合作或背书。认证身份、Android 签名密钥和本地运行材料也不作为项目源码重新授权。

## 本次公开范围

本次公开源码、测试和适配文档，保留原作者署名、GPL / AGPL 文本和素材已有许可。
Releases 另行提供 v11 和 v12 APK，其中实验性认证身份与上游公开 v0.2.10 安装包中的资源一致；
这些资源不作为项目代码重新授权，也不代表 Apple 官方认证。来源及分发限制见 [第三方说明](THIRD_PARTY_NOTICES.md)。
源码不包含认证私钥；Android 签名私钥、个人设备日志和热点配置不公开。
“欧拉好猫哈曼车机已适配”的具体平台和实车验证范围见 [项目首页](../README.md)。

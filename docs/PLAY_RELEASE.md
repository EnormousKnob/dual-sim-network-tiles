# Google Play 发布准备

本项目是可供测试及后续发布准备的实现，尚未完成真实设备验收或 Play 审核。Shizuku 的显式授权并不自动满足所有政策要求。

## 技术

- targetSdk 36；纯 Java/AIDL，无自有原生库。
- 调试 APK 仅供测试。正式发布须确定唯一包名、正式应用图标、版本号和开发者身份，生成生产签名 AAB，启用 Play App Signing。
- 当前工具链使用缓存中 AGP 8.7.3 / Gradle 8.9，对 SDK 36 使用兼容设置；生产前升级到正式支持 SDK 36 的 AGP / Gradle 组合并全量验证。
- 完成 DEVICE_TEST.md，尤其是 shell 模式 Shizuku、三星网络设置同步、权限撤销、重启、锁屏、通话中及外部切换。
- 上传内部测试轨道，跑预发布报告；按开发者账户要求完成测试流程。

## 审核披露

商店描述明确写出：必须安装并启动 Shizuku 并主动授权；非 Root 可使用无线调试；设备重启后一般需要重新启动；设备及运营商可能不支持；5G 自动表示允许 5G，并不承诺实际连接 5G。

向审核人员提供 Shizuku 启动、授权、添加磁贴、切换、撤销权限的完整操作录像及步骤。明确披露使用非 SDK 电话接口，不能声称普通权限即可切换，也不能承诺所有 Android 设备兼容。

无障碍服务、设备管理员、VPN、短信/通话记录及定位权限均未使用。数据安全表根据最终二进制与依赖实际行为填写；当前设计不收集或共享数据。不要在尚未审计依赖和完成测试时作最终合规声明。

发布最终隐私网页，填写支持邮箱、内容分级、目标受众、广告声明，准备 512px 商店图标、1024×500 宣传图及真实设备截图。不得用模拟成功状态制作截图。

## 商店文案草稿

应用名：双卡网络磁贴

简短说明：通过 Shizuku 在快捷面板切换默认数据卡和 5G 允许模式。

完整说明：为双卡手机添加两个快捷设置磁贴，显示默认数据 SIM 和用户网络模式。点击可切换默认数据卡，或在允许 5G 与禁用 5G 之间切换。禁用 5G 后保留原有 4G 和低代网络回退。需要安装并启动 Shizuku 并授予权限；系统、运营商和设备管理策略可能限制功能。网络模式显示不是实时驻留制式。无广告，无账号，本应用不上传 SIM 或网络信息。

## 官方依据

- [目标 API 要求](https://support.google.com/googleplay/android-developer/answer/11926878)
- [设备和网络滥用政策](https://support.google.com/googleplay/android-developer/answer/16559646)
- [非 SDK 接口兼容性限制](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)
- [Shizuku API](https://github.com/RikkaApps/Shizuku-API)

政策可能变化，应在提交前再次核查；不能保证审核通过。

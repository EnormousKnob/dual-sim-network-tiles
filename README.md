# 双卡网络磁贴

<img src="app/src/main/res/drawable-nodpi/launcher_artwork.png" alt="双卡网络磁贴图标" width="112" />

通过 **Shizuku**，将双卡数据切换和 4G / 5G 网络模式切换放进 Android 下拉快捷面板。

> **兼容性说明：目前仅在三星 Galaxy S24 Ultra、One UI 8.5 系统上测试通过。其他三星型号、其他 One UI 版本及其他品牌手机均未验证，不保证兼容。**

## 功能

- **切换数据卡**：点击磁贴，在两张已启用的普通 SIM 卡之间切换默认上网卡；图标显示 **SIM 1 / SIM 2**，副标题显示卡名称。
- **切换 4G / 5G**：操作当前默认数据卡，图标随设置显示 **4G / 5G**，模式分别为“4G 优先”和“5G 自动”。
- **状态同步**：切换后读取系统设置确认，并同步更新两个磁贴；应用内也能查看默认数据卡、活动数据卡和网络模式。
- **本地运行**：无广告、无账号、无分析 SDK，不请求互联网权限，不上传 SIM 或网络信息。

**磁贴颜色说明：**数据卡磁贴的 SIM 1 为灰底，SIM 2 为亮底，仅用于区分选中的卡，灰底不代表关闭移动数据。这一显示方式解决了测试机上换卡后数字图标未即时刷新的问题。

**网络模式说明：**“5G 自动”表示允许使用 5G，不代表已经连接 5G；“4G 优先”表示禁用 5G，并保留原有 LTE 和低代网络回退，不强制 LTE-only。实际连接取决于信号、运营商、套餐及系统策略。

## 下载与安装

从 [Releases](https://github.com/EnormousKnob/dual-sim-network-tiles/releases) 下载 APK。当前版本为 **0.1.4**，发布的 APK 是调试签名测试包，尚未在 Google Play 发布。

1. 安装并启动官方 [Shizuku](https://shizuku.rikka.app/zh-hans/guide/setup/)，非 Root 手机可通过无线调试启动。
2. 安装 APK，打开“双卡网络磁贴”，授予 Shizuku 权限。
3. 分别点击添加“数据卡”和“4G / 5G”磁贴，在系统对话框中确认。也可在下拉快捷面板的编辑页面手动添加。
4. 展开面板查看状态，点击磁贴切换。长按磁贴可返回应用；锁屏操作需要先解锁。

重启手机后，通常需要重新启动 Shizuku。应用需要两张已启用的普通 SIM 才能切换默认数据卡。

## 测试范围

| 项目 | 结果 |
|---|---|
| 三星 Galaxy S24 Ultra / One UI 8.5 | 用户实测通过 |
| 默认数据卡切换 | 已确认可用 |
| 4G / 5G 模式切换及图标更新 | 已确认可用 |
| 换卡磁贴即时刷新 | 0.1.4 已确认可用 |
| 其他手机和系统 | 未验证 |

以上为功能实测记录，不代表所有网络环境、通话状态、SIM/eSIM 组合、运营商和企业管理策略都经过测试。详见 [真机测试记录](docs/DEVICE_TEST.md)。

## 从源码构建

环境：**JDK 17、Android SDK 36、Build Tools 35.0.0、Gradle 8.9**。项目最低 API 31（Android 12），targetSdk 36；最低 API 配置不代表其他设备已通过测试。

用 Android Studio 打开项目并配置 SDK，或在本地 `local.properties` 中设置 `sdk.dir`。首次构建需要访问 Google Maven 和 Maven Central。

```powershell
# Windows 中文路径推荐：在临时英文路径构建，输出到 artifacts/
.\build.ps1

# 可指定本机已安装的 Gradle
.\build.ps1 -GradleExecutable 'C:\path\to\gradle.bat'

# 英文路径下可直接使用 Gradle Wrapper
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

`build.ps1 -Release` 额外生成未配置生产签名的 release AAB；正式发布前需自行配置签名。请勿将密钥或本机配置提交到仓库。

当前 AGP 为 8.7.3，对 compileSdk 36 使用兼容警告抑制配置。生产发布前应升级到正式支持 API 36 的工具链并重新验证。

## 实现与限制

- 使用 Shizuku UserService，以用户授权的 shell/root 身份调用设备自身的电话服务；不硬编码 Binder 事务号。
- 三参数网络模式写入接口不存在时，使用固定参数的系统 `cmd phone` 兼容路径，切换后仍读回状态确认。
- 电话服务使用非 SDK 接口，固件升级或厂商差异可能导致功能失效。权限或系统策略拒绝操作时会报告错误。
- 系统自动数据切换可能临时使用另一张卡，活动数据卡不一定等于默认数据卡。网络磁贴始终操作默认数据卡。
- 系统配置读回成功不等于无线链路已经恢复，请以系统设置和实际联网情况为准。

## 致谢

本项目使用 [Shizuku API](https://github.com/RikkaApps/Shizuku-API)。应用图标采用项目中选定的双卡切换设计，快捷磁贴使用原生单色矢量图标。

## 支持作者

如果这款小工具让你的日常切卡、切换网络更顺手，欢迎通过下方二维码请作者喝杯咖啡，为后续维护和改进添一份动力。

资助完全自愿，不影响应用的正常使用。愿意尝试、反馈问题或分享给有需要的人，同样是对这个项目的支持。感谢你的心意。

<table>
  <tr><th>微信支付</th><th>支付宝</th></tr>
  <tr>
    <td align="center"><a href="docs/support/wechat.jpg"><img src="docs/support/wechat.jpg" alt="微信支付收款码" width="280" /></a></td>
    <td align="center"><a href="docs/support/alipay.jpg"><img src="docs/support/alipay.jpg" alt="支付宝收款码" width="280" /></a></td>
  </tr>
</table>

点击图片可查看原图。

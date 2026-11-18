# 0.2.0 UI 渲染预览

这里的四张图片使用本机无界面 Chrome 渲染 HTML/CSS，布局、文案和配色参考 `MainActivity.java`。**它们不是 Android 渲染器输出，不是实际设备截图，也不是功能测试结果。** 每张图片均带有预览标记，连接、卡信息和诊断数据为示例；弱网开关显示默认关闭。

| 文件 | 内容 |
|---|---|
| ui-home-dark.png | 连接、双卡状态和网络模式首页（深色） |
| ui-preferences-light.png | 目标SIM、每卡偏好和快捷方式（浅色） |
| ui-weak-signal-dark.png | 滚动至弱信号监控设置与外观选项（深色） |
| ui-diagnostics-light.png | 本地诊断与示例报告（浅色） |

输出尺寸均为828×2320像素。字体和原生控件的真实外观应以安装包运行结果为准。后续可用已脱敏的实际设备截图替换这些预览。

## 重新渲染

仅需Python标准库和本机Chrome，不依赖在线资源，不调用AI图片生成接口。

```powershell
python tools/render_ui_previews.py --chrome 'C:/Program Files/Google/Chrome/Application/chrome.exe'
```

渲染脚本在临时目录生成页面，使用项目中的应用图标，输出PNG到本目录；不更改Android应用或APK。

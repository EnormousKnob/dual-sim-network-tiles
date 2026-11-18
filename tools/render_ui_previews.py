"""Render source-aligned UI illustrations, NOT Android/device screenshots.

Uses local headless Chromium; no network access, AI image generation, or image editing.
Run: python tools/render_ui_previews.py --chrome "C:/Program Files/Google/Chrome/Application/chrome.exe"
"""
import argparse
import base64
import html
import pathlib
import struct
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "images"


def text(value, css="body"):
    return f'<div class="{css}">{html.escape(value)}</div>'


def button(value):
    return text(value, "button")


def card(title, subtitle, body):
    return '<section>' + text(title, "title") + text(subtitle, "subtitle") + body + '</section>'


def select(label, value):
    return text(label, "label") + f'<div class="select">{html.escape(value)}<span>▾</span></div>'


def switch(label, description):
    return '<div class="switch-row">' + text(label, "switch-title") + '<i class="switch"></i></div>' + text(description, "subtitle")


def home():
    return (
        card("连接状态", "需要 Shizuku 启动并授权，才能修改网络设置",
             text("● 已连接 · Shizuku 已授权", "accent") + button("授权 / 重新连接 Shizuku"))
        + card("双卡状态", "默认数据卡与系统临时使用的活动卡可能不同",
               text("默认上网：SIM 1 · 示例卡 A\n活动数据：SIM 1 · 示例卡 A\n\n示例卡 A\n5G 自动 · 设备报告支持 5G\n\n示例卡 B\n4G 优先 · 设备报告支持 5G")
               + button("切换默认数据卡") + button("添加「数据卡」快捷磁贴"))
        + card("网络模式", "显示允许使用的网络模式，不等同于实时连接制式",
               text("跟随默认数据卡\nSIM 1 · 示例卡 A\n5G 自动 · 允许 5G")
               + button("切换 4G / 5G") + button("添加「4G / 5G」快捷磁贴"))
    )


def preferences():
    return (
        card("网络磁贴操作对象", "固定卡模式只修改该卡的网络设置，不会将它设为默认数据卡",
             select("目标 SIM", "跟随默认数据卡"))
        + card("每卡网络偏好", "仅在通过本应用切换数据卡后应用预设；不会持续覆盖系统设置",
               switch("切卡后应用新数据卡预设", "保持当前模式时不修改该卡的网络设置。")
               + select("SIM 1 作为上网卡时", "保持系统当前模式")
               + select("SIM 2 作为上网卡时", "保持系统当前模式")
               + switch("切卡后将非上网卡设为 4G 优先", "可选。保留 LTE 和原有低代网络回退，不使用 LTE-only。"))
        + card("桌面快捷方式与三星自动化", "快捷操作指定明确目标，重复执行不会来回切换。也可长按桌面应用图标查看快捷方式",
               button("添加：切到 SIM 1") + button("添加：切到 SIM 2") + button("添加：4G 优先") + button("添加：5G 自动"))
    )


def weak_signal():
    return (
        text("偏好设置 · 滚动至弱网监控区域", "scroll-note")
        + card("弱 5G 信号自动降级", "仅在移动数据使用中、非 Wi-Fi、通话状态已知且空闲时处理。读不到有效 NR 信号就不降级",
               switch("启用实验性弱网监控", "连续弱信号 → 4G 优先 → 冷却后试探恢复原设置。开启期间显示常驻通知；系统回收后不会强制自启。")
               + select("NR RSRP 不高于此值视为弱信号", "−110 dBm（默认）")
               + select("连续采样次数", "3 次")
               + select("冷却后试探恢复", "10 分钟（默认）")
               + text("亮屏约30秒、息屏约90秒采样，受系统调度影响。禁用5G后无法持续测量5G，因此采用定时试探恢复，不宣称已检测到5G信号恢复。不能判断“满格但速度差”。", "subtitle"))
        + card("外观", "", select("主题", "深色"))
    )


def diagnostics():
    report = (
        "设备与状态均为示例\n"
        "Android / API：示例\n"
        "Shizuku：已连接并授权\n"
        "默认订阅：示例 / SIM 1\n"
        "\n最近操作（示例）\n"
        "action=4 slot=1 origin=user\n"
        "成功 · Binder 写入\n"
        "网络模式读回确认\n"
        "\n不会自动上传诊断信息\n"
        "不展示真实设备标识或令牌"
    )
    return card(
        "本地诊断", "可复制后提交 Issue；不包含卡名称、手机号、ICCID 或授权令牌，不会自动上传",
        button("复制诊断报告") + button("读取一次信号（不修改网络）") + button("分享诊断报告")
        + button("清空操作日志") + text(report, "report") + button("Shizuku 启动说明")
    )


def document(body, dark, tab):
    logo = base64.b64encode((ROOT / "app/src/main/res/drawable-nodpi/launcher_artwork.png").read_bytes()).decode()
    tabs = "".join(f'<div class="tab {"chosen" if i == tab else ""}">{name}</div>'
                   for i, name in enumerate(["首页", "偏好设置", "诊断与帮助"]))
    return f'''<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><title>0.2.0 UI preview</title>
<style>
*{{box-sizing:border-box}}html,body{{margin:0;width:414px;height:1160px;overflow:hidden}}
body{{font-family:Roboto,"Microsoft YaHei UI","Microsoft YaHei",sans-serif;
--bg:{'#0C1425' if dark else '#F0F4FA'};--card:{'#19253B' if dark else '#FFFFFF'};
--fg:{'#F3F7FF' if dark else '#17243D'};--muted:{'#B0BED5' if dark else '#52647D'};
--accent:{'#80E4E8' if dark else '#006B78'};--button:{'#30363f' if dark else '#e0e0e0'};
background:var(--bg);color:var(--fg);font-size:16px}}
.preview-label{{height:28px;display:flex;align-items:center;justify-content:center;font-size:10px;
letter-spacing:.4px;color:var(--muted);border-bottom:1px solid #8899aa25}}
header{{display:flex;align-items:center;padding:16px 20px 10px;gap:14px;min-height:90px}}
header img{{width:54px;height:54px;border-radius:14px}}h1{{font-size:24px;font-weight:400;margin:0 0 7px;line-height:1.3}}
header p{{font-size:12px;color:var(--muted);margin:0;line-height:1.6}}
nav{{display:flex;padding:0 16px;gap:4px;height:52px}}.tab{{flex:1;display:flex;align-items:center;justify-content:center;
border-radius:3px;font-size:14px;background:var(--button);color:var(--fg);box-shadow:0 2px 3px #0002}}
.chosen{{box-shadow:inset 0 -2px 0 var(--accent),0 2px 3px #0002}}
main{{padding:8px 16px 24px;height:948px;overflow:hidden}}
section{{border-radius:22px;padding:14px 18px;margin:6px 0 16px;background:var(--card)}}
.title{{font-size:20px;line-height:1.5;padding:5px 0;font-weight:400}}
.subtitle{{font-size:13px;color:var(--muted);line-height:1.55;padding:5px 0;white-space:pre-wrap}}
.body{{font-size:16px;line-height:1.55;white-space:pre-wrap;padding:5px 0}}
.accent{{font-size:16px;color:var(--accent);line-height:1.6;padding:5px 0}}
.button{{min-height:48px;margin:5px 0;display:flex;align-items:center;justify-content:center;text-align:center;
font-size:14px;background:var(--button);color:var(--fg);border-radius:3px;padding:8px 10px;box-shadow:0 2px 3px #0002}}
.label{{padding:5px 0;font-size:14px;line-height:1.5}}.select{{height:48px;display:flex;align-items:center;
justify-content:space-between;font-size:16px;border-bottom:1px solid #8899aa77;padding:0 4px}}
.select span{{color:var(--muted);font-size:18px}}.switch-row{{display:flex;align-items:center;gap:12px;padding:12px 0 6px}}
.switch-title{{font-size:16px;line-height:1.5;flex:1}}.switch{{display:block;width:34px;height:14px;background:#8590a377;
border-radius:8px;flex-shrink:0;position:relative}}.switch:before{{content:"";position:absolute;left:-2px;top:-3px;
width:20px;height:20px;border-radius:50%;background:{'#c6cbd2' if dark else '#f4f4f4'};box-shadow:0 1px 3px #0005}}
.report{{font:12px/1.7 Consolas,monospace;white-space:pre-wrap;color:var(--muted);padding:5px 0}}
.scroll-note{{font-size:11px;color:var(--muted);padding:7px 4px}}
footer{{position:absolute;bottom:0;left:0;width:414px;height:42px;background:var(--bg);color:var(--muted);
display:flex;align-items:center;justify-content:center;font-size:11px;border-top:1px solid #8899aa33}}
</style><body><div class="preview-label">0.2.0 · 布局渲染预览 · 非 Android 真机截图</div>
<header><img src="data:image/png;base64,{logo}" alt="App icon"><div><h1>双卡网络磁贴</h1><p>更顺手地掌控每一张卡 · 0.2.0</p></div></header>
<nav>{tabs}</nav><main>{body}</main><footer>示例数据 · 弱网功能默认关闭 · 实际显示以设备为准</footer></body></html>'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--chrome", required=True)
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    items = [
        ("ui-home-dark.png", home(), True, 0),
        ("ui-preferences-light.png", preferences(), False, 1),
        ("ui-weak-signal-dark.png", weak_signal(), True, 1),
        ("ui-diagnostics-light.png", diagnostics(), False, 2),
    ]
    with tempfile.TemporaryDirectory(prefix="NetworkTilesUIPreview-") as directory:
        folder = pathlib.Path(directory)
        for name, body, dark, tab in items:
            page = folder / (name + ".html")
            page.write_text(document(body, dark, tab), encoding="utf-8")
            output = OUTPUT / name
            command = [args.chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                       "--no-first-run", "--no-default-browser-check", "--disable-background-networking",
                       "--force-device-scale-factor=2", "--window-size=414,1160", "--virtual-time-budget=1000",
                       "--user-data-dir=" + str(folder / "profile"), "--screenshot=" + str(output), page.as_uri()]
            result = subprocess.run(command, capture_output=True, timeout=60,
                                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            if result.returncode or not output.exists():
                raise RuntimeError(result.stderr.decode(errors="replace")[-1500:])
            width, height = struct.unpack(">II", output.read_bytes()[16:24])
            if width < 800 or height < 2200:
                raise RuntimeError(f"Unexpected output dimensions: {width}x{height}")
            print(f"{name}: {width}x{height}, {output.stat().st_size} bytes")


if __name__ == "__main__":
    main()

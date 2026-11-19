"""给界面截图套上设备外壳（手机 / Windows 窗口），输出到 docs/img/。

跑法：python docs/tools/frame_ui.py

安卓：深色机身 + 圆角屏幕 + 灵动岛 + 侧键 + 柔和投影，透明背景。
Windows：窗口卡片（标题栏 + 三个系统按钮 + 圆角 + 投影）。
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
IMG = ROOT / "docs" / "img"

BODY = (22, 24, 29, 255)          # 机身深灰
BODY_EDGE = (64, 70, 80, 255)     # 机身外圈高光
PAD = 200                         # 画布留白，给投影与侧键用


def rounded(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=255)
    return mask


def screen_mask(size: tuple[int, int], radius: int) -> Image.Image:
    return rounded(size, radius)


def drop_shadow(size: tuple[int, int], radius: int, blur: int, alpha: int) -> Image.Image:
    sh = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=(0, 0, 0, alpha))
    return sh.filter(ImageFilter.GaussianBlur(blur))


def frame_phone(shot_path: Path, out_path: Path, *, island: bool = False) -> None:
    shot = Image.open(shot_path).convert("RGBA")
    sw, sh = shot.size

    rim = 34                                   # 机身边宽
    key_w = 14                                 # 侧键伸出的宽度
    bw, bh = sw + rim * 2, sh + rim * 2
    scr_r = 78                                 # 屏幕圆角
    body_r = scr_r + rim

    canvas_w, canvas_h = bw + PAD * 2 + key_w * 2, bh + PAD * 2 + 40
    canvas = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))

    x0 = PAD + key_w
    y0 = PAD + 20

    # 投影：偏移到机身下方，模糊
    shadow = drop_shadow((bw, bh), body_r, 58, 58)
    canvas.alpha_composite(shadow, (x0 - 8, y0 + 46))

    # 机身（画布左右各留 key_w，侧键不会被裁掉）
    body = Image.new("RGBA", (bw + key_w * 2, bh), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    d.rounded_rectangle([key_w, 0, bw + key_w - 1, bh - 1], body_r, fill=BODY)
    d.rounded_rectangle([key_w, 0, bw + key_w - 1, bh - 1], body_r, outline=BODY_EDGE, width=3)
    # 外侧一圈更亮的高光，模拟金属倒角
    d.rounded_rectangle([key_w + 5, 5, bw + key_w - 6, bh - 6], body_r - 5, outline=(104, 112, 124, 130), width=2)

    # 侧键
    def key(rect, r=6):
        overlay = Image.new("RGBA", body.size, (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        od.rounded_rectangle(rect, r, fill=(96, 104, 116, 255))
        body.alpha_composite(overlay)

    key([key_w + bw - 4, int(bh * 0.19), key_w + bw + key_w, int(bh * 0.30)], 6)   # 电源键
    key([0, int(bh * 0.23), key_w, int(bh * 0.29)], 6)                            # 音量 +
    key([0, int(bh * 0.31), key_w, int(bh * 0.37)], 6)                            # 音量 -

    canvas.alpha_composite(body, (x0 - key_w, y0))

    # 屏幕
    scr = shot.copy()
    scr.putalpha(screen_mask(scr.size, scr_r))
    canvas.alpha_composite(scr, (x0 + rim, y0 + rim))

    # 灵动岛（默认不加：截图本身不是挖孔屏，硬加会压住状态栏图标）
    if island:
        iw, ih = int(sw * 0.24), int(sw * 0.062)
        pill = Image.new("RGBA", (iw, ih), (0, 0, 0, 0))
        pd = ImageDraw.Draw(pill)
        pd.rounded_rectangle([0, 0, iw - 1, ih - 1], ih // 2, fill=(6, 7, 9, 255))
        pd.rounded_rectangle([0, 0, iw - 1, ih - 1], ih // 2, outline=(46, 50, 58, 160), width=2)
        canvas.alpha_composite(pill, (x0 + rim + (sw - iw) // 2, y0 + rim + int(sw * 0.028)))

    canvas.save(out_path)
    print(f"{out_path.name}  {canvas.size}")


def frame_window(shot_path: Path, out_path: Path) -> None:
    shot = Image.open(shot_path).convert("RGBA")
    sw, sh = shot.size
    bar = 62                                    # 标题栏高度
    w, h = sw + 2, sh + bar
    r = 26

    cw, ch = w + PAD * 2, h + PAD * 2 + 20
    canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    canvas.alpha_composite(drop_shadow((w, h), r, 40, 78), (PAD, PAD + 20))

    card = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    d.rounded_rectangle([0, 0, w - 1, h - 1], r, fill=(250, 250, 252, 255))
    canvas.alpha_composite(card, (PAD, PAD + 20))

    # 标题栏：浅灰底 + 底部分隔线
    title = Image.new("RGBA", (w, bar), (0, 0, 0, 0))
    td = ImageDraw.Draw(title)
    td.rounded_rectangle([0, 0, w - 1, bar + r], r, fill=(236, 238, 242, 255))
    td.rectangle([0, bar - 1, w - 1, bar], fill=(214, 218, 224, 255))
    canvas.alpha_composite(title, (PAD, PAD + 20))

    # 窗口控件（Windows 风格：减号 / 方框 / 叉）
    for i, kind in enumerate(("min", "max", "close")):
        x = w - 56 - i * 46
        y = bar // 2
        dd = ImageDraw.Draw(canvas)
        col = (120, 126, 136, 255) if kind != "close" else (196, 92, 92, 255)
        if kind == "min":
            dd.rectangle([PAD + x - 10, PAD + 20 + y - 1, PAD + x + 10, PAD + 20 + y + 1], fill=col)
        elif kind == "max":
            dd.rectangle([PAD + x - 9, PAD + 20 + y - 9, PAD + x + 9, PAD + 20 + y + 9], outline=col, width=2)
        else:
            dd.line([PAD + x - 9, PAD + 20 + y - 9, PAD + x + 9, PAD + 20 + y + 9], fill=col, width=2)
            dd.line([PAD + x - 9, PAD + 20 + y + 9, PAD + x + 9, PAD + 20 + y - 9], fill=col, width=2)

    # 内容区：截图裁掉顶部自带的空白边也不影响，直接贴
    content = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    content.paste(shot, (0, 0))
    bottom_mask = Image.new("L", (sw, sh), 255)
    ImageDraw.Draw(bottom_mask).rectangle([0, sh - r, sw, sh], fill=255)
    mm = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mm).rounded_rectangle([0, 0, sw - 1, sh - 1], 0, fill=255)
    bottom = rounded((sw, r + 2), r)
    mm.paste(bottom, (0, sh - r - 2))
    content.putalpha(mm)
    canvas.alpha_composite(content, (PAD + 1, PAD + 20 + bar))

    canvas.save(out_path)
    print(f"{out_path.name}  {canvas.size}")


def main() -> int:
    jobs_phone = [
        ("ui-android-main.png", "ui-android-main-device.png", False),
        ("ui-android-settings.png", "ui-android-settings-device.png", False),
        ("ui-android-about.png", "ui-android-about-device.png", False),
    ]
    for src, dst, island in jobs_phone:
        p = IMG / src
        if not p.exists():
            print(f"跳过（缺文件）{src}")
            continue
        frame_phone(p, IMG / dst, island=island)

    # Windows 关于窗直接用系统原生窗口边框的真实截图，不再套壳
    return 0


if __name__ == "__main__":
    sys.exit(main())


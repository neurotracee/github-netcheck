"""给 Windows 端界面截图套窗口外壳（标题栏 + 圆角 + 投影），输出到 docs/img/。

跑法：python docs/tools/frame_window.py
输入：docs/img/raw-windows-about.png（真机截的客户区，不含系统边框）
输出：docs/img/ui-windows-about-device.png
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
IMG = ROOT / "docs" / "img"

TITLE = "GitHub 网络自检"
VERSION = "v1.1.5.2"

BAR = 58                 # 标题栏高度
RADIUS = 16
PAD = 90                 # 画布留白（给投影）
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\segoeui.ttf",
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for p in FONT_CANDIDATES:
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()


def shadow(size: tuple[int, int], radius: int, blur: int, alpha: int) -> Image.Image:
    sh = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(
        [0, 0, size[0] - 1, size[1] - 1], radius, fill=(15, 20, 28, alpha)
    )
    return sh.filter(ImageFilter.GaussianBlur(blur))


def trim_bottom_border(im: Image.Image) -> Image.Image:
    """截的是窗口客户区，底部常会带一条窗口边框的灰条，逐行检测后裁掉。"""
    w, h = im.size
    px = im.convert("RGB").load()
    bottom = h - 1
    while bottom > h // 2:
        row = [px[x, bottom] for x in range(0, w, max(1, w // 40))]
        grayish = all(abs(c[0] - c[1]) < 6 and abs(c[1] - c[2]) < 6 for c in row)
        uniform = (max(c[0] for c in row) - min(c[0] for c in row)) < 8
        if grayish and uniform and 150 <= row[0][0] <= 250:
            bottom -= 1
        else:
            break
    if bottom < h - 1:
        return im.crop((0, 0, w, bottom + 1))
    return im


def frame(shot_path: Path, out_path: Path) -> None:
    shot = Image.open(shot_path).convert("RGBA")
    # 客户区截图底部会带一条窗口外框的深色边，固定裁掉
    shot = shot.crop((0, 0, shot.size[0], shot.size[1] - 16))
    shot = trim_bottom_border(shot)
    sw, sh = shot.size

    w, h = sw + 2, sh + BAR
    cw, ch = w + PAD * 2, h + PAD * 2
    canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))

    # 投影
    canvas.alpha_composite(shadow((w, h), RADIUS, 46, 74), (PAD - 4, PAD + 26))
    canvas.alpha_composite(shadow((w, h), RADIUS, 16, 46), (PAD, PAD + 6))

    # 窗口卡片：白底 + 圆角
    card = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle([0, 0, w - 1, h - 1], RADIUS, fill=(255, 255, 255, 255))
    canvas.alpha_composite(card, (PAD, PAD))

    # 标题栏（浅灰）
    bar = Image.new("RGBA", (w, BAR), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bar)
    bd.rounded_rectangle([0, 0, w - 1, BAR + RADIUS], RADIUS, fill=(243, 245, 248, 255))
    bd.rectangle([0, BAR - 1, w - 1, BAR], fill=(226, 230, 236, 255))
    canvas.alpha_composite(bar, (PAD, PAD))

    # 标题文字
    f_title = load_font(19)
    f_ver = load_font(15)
    d = ImageDraw.Draw(canvas)
    d.text((PAD + 20, PAD + 17), TITLE, font=f_title, fill=(28, 32, 38, 255))
    tw = d.textlength(TITLE, font=f_title)
    d.text((PAD + 20 + tw + 10, PAD + 21), VERSION, font=f_ver, fill=(120, 128, 140, 255))

    # 右侧窗口三键（减号 / 方框 / 叉）
    bx = PAD + w - 22
    by = PAD + BAR // 2
    d.line([bx - 34 - 11, by, bx - 34 + 11, by], fill=(110, 116, 126, 255), width=2)
    d.rectangle([bx - 11 - 9, by - 9, bx - 11 + 9, by + 9], outline=(110, 116, 126, 255), width=2)
    d.line([bx + 11 - 10, by - 10, bx + 11 + 10, by + 10], fill=(196, 92, 92, 255), width=2)
    d.line([bx + 11 - 10, by + 10, bx + 11 + 10, by - 10], fill=(196, 92, 92, 255), width=2)

    # 截图内容：贴到标题栏下方，并让下方两角跟窗口圆角一致
    content = shot.copy()
    mask = Image.new("L", (sw, sh), 255)
    rounded = Image.new("L", (sw, RADIUS + 2), 0)
    ImageDraw.Draw(rounded).rounded_rectangle(
        [0, 0, sw - 1, RADIUS * 2], RADIUS, fill=255
    )
    mask.paste(rounded, (0, sh - RADIUS - 2))
    content.putalpha(mask)
    canvas.alpha_composite(content, (PAD + 1, PAD + BAR))

    # 一圈极淡描边，避免白底窗口在白色 README 上糊边
    d.rounded_rectangle([PAD, PAD, PAD + w - 1, PAD + h - 1], RADIUS, outline=(222, 226, 232, 255), width=1)

    canvas.save(out_path)
    print(f"{out_path.name}  {canvas.size}")


def frame_terminal(shot_path: Path, out_path: Path, title: str = "GitHub 网络自检 · 控制台",
                   head_crop: int = 62, right_crop: int = 32, bottom_crop: int = 20, left_crop: int = 0) -> None:
    """给终端截图套壳：裁掉终端自带的标签栏与滚动条，换成深色标题栏。"""
    shot = Image.open(shot_path).convert("RGBA")
    w, h = shot.size
    shot = shot.crop((left_crop, head_crop, w - right_crop, h - bottom_crop))
    sw, sh = shot.size

    BAR = 56
    RADIUS = 14
    ww, hh = sw + 2, sh + BAR
    cw, ch = ww + PAD * 2, hh + PAD * 2
    canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))

    canvas.alpha_composite(shadow((ww, hh), RADIUS, 46, 80), (PAD - 4, PAD + 26))
    canvas.alpha_composite(shadow((ww, hh), RADIUS, 16, 50), (PAD, PAD + 6))

    card = Image.new("RGBA", (ww, hh), (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle([0, 0, ww - 1, hh - 1], RADIUS, fill=(24, 26, 30, 255))
    canvas.alpha_composite(card, (PAD, PAD))

    bar = Image.new("RGBA", (ww, BAR), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bar)
    bd.rounded_rectangle([0, 0, ww - 1, BAR + RADIUS], RADIUS, fill=(42, 45, 51, 255))
    bd.rectangle([0, BAR - 1, ww - 1, BAR], fill=(60, 64, 72, 255))
    canvas.alpha_composite(bar, (PAD, PAD))

    f_title = load_font(18)
    d = ImageDraw.Draw(canvas)
    d.text((PAD + 20, PAD + 16), title, font=f_title, fill=(226, 230, 236, 255))

    # 右侧三键（深色版配色）
    bx = PAD + ww - 22
    by = PAD + BAR // 2
    d.line([bx - 34 - 11, by, bx - 34 + 11, by], fill=(150, 156, 166, 255), width=2)
    d.rectangle([bx - 11 - 9, by - 9, bx - 11 + 9, by + 9], outline=(150, 156, 166, 255), width=2)
    d.line([bx + 11 - 10, by - 10, bx + 11 + 10, by + 10], fill=(214, 108, 108, 255), width=2)
    d.line([bx + 11 - 10, by + 10, bx + 11 + 10, by - 10], fill=(214, 108, 108, 255), width=2)

    # 终端内容：底部两角跟窗口圆角一致
    content = shot.copy()
    mask = Image.new("L", (sw, sh), 255)
    r2 = Image.new("L", (sw, RADIUS + 2), 0)
    ImageDraw.Draw(r2).rounded_rectangle([0, 0, sw - 1, RADIUS * 2], RADIUS, fill=255)
    mask.paste(r2, (0, sh - RADIUS - 2))
    content.putalpha(mask)
    canvas.alpha_composite(content, (PAD + 1, PAD + BAR))

    d.rounded_rectangle([PAD, PAD, PAD + ww - 1, PAD + hh - 1], RADIUS, outline=(70, 74, 82, 255), width=1)

    canvas.save(out_path)
    print(f"{out_path.name}  {canvas.size}")


def main() -> int:
    src = IMG / "raw-windows-about.png"
    if src.exists():
        frame(src, IMG / "ui-windows-about-device.png")
    else:
        print("缺 raw-windows-about.png")

    con = IMG / "raw-windows-console.png"
    if con.exists():
        frame_terminal(con, IMG / "ui-windows-console-device.png")
    else:
        print("缺 raw-windows-console.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())





#!/usr/bin/env python3
"""生成 BatteryPower 启动图标：深蓝渐变圆角方块 + 黄色闪电。

用法: python3 tools/make_icon.py
输出: app/src/main/res/mipmap-*/ic_launcher.png
"""
import os
from PIL import Image, ImageDraw

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

BG_TOP = (0x1E, 0x3A, 0x5F)
BG_BOTTOM = (0x0B, 0x14, 0x26)
BOLT = (0xFF, 0xD6, 0x00, 255)
SUPER = 4

# 闪电多边形（归一化坐标，0~1），取自 Material Design 的 bolt 图标轮廓
BOLT_POINTS = [
    (0.5417, 0.0833),   # 顶点
    (0.1704, 0.5404),   # 左下（上半笔画）
    (0.3958, 0.5404),   # 缺口左
    (0.4583, 0.9167),   # 底部尖端
    (0.8296, 0.4600),   # 右上（下半笔画）
    (0.6042, 0.4600),   # 缺口右
]


def make_base(size: int) -> Image.Image:
    s = size * SUPER
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    pad = int(s * 0.05)
    box_s = s - pad * 2
    radius = int(box_s * 0.22)

    gradient = Image.new("RGBA", (box_s, box_s))
    gd = ImageDraw.Draw(gradient)
    for y in range(box_s):
        t = y / max(box_s - 1, 1)
        color = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)) + (255,)
        gd.line([(0, y), (box_s, y)], fill=color)

    mask = Image.new("L", (box_s, box_s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, box_s - 1, box_s - 1], radius=radius, fill=255)
    img.paste(gradient, (pad, pad), mask)

    d = ImageDraw.Draw(img)
    pts = [(int(pad + x * box_s), int(pad + y * box_s)) for x, y in BOLT_POINTS]
    d.polygon(pts, fill=BOLT)

    return img.resize((size, size), Image.LANCZOS)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    base = make_base(512)
    for folder, size in SIZES.items():
        out_dir = os.path.join(root, "app", "src", "main", "res", folder)
        os.makedirs(out_dir, exist_ok=True)
        out = os.path.join(out_dir, "ic_launcher.png")
        base.resize((size, size), Image.LANCZOS).save(out, "PNG")
        print("生成", out, f"{size}x{size}")


if __name__ == "__main__":
    main()

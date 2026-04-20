#!/usr/bin/env python3
"""Generate Play Store graphic assets from scratch using PIL.

Outputs:
  playstore/app-icon-512.png   (required: 512x512 PNG app icon)
  playstore/feature-1024x500.png (required: feature graphic)
"""

from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CREAM = (244, 233, 212)
INK = (26, 26, 26)
ACCENT = (230, 106, 58)
MUTED = (90, 84, 76)


def draw_h_monogram(draw: ImageDraw.ImageDraw, cx: int, cy: int, size: int, ink=INK, accent=ACCENT):
    """Draw the 'H' + orange dot logo, centered at (cx, cy) at overall size px."""
    # viewport was 108x108; scale factor:
    s = size / 108.0

    def P(x, y):  # scaled point
        return (cx - size / 2 + x * s, cy - size / 2 + y * s)

    # H polygon from drawable/ic_logo_h.xml:
    # M38,32 L46,32 L46,50 L62,50 L62,32 L70,32 L70,76 L62,76 L62,58 L46,58 L46,76 L38,76 Z
    pts = [
        P(38, 32), P(46, 32), P(46, 50), P(62, 50),
        P(62, 32), P(70, 32), P(70, 76), P(62, 76),
        P(62, 58), P(46, 58), P(46, 76), P(38, 76),
    ]
    draw.polygon(pts, fill=ink)

    # Orange dot centered at (76, 32) with r=4
    dot_r = 4 * s
    draw.ellipse(
        (cx - size / 2 + 76 * s - dot_r, cy - size / 2 + 32 * s - dot_r,
         cx - size / 2 + 76 * s + dot_r, cy - size / 2 + 32 * s + dot_r),
        fill=accent,
    )


def gen_app_icon():
    size = 512
    img = Image.new("RGBA", (size, size), CREAM + (255,))
    draw = ImageDraw.Draw(img)
    draw_h_monogram(draw, size // 2, size // 2, int(size * 0.86))
    out = ROOT / "app-icon-512.png"
    img.save(out, optimize=True)
    print(f"wrote {out}")


def find_font(candidates, size):
    for f in candidates:
        try:
            return ImageFont.truetype(f, size)
        except Exception:
            continue
    return ImageFont.load_default()


def gen_feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), CREAM)
    draw = ImageDraw.Draw(img)

    # Logo on left third
    mono_size = 340
    draw_h_monogram(draw, 220, H // 2, mono_size)

    # Headline text on right
    bold_candidates = [
        "/System/Library/Fonts/Supplemental/Georgia Bold.ttf",
        "/System/Library/Fonts/Supplemental/Times New Roman Bold.ttf",
        "/System/Library/Fonts/Supplemental/Palatino.ttc",
        "/Library/Fonts/Georgia Bold.ttf",
    ]
    reg_candidates = [
        "/System/Library/Fonts/Supplemental/Georgia.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
    ]
    head_font = find_font(bold_candidates, 78)
    tag_font = find_font(reg_candidates, 28)

    lines = ["Hand your", "phone a", "task."]
    x = 430
    y = 110
    for line in lines:
        draw.text((x, y), line, font=head_font, fill=INK)
        y += 92

    draw.text((x, y + 20), "Voice or text. Handy does it.", font=tag_font, fill=MUTED)

    out = ROOT / "feature-1024x500.png"
    img.save(out, optimize=True)
    print(f"wrote {out}")


if __name__ == "__main__":
    gen_app_icon()
    gen_feature_graphic()

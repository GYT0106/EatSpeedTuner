"""Generates the Eat Speed Tuner mod icon.

Center piece is the vanilla Minecraft cooked_beef (steak) texture. It is scaled with
NEAREST so the pixel art stays crisp instead of being smeared by interpolation.

The texture is NOT stored in this repository — it is Mojang's copyrighted artwork.
This script extracts it from the user's own Minecraft installation at build time.
If no installation can be found, a neutral fallback steak is drawn instead so the
icon can still be generated.

Background (dial ring + plate) is rendered supersampled for smooth curves, then
the crisp pixel-art steak is composited on top at final resolution.

Output: 128x128 PNG in the mod's resource root.
"""

from PIL import Image, ImageDraw
import glob
import io
import math
import os
import zipfile

SIZE = 128
SS = 8
S = SIZE * SS

BG_TOP = (46, 38, 33)
BG_BOTTOM = (24, 20, 18)
RING_BG = (92, 72, 34)
DIAL = (255, 200, 70)
DIAL_HOT = (255, 231, 150)

PLATE = (247, 247, 250)
PLATE_RIM = (203, 205, 214)
PLATE_SHADOW = (176, 179, 191)

HERE = os.path.dirname(os.path.abspath(__file__))

# Where the vanilla texture may live inside a Minecraft jar.
STEAK_ENTRY = "assets/minecraft/textures/item/cooked_beef.png"


def find_game_jars():
    """Best-effort search for Minecraft jars that contain vanilla assets."""
    candidates = []

    env_root = os.environ.get("MC_DIR")
    roots = []
    if env_root:
        roots.append(env_root)

    # common launcher layouts
    for base in (
        os.path.expanduser("~/.minecraft"),
        r"D:\minecraft to go\.minecraft",
        r"C:\minecraft to go\.minecraft",
    ):
        if os.path.isdir(base):
            roots.append(base)

    for root in roots:
        # version jars
        candidates += glob.glob(os.path.join(root, "versions", "*", "*.jar"))
        # vanilla asset jars shipped as libraries
        candidates += glob.glob(
            os.path.join(root, "libraries", "net", "minecraft", "client", "*", "*-extra.jar")
        )
        candidates += glob.glob(
            os.path.join(root, "libraries", "net", "minecraft", "client", "*", "*-srg.jar")
        )
    return candidates


def extract_steak():
    """Return the vanilla steak texture, or None if it cannot be found."""
    for jar in find_game_jars():
        try:
            with zipfile.ZipFile(jar) as z:
                if STEAK_ENTRY in z.namelist():
                    data = z.read(STEAK_ENTRY)
                    return Image.open(io.BytesIO(data)).convert("RGBA")
        except (zipfile.BadZipFile, OSError):
            continue
    return None


def fallback_steak():
    """A neutral placeholder so the icon can still be built without the game."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1, 3, 14, 12], radius=3, fill=(176, 78, 44, 255))
    d.rounded_rectangle([2, 4, 13, 7], radius=2, fill=(214, 122, 74, 255))
    for y in (7, 9):
        d.line([4, y, 11, y], fill=(247, 226, 190, 255), width=1)
    return img


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", (1, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        d.point((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img.resize((size, size), Image.BICUBIC)


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def render_background():
    """Dial ring + plate, rendered at SS resolution then downscaled smoothly."""
    img = vertical_gradient(S, BG_TOP, BG_BOTTOM).convert("RGBA")
    d = ImageDraw.Draw(img)
    cx = cy = S / 2

    ring_r = S * 0.445
    ring_w = S * 0.052
    ring_bbox = [cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r]

    d.ellipse(ring_bbox, outline=RING_BG, width=int(ring_w))
    d.arc(ring_bbox, start=-135, end=45, fill=DIAL, width=int(ring_w))

    for i in range(12):
        deg = -180 + i * 30
        ang = math.radians(deg)
        r1 = ring_r - ring_w * 1.55
        r2 = ring_r - ring_w * 0.45
        x1, y1 = cx + r1 * math.cos(ang), cy + r1 * math.sin(ang)
        x2, y2 = cx + r2 * math.cos(ang), cy + r2 * math.sin(ang)
        lit = -135 <= deg <= 45
        d.line([x1, y1, x2, y2], fill=DIAL_HOT if lit else RING_BG, width=int(S * 0.019))

    pa = math.radians(-135)
    px, py = cx + ring_r * math.cos(pa), cy + ring_r * math.sin(pa)
    dot = S * 0.030
    d.ellipse([px - dot, py - dot, px + dot, py + dot], fill=DIAL_HOT)

    plate_r = S * 0.318
    off = S * 0.016
    d.ellipse([cx - plate_r, cy - plate_r + off, cx + plate_r, cy + plate_r + off],
              fill=PLATE_SHADOW)
    d.ellipse([cx - plate_r, cy - plate_r, cx + plate_r, cy + plate_r], fill=PLATE)
    d.ellipse([cx - plate_r, cy - plate_r, cx + plate_r, cy + plate_r],
              outline=PLATE_RIM, width=int(S * 0.014))

    return img.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    bg = render_background()

    steak = extract_steak()
    if steak is None:
        print("note: vanilla texture not found, using fallback artwork")
        steak = fallback_steak()

    # crop to the actual artwork so centring is based on visible pixels, not padding
    bbox = steak.getbbox()
    if bbox:
        steak = steak.crop(bbox)

    # integer upscale keeps every source pixel a perfect square
    plate_d = 2 * 0.318 * SIZE          # ~81 px
    target = int(plate_d * 0.80)        # leave a small margin inside the plate
    scale = max(1, target // max(steak.width, steak.height))
    steak_big = steak.resize(
        (steak.width * scale, steak.height * scale), Image.NEAREST
    )

    # drop shadow: use the steak's own alpha so no square block appears behind it
    alpha = steak_big.split()[3]
    shadow = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    sh = Image.new("RGBA", steak_big.size, (0, 0, 0, 0))
    sh.putalpha(alpha.point(lambda a: int(a * 0.45)))
    shadow.paste(sh, (
        (SIZE - steak_big.width) // 2 + 2,
        (SIZE - steak_big.height) // 2 + 3,
    ), sh)
    bg.alpha_composite(shadow)

    pos = ((SIZE - steak_big.width) // 2, (SIZE - steak_big.height) // 2)
    bg.alpha_composite(steak_big, pos)

    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.paste(bg, (0, 0), rounded_mask(SIZE, int(SIZE * 0.17)))

    target_path = os.path.normpath(
        os.path.join(HERE, "..", "src", "main", "resources", "eatspeedtuner_icon.png")
    )
    out.save(target_path, "PNG", optimize=True)
    print("wrote", target_path, out.size, "steak scale x%d" % scale)

    prev = out.resize((32, 32), Image.LANCZOS)
    prev_path = os.path.join(HERE, "icon-preview-32.png")
    prev.save(prev_path, "PNG")
    print("wrote", prev_path, prev.size)


if __name__ == "__main__":
    main()

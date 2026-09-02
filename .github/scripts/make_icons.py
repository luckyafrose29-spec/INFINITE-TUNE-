#!/usr/bin/env python3
"""
Generate all Android launcher icons for INFINITE TUNE from a single logo.png.

Run automatically by .github/workflows/build-infinite-tune.yml so that you only
ever have to upload ONE image from your phone instead of fifteen.

What it produces, matching Metrolist's existing resource layout exactly:

  mipmap-{mdpi..xxxhdpi}/ic_launcher.webp        legacy square  (replaces stock)
  mipmap-{mdpi..xxxhdpi}/ic_launcher_round.webp  legacy round   (replaces stock)
  mipmap-{mdpi..xxxhdpi}/it_logo_fg.png          adaptive foreground (new name)
  mipmap-anydpi-v26/ic_launcher{,_round}.xml     Android 8-11
  mipmap-anydpi-v31/ic_launcher{,_round}.xml     Android 12+   <- most phones
  values/it_launcher_background.xml              background colour

Deliberate choices:
  * .webp for the legacy icons because Metrolist ships .webp. Adding .png
    alongside would be a duplicate resource and break the build.
  * The adaptive foreground uses a NEW name (it_logo_fg) rather than
    overwriting @drawable/ic_launcher_foreground, so `Sync fork` stays clean.
  * ic_launcher_static* and ic_launcher_monochrome are left untouched; the
    monochrome layer is still referenced so themed icons keep working.
"""

import math
import os
import sys

from PIL import Image, ImageFilter

RES = "app/src/main/res"
LOGO = "logo.png"

# Background colour behind the glyph. Sampled to match the logo artwork.
BG_HEX = "#050C28"
BG_RGB = (5, 12, 40)

# The dark navy the logo sits on. Pixels close to this become transparent.
SRC_BG = (0, 22, 67)

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# Adaptive icons are cropped by the launcher (circle, squircle, ...). Only the
# centre ~66% is guaranteed visible, so the glyph must stay well inside.
ADAPTIVE_FRAC = 0.62
LEGACY_FRAC = 0.88


def die(msg):
    print(f"::error::{msg}")
    sys.exit(1)


def load_glyph():
    """Crop the logo to the artwork and key out the flat background."""
    if not os.path.isfile(LOGO):
        die(f"{LOGO} not found in the repository root.")

    im = Image.open(LOGO).convert("RGB")
    w, h = im.size

    # The supplied artwork is a wide banner: logo centred, wordmark underneath.
    # Take the upper-middle band, which holds the infinity mark on its own.
    # Expressed as ratios so a re-exported logo of any size still works.
    box = (
        int(w * 0.308),
        int(h * 0.152),
        int(w * 0.691),
        int(h * 0.547),
    )
    glyph = im.crop(box)
    gw, gh = glyph.size
    if gw < 8 or gh < 8:
        die("Cropped logo is too small - is logo.png the full banner image?")

    # Sample the actual background from the crop's corners instead of trusting
    # a hardcoded colour. Re-exporting or resizing logo.png shifts those values
    # slightly, and a stale constant leaves a visible rectangular halo.
    corner = 6
    samples = []
    for cx, cy in ((0, 0), (gw - corner, 0), (0, gh - corner), (gw - corner, gh - corner)):
        patch = glyph.crop((cx, cy, cx + corner, cy + corner))
        samples.extend(list(patch.convert("RGB").tobytes()[i:i+3]
                            for i in range(0, corner * corner * 3, 3)))
    bg = tuple(sum(c[i] for c in samples) // len(samples) for i in range(3))
    print(f"  background keyed at rgb{bg}")

    # Distance-from-background keying. Cheap, and it preserves the neon bloom
    # far better than a hard threshold would.
    px = glyph.load()
    mask = Image.new("L", (gw, gh), 0)
    mp = mask.load()
    for y in range(gh):
        for x in range(gw):
            r, g, b = px[x, y]
            d = math.sqrt(
                (r - bg[0]) ** 2 + (g - bg[1]) ** 2 + (b - bg[2]) ** 2
            )
            # Dead zone below 26 kills compression noise in the flat backdrop,
            # which is what produced the faint rectangle around the glyph.
            v = 0 if d < 26 else min(255, (d - 26) * 2.6)
            mp[x, y] = int(v)

    mask = mask.filter(ImageFilter.GaussianBlur(2))
    glyph.putalpha(mask)
    return glyph


def foreground(glyph, size, frac):
    """Transparent square containing the glyph plus a soft neon bloom."""
    gw, gh = glyph.size
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    tw = max(1, int(size * frac))
    th = max(1, int(round(tw * gh / gw)))
    g = glyph.resize((tw, th), Image.LANCZOS)

    bloom = g.resize((max(1, tw // 5), max(1, th // 5)), Image.LANCZOS)
    bloom = bloom.resize((tw, th), Image.LANCZOS)
    bloom = bloom.filter(ImageFilter.GaussianBlur(max(1, size * 0.022)))
    bloom.putalpha(bloom.split()[3].point(lambda v: int(v * 0.7)))

    pos = ((size - tw) // 2, (size - th) // 2)
    canvas.alpha_composite(bloom, pos)
    canvas.alpha_composite(g, pos)
    return canvas


def solid(glyph, size, frac):
    """Opaque icon on a radial navy gradient, for pre-Android-8 launchers."""
    s = 64
    grad = Image.new("RGB", (s, s))
    gp = grad.load()
    c = (s - 1) / 2
    for y in range(s):
        for x in range(s):
            t = min(1.0, math.hypot(x - c, y - c) / (s / 2 * 1.414))
            gp[x, y] = (
                int(18 * (1 - t) + 3 * t),
                int(34 * (1 - t) + 8 * t),
                int(86 * (1 - t) + 28 * t),
            )
    base = grad.resize((size, size), Image.LANCZOS).convert("RGBA")
    base.alpha_composite(foreground(glyph, size, frac))
    return base.convert("RGB")


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/it_launcher_background" />
    <foreground android:drawable="@mipmap/it_logo_fg" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""

COLOR_XML = f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="it_launcher_background">{BG_HEX}</color>
</resources>
"""


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    print(f"  wrote {path}")


def main():
    if not os.path.isdir(RES):
        die(f"{RES} not found - run this from the repository root.")

    glyph = load_glyph()
    print(f"Logo cropped to {glyph.size[0]}x{glyph.size[1]}")

    print("Generating legacy icons (.webp)")
    for d, size in LEGACY.items():
        folder = f"{RES}/mipmap-{d}"
        os.makedirs(folder, exist_ok=True)
        icon = solid(glyph, size, LEGACY_FRAC)
        icon.save(f"{folder}/ic_launcher.webp", "WEBP", quality=95)
        icon.save(f"{folder}/ic_launcher_round.webp", "WEBP", quality=95)
        print(f"  {folder}/ic_launcher.webp ({size}x{size})")

    print("Generating adaptive foregrounds (.png)")
    for d, size in ADAPTIVE.items():
        folder = f"{RES}/mipmap-{d}"
        os.makedirs(folder, exist_ok=True)
        foreground(glyph, size, ADAPTIVE_FRAC).save(f"{folder}/it_logo_fg.png")
        print(f"  {folder}/it_logo_fg.png ({size}x{size})")

    print("Writing adaptive icon XML")
    # v31 is the one Android 12+ devices actually read - the common case.
    for api in ("v26", "v31"):
        for name in ("ic_launcher", "ic_launcher_round"):
            write(f"{RES}/mipmap-anydpi-{api}/{name}.xml", ADAPTIVE_XML)

    write(f"{RES}/values/it_launcher_background.xml", COLOR_XML)

    # Stale .png copies from an earlier manual attempt would collide with the
    # .webp files above and fail the build with "duplicate resources".
    removed = 0
    for d in LEGACY:
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            stale = f"{RES}/mipmap-{d}/{name}"
            if os.path.exists(stale):
                os.remove(stale)
                print(f"  removed conflicting {stale}")
                removed += 1
    if removed:
        print(f"Cleaned up {removed} conflicting .png file(s)")

    print("Icon generation complete.")


if __name__ == "__main__":
    main()

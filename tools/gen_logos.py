#!/usr/bin/env python3
"""Draws the Bubie mod logo and the Bubie Pack logo on a 64x64 pixel-art grid (like the Voltaic Dynamics
and Base Blueprint logos: one art pixel = 2x2 at 128 px), using the bubie.gg palette.

Run from the repository root:  python tools/gen_logos.py
Writes src/main/resources/bubie_logo.png (128 px, the in-game mod list), docs/logos/*.png (512 px for
CurseForge, 128 px, and the native 64 px art).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_textures import Img, rgb, CLEAR  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")

# bubie.gg palette (manifest.ts RAMP_KEYS) and a near-black outline (PIXEL_ART.md: never pure black)
SH0, SH1, SH2, SH3 = rgb("FFF9EE"), rgb("F3E7D5"), rgb("DDCBB2"), rgb("BFA586")
L1, L2 = rgb("F7EEE1"), rgb("E2D2BC")
G0, G1, G2 = rgb("FFE6B0"), rgb("F4BF69"), rgb("C98F3A")
C0, C1, C2, C3 = rgb("4C4547"), rgb("38333A"), rgb("29252B"), rgb("736B6C")
SCREEN, SCREEN_DEEP, SCREEN_HI = rgb("2A2629"), rgb("1B181E"), rgb("3A3540")
OUT = rgb("16141C")
WOOD, WOOD_D = rgb("9C6B3C"), rgb("6E4824")
IRON0, IRON1, IRON2 = rgb("EEF1F4"), rgb("C3C9D1"), rgb("8B929C")


def rounded(img, x, y, w, h, r, color, only_edge=False):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            dx = max(x + r - xx - 0.5, 0, xx + 0.5 - (x + w - r))
            dy = max(y + r - yy - 0.5, 0, yy + 0.5 - (y + h - r))
            if dx * dx + dy * dy <= r * r:
                img.set(xx, yy, color)


def outline(sprite, color):
    """1 px outline around every painted pixel (4-neighbourhood), like Pontilha's effect.outline."""
    out = Img(sprite.w, sprite.h)
    out.px = list(sprite.px)
    for y in range(sprite.h):
        for x in range(sprite.w):
            if sprite.px[y * sprite.w + x][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < sprite.w and 0 <= ny < sprite.h and sprite.px[ny * sprite.w + nx][3]:
                    out.px[y * sprite.w + x] = color
                    break
    return out


def paste(dst, src):
    for i, c in enumerate(src.px):
        if c[3]:
            dst.px[i] = c


def bubie(size, hx, hy):
    """The mascot, front view: CRT head (26x18), hoodie, a raised waving arm, an axe in the other hand."""
    s = Img(size, size)
    w, h = 26, 18
    # head: rounded casing with light top-left and shade bottom-right
    rounded(s, hx, hy, w, h, 2.2, SH1)
    s.rect(hx + 1, hy, w - 2, 1, SH0)
    s.rect(hx, hy + 1, 1, h - 2, SH0)
    s.rect(hx + 1, hy + h - 1, w - 2, 1, SH2)
    s.rect(hx + w - 1, hy + 1, 1, h - 2, SH2)
    s.rect(hx + 2, hy + h - 2, w - 4, 1, SH2)
    # recessed screen 20x10 with a lip
    sx, sy = hx + 3, hy + 3
    s.rect(sx - 1, sy - 1, 22, 1, SH2)
    s.rect(sx - 1, sy - 1, 1, 12, SH2)
    rounded(s, sx, sy, 20, 10, 1.6, SCREEN)
    s.rect(sx + 1, sy, 18, 1, SCREEN_DEEP)
    s.rect(sx, sy + 1, 1, 8, SCREEN_DEEP)
    s.set(sx + 17, sy + 1, SCREEN_HI)
    s.set(sx + 18, sy + 1, SCREEN_HI)
    s.set(sx + 18, sy + 2, SCREEN_HI)
    # face: round dot eyes and a pill mouth, like the bubie.gg logo
    for ex in (sx + 4, sx + 12):
        s.grid(ex, sy + 2, [".##.", "####", "####", ".##."], {"#": G1})
        s.set(ex + 1, sy + 2, G0)
    s.grid(sx + 7, sy + 7, [".####.", "######"][0:1] + [".####."], {"#": G1})
    # chin with the power LED
    s.set(hx + w - 5, hy + h - 4, G1)
    s.set(hx + w - 4, hy + h - 4, G2)

    bx, by = hx + 6, hy + h  # hoodie, 14x10
    s.rect(bx, by, 14, 10, C1)
    s.rect(bx, by, 14, 1, C2)          # shadow under the head
    s.rect(bx + 13, by + 1, 1, 9, C2)  # shaded side
    s.rect(bx + 4, by + 1, 1, 3, C3)   # drawstrings
    s.rect(bx + 9, by + 1, 1, 3, C3)
    s.set(bx + 4, by + 4, C0)
    s.set(bx + 9, by + 4, C0)
    s.rect(bx + 3, by + 6, 8, 1, C2)   # kangaroo pocket
    s.rect(bx + 3, by + 6, 1, 3, C2)
    s.rect(bx + 10, by + 6, 1, 3, C2)
    s.rect(bx, by + 9, 14, 1, C2)      # hem
    # legs and light shoes
    for lx in (bx + 3, bx + 8):
        s.rect(lx, by + 10, 3, 3, L1)
        s.rect(lx + 2, by + 10, 1, 3, L2)
        s.rect(lx - (1 if lx == bx + 3 else 0), by + 13, 4, 1, SH0)
    # left arm (viewer's left) hanging, holding an axe raised over the shoulder
    s.rect(bx - 3, by + 1, 3, 5, C1)
    s.rect(bx - 3, by + 1, 3, 1, C0)
    s.rect(bx - 3, by + 6, 3, 2, L1)
    s.set(bx - 1, by + 7, L2)
    for i in range(8):  # handle, a clean 1:1 slope
        s.set(bx - 3 - i, by + 6 - i, WOOD)
        s.set(bx - 2 - i, by + 6 - i, WOOD_D) if i < 7 else None
    ax, ay = bx - 12, by - 5  # axe head
    s.grid(ax, ay, [".###", "####", "###.", "##.."], {"#": IRON1})
    s.set(ax + 1, ay, IRON0)
    s.set(ax, ay + 1, IRON0)
    s.set(ax, ay + 2, IRON0)
    s.set(ax + 3, ay + 1, IRON2)
    s.set(ax + 2, ay + 2, IRON2)
    # right arm waving: out and up, clear of the head
    for i in range(6):
        s.rect(bx + 14 + i, by + 2 - i, 3, 3, C1)
    s.rect(bx + 14, by + 4, 2, 1, C2)
    s.rect(bx + 21, by - 6, 3, 3, L1)  # hand, clear of the head
    s.set(bx + 23, by - 4, L2)
    return outline(s, OUT)


def sparkle(img, x, y, c=G0, c2=G1):
    img.set(x, y, c)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        img.set(x + dx, y + dy, c2)


def mod_logo():
    img = Img(64, 64)
    rounded(img, 2, 2, 60, 60, 7, rgb("0D0B10"))
    rounded(img, 3, 3, 58, 58, 6, SCREEN_DEEP)
    img.rect(9, 3, 46, 1, SCREEN)  # top lip catching the light
    # warm glow and the amber ring, lit from the top-left
    cx = cy = 31.5
    for y in range(64):
        for x in range(64):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32)
            a = math.degrees(math.atan2(y + 0.5 - 32, x + 0.5 - 32))
            if d < 21.5 and img.px[y * 64 + x] == SCREEN_DEEP:
                img.set(x, y, rgb("221B1F") if d < 15 else rgb("1F191D"))
            if 21.5 <= d < 23.6:
                img.set(x, y, G1 if -180 <= a <= -60 else G2)
    sprite = bubie(64, 19, 12)
    paste(img, sprite)
    sparkle(img, 11, 12)
    sparkle(img, 53, 51)
    img.set(52, 15, G1)
    img.set(12, 50, G2)
    return img


def grass_block(img, top_y):
    """An isometric grass block: 32 wide, 2:1 slopes, light from the top-left."""
    gl, gm, gd = rgb("8BC34A"), rgb("6DAA44"), rgb("4E8A2E")
    dl, dd, dk = rgb("9A6A3A"), rgb("7A5230"), rgb("5E3E24")
    mid = top_y + 8
    for y in range(top_y, top_y + 17):
        half = (y - top_y) * 2 if y <= mid else (top_y + 16 - y) * 2
        for x in range(32 - half, 32 + half):
            img.set(x, y, gm)
    for x in range(16, 48):  # sides, 12 tall
        yt = mid + ((x - 16) // 2 if x < 32 else (47 - x) // 2)
        for y in range(yt, yt + 12):
            img.set(x, y, dl if x < 32 else dk)
        for y in range(yt, yt + 3):  # grass hanging over the edge
            img.set(x, y, gm if x < 32 else gd)
        if x % 3 == 0:
            img.set(x, yt + 3, gm if x < 32 else gd)
    # top highlights and texture
    for y in range(top_y + 2, top_y + 15):
        for x in range(18, 46):
            if img.px[y * 64 + x] == gm and (x * 7 + y * 3) % 11 == 0:
                img.set(x, y, gl)
    for x in range(17, 32):
        y = mid + (x - 16) // 2
        img.set(x, y + 6 + (x % 4 == 0), dd)
    block = Img(64, 64)
    block.px = [c if c[3] else CLEAR for c in img.px]
    return block


def bolt(img, x, y):
    """The Voltaic Dynamics lightning bolt."""
    img.grid(x, y, ["...##", "..##.", ".##..", "#####", "..##.", ".##..", "##..."], {"#": rgb("C98BE0")})
    img.set(x + 3, y, rgb("F0DBFF"))
    img.set(x + 2, y + 1, rgb("F0DBFF"))


def blueprint(img, x, y):
    """The Base Blueprint sheet: a blue page with a little house on it."""
    img.grid(x, y, ["#######", "#..w..#", "#.www.#", "#.w.w.#", "#.....#", "#######"],
             {"#": rgb("3A63B8"), ".": rgb("5B8DE0"), "w": rgb("EEF5FF")})


def pack_logo():
    img = Img(64, 64)
    rounded(img, 2, 2, 60, 60, 7, SH3)
    rounded(img, 3, 3, 58, 58, 6, rgb("F5ECDD"))
    img.rect(9, 3, 46, 1, SH0)
    # soft amber halo behind Bubie
    for y in range(64):
        for x in range(64):
            if img.px[y * 64 + x] == rgb("F5ECDD") and math.hypot(x + 0.5 - 32, y + 0.5 - 26) < 19:
                img.set(x, y, rgb("FBE7C2"))
    block = Img(64, 64)
    block = grass_block(block, 38)
    paste(img, outline(block, OUT))
    paste(img, bubie(64, 19, 10))
    tiny = Img(64, 64)
    bolt(tiny, 7, 29)
    blueprint(tiny, 51, 30)
    paste(img, outline(tiny, OUT))
    sparkle(img, 10, 11)
    sparkle(img, 54, 13)
    return img


def scaled(img, k):
    big = Img(img.w * k, img.h * k)
    for y in range(img.h):
        for x in range(img.w):
            c = img.px[y * img.w + x]
            for yy in range(k):
                row = (y * k + yy) * big.w
                for xx in range(k):
                    big.px[row + x * k + xx] = c
    return big


def save(img, path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    import gen_textures
    old = gen_textures.ROOT
    gen_textures.ROOT = os.path.dirname(full)
    img.save(os.path.basename(full))
    gen_textures.ROOT = old


if __name__ == "__main__":
    mod, pack = mod_logo(), pack_logo()
    save(scaled(mod, 2), "src/main/resources/bubie_logo.png")
    for name, art in (("bubie", mod), ("bubie-pack", pack)):
        save(art, f"docs/logos/{name}-64.png")
        save(scaled(art, 2), f"docs/logos/{name}-128.png")
        save(scaled(art, 8), f"docs/logos/{name}-512.png")

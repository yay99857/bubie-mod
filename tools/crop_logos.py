#!/usr/bin/env python3
"""Cuts the 1024 px logo squares out of the runLogos screenshots and writes the logo sizes.

    ./gradlew runLogos && python tools/crop_logos.py

docs/logos/bubie-512.png and bubie-pack-512.png (CurseForge), *-128.png, and src/main/resources/bubie_logo.png
(the mod list in game, 128 px). Downscaling averages each block of pixels, which suits a 3D render.
"""
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_textures import Img  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SIDE = 1024


def load(path):
    d = open(path, "rb").read()
    i, idat = 8, b""
    while i < len(d):
        n = struct.unpack(">I", d[i:i + 4])[0]
        t, c = d[i + 4:i + 8], d[i + 8:i + 8 + n]
        if t == b"IHDR":
            w, h, _, ct = struct.unpack(">IIBB", c[:10])
        elif t == b"IDAT":
            idat += c
        i += 12 + n
    raw = zlib.decompress(idat)
    bpp = {6: 4, 2: 3}[ct]
    stride, rows, prev, p = w * bpp, [], bytearray(w * bpp), 0
    for _ in range(h):
        f, line = raw[p], bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    return w, h, bpp, rows


def square(path):
    """Finds the logo square: the bounding box of non-black pixels."""
    w, h, bpp, rows = load(path)
    xs, ys = [], []
    for y in range(0, h, 4):
        for x in range(0, w, 4):
            px = rows[y][x * bpp:x * bpp + 3]
            if sum(px) > 30:
                xs.append(x)
                ys.append(y)
    x0, y0 = min(xs), min(ys)
    while x0 > 0 and sum(rows[y0 + 8][(x0 - 1) * bpp:(x0 - 1) * bpp + 3]) > 30:
        x0 -= 1
    while y0 > 0 and sum(rows[y0 - 1][(x0 + 8) * bpp:(x0 + 8) * bpp + 3]) > 30:
        y0 -= 1
    side = min(SIDE, w - x0, h - y0)
    return [[tuple(rows[y0 + y][(x0 + x) * bpp:(x0 + x) * bpp + 3]) + (255,) for x in range(side)] for y in range(side)]


def shrink(px, size):
    side = len(px)
    k = side / size
    img = Img(size, size)
    for y in range(size):
        for x in range(size):
            acc = [0, 0, 0]
            n = 0
            for yy in range(int(y * k), int((y + 1) * k)):
                for xx in range(int(x * k), int((x + 1) * k)):
                    c = px[yy][xx]
                    acc[0] += c[0]
                    acc[1] += c[1]
                    acc[2] += c[2]
                    n += 1
            img.px[y * size + x] = (acc[0] // n, acc[1] // n, acc[2] // n, 255)
    return img


def save(img, rel):
    import gen_textures
    full = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    gen_textures.ROOT = os.path.dirname(full)
    img.save(os.path.basename(full))


if __name__ == "__main__":
    shots = os.path.join(ROOT, "run", "screenshots")
    for shot, name in (("bubie_logo_mod.png", "bubie"), ("bubie_logo_pack.png", "bubie-pack")):
        px = square(os.path.join(shots, shot))
        save(shrink(px, 512), f"docs/logos/{name}-512.png")
        small = shrink(px, 128)
        save(small, f"docs/logos/{name}-128.png")
        if name == "bubie":
            save(small, "src/main/resources/bubie_logo.png")

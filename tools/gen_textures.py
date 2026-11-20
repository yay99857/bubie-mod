#!/usr/bin/env python3
"""Draws every texture of the Bubie mod into src/main/resources/assets/bubie/textures.

Pure Python (no Pillow): each texture is painted pixel by pixel from the bubie.gg palette
(bubie/frontend/features/my-bubie/manifest.ts, RAMP_KEYS) and written as a PNG.
Run from the repository root:  python tools/gen_textures.py
"""
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "bubie", "textures")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


CLEAR = (0, 0, 0, 0)
# shell (ivory)
SH0, SH1, SH2, SH3 = rgb("FFF9EE"), rgb("F3E7D5"), rgb("DDCBB2"), rgb("BFA586")
# limbs (the two-tone ivory of the base body)
L1, L2 = rgb("F7EEE1"), rgb("E2D2BC")
# amber glow
G0, G1, G2 = rgb("FFE6B0"), rgb("F4BF69"), rgb("C98F3A")
# hoodie (cloth: black)
C0, C1, C2, C3, C4 = rgb("4C4547"), rgb("38333A"), rgb("29252B"), rgb("736B6C"), rgb("1B181E")
SCREEN, SCREEN_DEEP, SCREEN_HI = rgb("2A2629"), rgb("1B181E"), rgb("3A3540")
OUTLINE = rgb("3B3537")

class Img:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def grid(self, x, y, rows, palette):
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch in palette:
                    self.set(x + c, y + r, palette[ch])

    def save(self, *path):
        full = os.path.join(ROOT, *path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                raw.extend(bytes(self.px[y * self.w + x]))

        def chunk(kind, data):
            body = kind + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        with open(full, "wb") as f:
            f.write(png)
        print("wrote", os.path.relpath(full))


# ------------------------------------------------------------------ entity
# Box UV layout (see client/BubieModel.java): for a box at (u, v) of size w x h x d,
#   top (u+d, v) w x d, bottom (u+d+w, v) w x d,
#   right (u, v+d) d x h, front (u+d, v+d) w x h, left (u+d+w, v+d) d x h, back (u+d+w+d, v+d) w x h.

def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


HEAD = box_faces(0, 0, 12, 8, 8)
TUBE = box_faces(0, 16, 8, 6, 3)
TORSO = box_faces(0, 26, 6, 5, 4)
HOOD = box_faces(24, 26, 5, 2, 2)
ARM_R, ARM_L = box_faces(42, 0, 2, 5, 2), box_faces(42, 8, 2, 5, 2)
LEG_R, LEG_L = box_faces(52, 0, 2, 3, 2), box_faces(52, 6, 2, 3, 2)


def entity():
    img = Img(64, 64)
    # --- head: the CRT casing
    x, y, w, h = HEAD["top"]
    img.rect(x, y, w, h, SH0)
    img.rect(x, y, w, 1, SH1)
    img.rect(x, y + h - 1, w, 1, SH1)
    x, y, w, h = HEAD["bottom"]
    img.rect(x, y, w, h, SH3)
    for side in ("right", "left"):
        x, y, w, h = HEAD[side]
        img.rect(x, y, w, h, SH1)
        img.rect(x, y, w, 1, SH0)
        img.rect(x, y + h - 1, w, 1, SH2)
        for vy in (2, 4):
            img.rect(x + 2, y + vy, 4, 1, SH3)  # vents
    x, y, w, h = HEAD["back"]
    img.rect(x, y, w, h, SH2)
    img.rect(x, y, w, 1, SH1)
    img.rect(x, y + h - 1, w, 1, SH3)
    # front: bezel, recessed screen (10x5), chin with the amber LED
    x, y, w, h = HEAD["front"]
    img.rect(x, y, w, h, SH1)
    img.rect(x, y, w, 1, SH0)
    img.rect(x, y, 1, h, SH0)
    img.rect(x + w - 1, y, 1, h, SH2)
    img.rect(x, y + h - 1, w, 1, SH2)
    img.rect(x + 1, y + 1, 10, 5, SCREEN)
    img.rect(x + 1, y + 1, 10, 1, SCREEN_DEEP)
    img.rect(x + 1, y + 1, 1, 5, SCREEN_DEEP)
    img.set(x + 9, y + 2, SCREEN_HI)
    img.set(x + 10, y + 2, SCREEN_HI)
    img.set(x + 9, y + 3, SCREEN_HI)
    img.set(x + 10, y + 6, G1)  # power LED, bottom right like the sprite's
    # --- tube behind the head
    for name, color in (("top", SH1), ("bottom", SH3), ("right", SH2), ("left", SH2), ("front", SH2), ("back", SH2)):
        x, y, w, h = TUBE[name]
        img.rect(x, y, w, h, color)
    x, y, w, h = TUBE["back"]
    img.rect(x, y, w, 1, SH1)
    for vy in (2, 4):
        img.rect(x + 2, y + vy, 4, 1, SH3)
    # --- torso: the hoodie
    x, y, w, h = TORSO["front"]
    img.grid(x, y, ["2s22s2", "1s11s1", "111111", "122221", "222222"], {"1": C1, "2": C2, "s": C3})
    for side in ("right", "left"):
        x, y, w, h = TORSO[side]
        img.rect(x, y, w, h, C1)
        img.rect(x, y + h - 1, w, 1, C2)
    x, y, w, h = TORSO["back"]
    img.rect(x, y, w, h, C1)
    img.rect(x, y + h - 1, w, 1, C2)
    img.rect(*TORSO["top"], C0)
    img.rect(*TORSO["bottom"], C2)
    # hood, lying down the back
    for name, color in (("front", C1), ("back", C1), ("right", C1), ("left", C1), ("top", C2), ("bottom", C2)):
        img.rect(*HOOD[name], color)
    x, y, w, h = HOOD["back"]
    img.rect(x, y, w, 1, C0)
    x, y, w, h = HOOD["top"]
    img.rect(x, y, w, 1, C0)
    # --- arms: black sleeves, cream hands
    for arm in (ARM_R, ARM_L):
        for side in ("right", "front", "left", "back"):
            x, y, w, h = arm[side]
            img.rect(x, y, w, 3, C1)
            img.rect(x, y + 3, w, 1, C2)
            img.rect(x, y + 4, w, 1, L1)
        img.rect(*arm["top"], C0)
        img.rect(*arm["bottom"], L2)
    # --- legs: cream with light feet
    for leg in (LEG_R, LEG_L):
        for side in ("right", "front", "left", "back"):
            x, y, w, h = leg[side]
            img.rect(x, y, w, 2, L1 if side in ("front", "back") else L2)
            img.rect(x, y + 2, w, 1, SH0)
        img.rect(*leg["top"], L1)
        img.rect(*leg["bottom"], L2)
    img.save("entity", "bubie.png")



# ------------------------------------------------------------------ items

def item_bubie():
    img = Img(16, 16)
    img.grid(0, 0, [
        "................",
        "..OOOOOOOOOOOO..",
        ".OhhhhhhhhhhhhO.",
        ".OhDDDDDDDDDDdO.",
        ".OhDSSSSSSSSSdO.",
        ".OhDSaaSSaaSSdO.",
        ".OhDSaaSSaaSSdO.",
        ".OhDSSSSSSSSSdO.",
        ".OhDSSSaaSSSSdO.",
        ".OccccccccccccO.",
        ".OddddddddddddO.",
        "..OOOOOOOOOOOO..",
        "....OKKssKKO....",
        "...OcKKKKKKcO...",
        "....OKKKKKKO....",
        ".....OfOOfO.....",
    ], {"O": OUTLINE, "h": SH0, "c": SH1, "d": SH2, "D": SCREEN_DEEP, "S": SCREEN, "a": G1, "L": G1, "l": G2,
        "K": C1, "s": C3, "f": SH0})
    img.save("item", "bubie.png")


def item_clipboard():
    img = Img(16, 16)
    board, board_dark, paper, line = rgb("9C6B3C"), rgb("6E4824"), SH0, SH2
    img.rect(2, 2, 12, 14, board_dark)
    img.rect(3, 3, 10, 12, board)
    img.rect(4, 4, 8, 10, paper)
    img.rect(4, 13, 8, 1, SH1)
    # the clip
    img.rect(6, 1, 4, 3, G2)
    img.rect(7, 1, 2, 1, G0)
    img.rect(6, 3, 4, 1, OUTLINE)
    # an area drawn on the paper: amber box with corner dots
    for x in range(5, 11):
        img.set(x, 6, G1)
        img.set(x, 11, G1)
    for y in range(6, 12):
        img.set(5, y, G1)
        img.set(10, y, G1)
    img.set(5, 6, G2)
    img.set(10, 11, G2)
    img.rect(7, 8, 2, 2, line)
    img.save("item", "bubie_clipboard.png")


CHIP_SYMBOLS = {
    "speed": (rgb("FFE066"), ["..#.", ".##.", "##..", ".#.."]),
    "storage": (SH1, ["####", "#..#", "####", "####"]),
    "range": (rgb("7F9CCB"), ["####", "#..#", "#..#", "####"]),
    "magnet": (rgb("E05A4E"), ["#..#", "#..#", "#..#", ".##."]),
    "smelting": (rgb("F08A3C"), [".#..", ".##.", "####", ".##."]),
    "efficiency": (rgb("8FD18A"), ["..##", ".###", "###.", "#..."]),
}


def item_chips():
    for name, (color, rows) in CHIP_SYMBOLS.items():
        img = Img(16, 16)
        # gold pins on all four sides
        for i in range(4, 12, 2):
            img.rect(i, 1, 1, 2, G2)
            img.rect(i, 13, 1, 2, G2)
            img.rect(1, i, 2, 1, G2)
            img.rect(13, i, 2, 1, G2)
        img.rect(3, 3, 10, 10, C4)
        img.rect(4, 4, 8, 8, C2)
        img.rect(4, 4, 8, 1, C0)
        img.set(4, 11, G1)  # pin-1 dot
        img.grid(6, 6, rows, {"#": color})
        img.save("item", "chip_" + name + ".png")


# ------------------------------------------------------------------ station block

def station():
    front = Img(16, 16, SH1)
    front.rect(0, 0, 16, 1, SH0)
    front.rect(0, 0, 1, 16, SH0)
    front.rect(15, 0, 1, 16, SH2)
    front.rect(0, 15, 16, 1, SH3)
    front.rect(2, 2, 12, 8, SCREEN)
    front.rect(2, 2, 12, 1, SCREEN_DEEP)
    front.rect(2, 2, 1, 8, SCREEN_DEEP)
    front.rect(1, 1, 14, 1, SH2)
    front.rect(1, 1, 1, 10, SH2)
    front.rect(14, 1, 1, 10, SH0)
    front.rect(1, 10, 14, 1, SH0)
    front.grid(3, 3, ["..........", ".##....##.", ".##....##.", "..........", "...####...", ".........."]
               [0:6], {"#": G1})
    front.set(12, 3, SCREEN_HI)
    # the charging slot with its amber light
    front.rect(4, 12, 8, 2, OUTLINE)
    front.rect(5, 12, 6, 1, G1)
    front.rect(5, 13, 6, 1, G2)
    front.save("block", "bubie_station_front.png")

    side = Img(16, 16, SH1)
    side.rect(0, 0, 16, 1, SH0)
    side.rect(0, 15, 16, 1, SH3)
    side.rect(15, 0, 1, 16, SH2)
    for y in (4, 6, 8, 10):
        side.rect(3, y, 10, 1, SH3)
        side.rect(3, y + 1, 10, 1, SH0)
    side.rect(13, 2, 1, 1, G1)
    side.save("block", "bubie_station_side.png")

    top = Img(16, 16, SH0)
    top.rect(0, 0, 16, 16, SH1)
    top.rect(1, 1, 14, 14, SH0)
    top.rect(3, 3, 10, 10, OUTLINE)
    top.rect(4, 4, 8, 8, SCREEN)
    for i in range(4, 12):
        top.set(i, 4, G2)
        top.set(i, 11, G2)
        top.set(4, i, G2)
        top.set(11, i, G2)
    top.grid(6, 5, ["..#.", ".##.", "####", ".##.", ".#..", "#..."][0:6], {"#": G1})
    top.save("block", "bubie_station_top.png")

    bottom = Img(16, 16, SH2)
    bottom.rect(1, 1, 14, 14, SH3)
    bottom.save("block", "bubie_station_bottom.png")


if __name__ == "__main__":
    entity()
    item_bubie()
    item_clipboard()
    item_chips()
    station()

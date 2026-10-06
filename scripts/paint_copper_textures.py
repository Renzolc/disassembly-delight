#!/usr/bin/env python3
"""Paints the 16x16 copper Disassembly Table textures (disassembly_delight:block/disassembler_*).

Original pixel art in a copper-machine style; no textures from other mods are copied. Deterministic (fixed seed),
so re-running produces the same files. Usage: python3 scripts/paint_copper_textures.py [out_dir]
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "src/main/resources/assets/disassembly_delight/textures/block"


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# Copper, darkest to lightest (warm, slightly exposed like the concept art).
C = [hexc(x) for x in ["4a2619", "5c2f1f", "6e3a26", "87462f", "9a5038", "a75a40", "b26247", "c26b4c", "d67b5b", "e3826c"]]
# Patina greens, dark to light.
P = [hexc(x) for x in ["3b6a58", "3e816b", "4c9484", "53a178", "66a977", "7ab799"]]
# Dark interior.
D = [hexc(x) for x in ["140d0b", "1f1410", "2b1c16", "3a261d", "4a3022"]]
# Wood (cog spokes).
W = [hexc(x) for x in ["3b2614", "4d3219", "5f3f20", "74502c", "8a6238"]]
# Iron and gold for the window scene.
IRON = [hexc(x) for x in ["5a5a5a", "7d7d7d", "a8a8a8", "d8d8d8", "f0f0f0"]]
GOLD = [hexc(x) for x in ["8a5d0e", "c7921b", "f0c33c", "fde67a"]]
CLEAR = (0, 0, 0, 0)


def new():
    return Image.new("RGBA", (16, 16), CLEAR)


def body(rng):
    """Copper machine panel: three vertical copper planks, dark seams, rivets, a few patina pixels at the seams."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            plank = 0 if x < 5 else (1 if x < 11 else 2)
            base = [5, 6, 5][plank]
            v = base + rng.choice([-1, 0, 0, 0, 1])
            if x in (0, 5, 11):  # left edge of each plank catches light
                v = min(9, v + 2)
            px[x, y] = C[v]
    for x in (4, 10, 15):  # seams
        for y in range(16):
            px[x, y] = C[1] if rng.random() > 0.15 else C[2]
    for y in (0, 15):  # top/bottom trim of each panel
        for x in range(16):
            px[x, y] = C[3] if y == 15 else C[7]
    for x, y in [(2, 2), (7, 2), (13, 2), (2, 13), (7, 13), (13, 13)]:  # rivets
        px[x, y] = C[9]
        px[x + 1, y + 1] = C[2]
    for _ in range(9):  # patina creeping out of the seams
        x = rng.choice([3, 4, 5, 9, 10, 11, 14, 15])
        y = rng.randrange(1, 15)
        px[x, y] = rng.choice(P[1:5])
    return im


def casing(rng):
    """Corner posts, trim bands and caps: darker hammered copper with bolts and patina spots."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = C[4 + rng.choice([-1, 0, 0, 1])]
    for i in range(16):
        px[i, 0] = C[8]
        px[0, i] = C[7]
        px[i, 15] = C[2]
        px[15, i] = C[2]
    for x, y in [(3, 3), (12, 3), (3, 12), (12, 12), (7, 7)]:
        px[x, y] = C[9]
        px[x + 1, y] = C[6]
        px[x, y + 1] = C[2]
    for _ in range(14):
        x, y = rng.randrange(16), rng.randrange(16)
        if (x < 4 or x > 11) and (y < 4 or y > 11):  # patina collects at the corners
            px[x, y] = rng.choice(P[1:6])
    return im


def hopper(rng):
    """Funnel copper with green patina streaks running down from the rim."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = C[5 + rng.choice([-1, 0, 0, 1, 1])]
    for x in range(16):
        px[x, 0] = C[9]
        px[x, 1] = C[7]
    streaks = [1, 4, 7, 9, 12, 14]
    for sx in streaks:
        length = rng.randrange(5, 14)
        for y in range(1, length):
            fade = y / length
            if rng.random() > fade * 0.8:
                px[sx, y] = P[min(5, int(4 - fade * 3) + rng.choice([0, 1]))]
            if rng.random() < 0.25 and sx + 1 < 16:
                px[sx + 1, y] = P[2]
    for x in range(16):  # rim lip shadow
        px[x, 15] = C[2]
    return im


def dark(rng):
    """Inside of the hopper and the machine: dark copper."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = D[rng.choice([1, 2, 2, 3])]
    for _ in range(6):
        px[rng.randrange(16), rng.randrange(16)] = C[2]
    return im


def window(rng):
    """The chamber seen through the front window: an iron piece and a chain mid-breakdown, shards and nuggets."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            glow = max(0, (y - 9)) * 0.12  # warm light from below
            base = D[rng.choice([1, 2, 2])]
            px[x, y] = tuple(int(base[i] + glow * (C[6][i] - base[i])) for i in range(3)) + (255,)
    # Iron ingot, tilted.
    ingot = [(3, 8), (4, 8), (5, 8), (6, 8), (2, 9), (3, 9), (4, 9), (5, 9), (6, 9), (3, 10), (4, 10), (5, 10), (6, 10), (7, 9)]
    for i, (x, y) in enumerate(ingot):
        px[x, y] = IRON[3] if y == 8 else (IRON[2] if y == 9 else IRON[1])
    px[3, 8] = IRON[4]
    # Chain going up to the right.
    for i, (x, y) in enumerate([(7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 3)]):
        px[x, y] = IRON[2] if i % 2 else IRON[3]
        if i % 2 == 0 and x + 1 < 16:
            px[x + 1, y + 1] = IRON[0]
    # Shards and nuggets flying around.
    for x, y, c in [(9, 9, GOLD[2]), (11, 8, GOLD[1]), (12, 11, GOLD[3]), (8, 12, IRON[2]), (5, 5, IRON[3]),
                    (13, 6, GOLD[2]), (2, 12, GOLD[1]), (10, 12, GOLD[2]), (6, 12, IRON[1]), (13, 9, IRON[3])]:
        px[x, y] = c
    for x in range(16):  # pile at the bottom
        if rng.random() < 0.6:
            px[x, 14] = rng.choice([GOLD[1], IRON[1], C[3], GOLD[2]])
        px[x, 15] = rng.choice([IRON[0], C[2], D[3]])
    return im


def chute(rng):
    """Output chute mouth: dark opening, copper lip at the top, pieces spilling at the bottom."""
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = D[0 if y < 10 else 1 + rng.choice([0, 1])]
    for x in range(16):
        px[x, 0] = C[8]
        px[x, 1] = C[5]
        px[x, 2] = C[2]
    for x in range(16):
        if rng.random() < 0.55:
            px[x, 15] = rng.choice([IRON[2], GOLD[1], IRON[1], W[3], GOLD[2]])
        if rng.random() < 0.3:
            px[x, 14] = rng.choice([IRON[1], GOLD[1], W[2]])
    return im


def cog(rng):
    """12-tooth cogwheel with 6 wooden spokes, copper rim and teeth, patina on the tooth tips. Transparent outside.

    Shapes are sampled 8x8 per pixel so the teeth stay even at 16x16.
    """
    im = new()
    px = im.load()
    cx = cy = 7.5
    teeth = 12
    period = 2 * math.pi / teeth

    def region(x, y):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        off = abs(((a + period / 2) % period) - period / 2)
        if r <= 1.2:
            return "axle"
        if r <= 2.5:
            return "hub"
        if r <= 4.5:
            sp = 2 * math.pi / 6
            soff = abs(((a + sp / 2) % sp) - sp / 2)
            return "spoke" if soff * r < 0.8 else None
        if r <= 5.9:
            return "rim"
        # Tooth: a trapezoid, wider at the root.
        half = (1.25 - (r - 5.9) * 0.18) / r
        if r <= 8.0 and off < half:
            return "tip" if r > 7.1 else "tooth"
        return None

    n = 8
    for y in range(16):
        for x in range(16):
            counts = {}
            for sy in range(n):
                for sx in range(n):
                    reg = region(x + (sx + 0.5) / n - 0.5, y + (sy + 0.5) / n - 0.5)
                    counts[reg] = counts.get(reg, 0) + 1
            filled = n * n - counts.get(None, 0)
            if filled < n * n * 0.45:
                continue
            reg = max((k for k in counts if k), key=lambda k: counts[k])
            lit = (x - cx) + (y - cy) < 0  # light from the top left
            if reg == "axle":
                c = D[1]
            elif reg == "hub":
                c = C[8] if lit else C[4]
            elif reg == "spoke":
                c = W[3] if lit else W[1]
            elif reg == "rim":
                c = C[6] if lit else C[3]
            elif reg == "tooth":
                c = C[8] if lit else C[5]
            else:
                c = rng.choice([P[3], P[4]]) if lit else rng.choice([P[1], P[2]])
            px[x, y] = c
    return im


def main():
    rng = random.Random(1001)
    OUT.mkdir(parents=True, exist_ok=True)
    for name, painter in [("body", body), ("casing", casing), ("hopper", hopper), ("dark", dark), ("window", window),
                          ("chute", chute), ("cog", cog)]:
        painter(random.Random(f"{name}-1001")).save(OUT / f"disassembler_{name}.png")
        print("wrote", OUT / f"disassembler_{name}.png")


if __name__ == "__main__":
    main()

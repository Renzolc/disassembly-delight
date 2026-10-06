#!/usr/bin/env python3
"""Small isometric renderer for a Minecraft block model (elements + textures), used for the README preview.

Usage: render_preview.py <model.json> <out.png> [--vanilla DIR]
DIR holds the extracted vanilla textures (assets/minecraft/textures); needed for minecraft:* texture references.
Draws every texel as a quad, sorts by depth (painter's algorithm), uses Minecraft's directional face shading,
and drops pixels with alpha < 0.1 like the cutout render type.
"""
import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets"


def norm(v):
    l = math.sqrt(sum(c * c for c in v))
    return tuple(c / l for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def load_texture(ref, vanilla):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    base = Path(vanilla) / "assets" if ns == "minecraft" else ASSETS
    im = Image.open(base / ns / "textures" / (path + ".png")).convert("RGBA")
    return im.crop((0, 0, im.width, im.width))  # first frame of animated textures


def face_corners(face, f, t):
    """Returns P(s, tt) where s runs along U and tt along V of the face."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    lerp = lambda a, b, k: a + (b - a) * k
    return {
        "north": lambda s, v: (lerp(x1, x0, s), lerp(y1, y0, v), z0),
        "south": lambda s, v: (lerp(x0, x1, s), lerp(y1, y0, v), z1),
        "west": lambda s, v: (x0, lerp(y1, y0, v), lerp(z0, z1, s)),
        "east": lambda s, v: (x1, lerp(y1, y0, v), lerp(z1, z0, s)),
        "up": lambda s, v: (lerp(x0, x1, s), y1, lerp(z0, z1, v)),
        "down": lambda s, v: (lerp(x0, x1, s), y0, lerp(z1, z0, v)),
    }[face]


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def rotator(rot):
    if not rot:
        return lambda p: p
    a = math.radians(rot["angle"])
    o = rot.get("origin", [8, 8, 8])
    c, s = math.cos(a), math.sin(a)
    ax = rot["axis"]

    def r(p):
        x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
        if ax == "y":
            x, z = x * c + z * s, -x * s + z * c
        elif ax == "x":
            y, z = y * c - z * s, y * s + z * c
        else:
            x, y = x * c - y * s, x * s + y * c
        return (x + o[0], y + o[1], z + o[2])

    return r


def shade(n):
    up = 1.0 if n[1] > 0 else 0.5
    return min(1.0, n[0] ** 2 * 0.6 + n[1] ** 2 * up + n[2] ** 2 * 0.8)


def quads(model, vanilla):
    textures = model["textures"]

    def resolve(ref):
        while ref.startswith("#"):
            ref = textures[ref[1:]]
        return ref

    cache = {}
    for el in model["elements"]:
        rot = rotator(el.get("rotation"))
        for face, spec in el["faces"].items():
            ref = resolve(spec["texture"])
            if ref not in cache:
                cache[ref] = load_texture(ref, vanilla)
            tex = cache[ref]
            px = tex.load()
            k = tex.width / 16.0
            u0, v0, u1, v1 = spec["uv"]
            P = face_corners(face, el["from"], el["to"])
            n = rot(tuple(c + 8 for c in NORMALS[face]))
            o = rot((8, 8, 8))
            normal = norm(tuple(a - b for a, b in zip(n, o)))
            # Split on texture pixel boundaries.
            def cuts(a, b):
                lo, hi = min(a, b) * k, max(a, b) * k
                pts = sorted({lo, hi} | {float(i) for i in range(math.ceil(lo), math.floor(hi) + 1)})
                return [((p / k) - a) / (b - a) for p in pts] if b != a else [0.0, 1.0]
            su = sorted(cuts(u0, u1))
            sv = sorted(cuts(v0, v1))
            for i in range(len(su) - 1):
                for j in range(len(sv) - 1):
                    sm, vm = (su[i] + su[i + 1]) / 2, (sv[j] + sv[j + 1]) / 2
                    tu = int(min(tex.width - 1, max(0, (u0 + (u1 - u0) * sm) * k)))
                    tv = int(min(tex.height - 1, max(0, (v0 + (v1 - v0) * vm) * k)))
                    col = px[tu, tv]
                    if col[3] < 26:
                        continue
                    pts = [rot(P(su[i], sv[j])), rot(P(su[i + 1], sv[j])), rot(P(su[i + 1], sv[j + 1])), rot(P(su[i], sv[j + 1]))]
                    yield pts, normal, col, el.get("shade", True)


def render(model, vanilla, yaw_deg, pitch_deg=30, size=512, scale=None):
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    # yaw 45 = camera at north-west looking south-east.
    fwd = norm((math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)))
    right = norm(cross(fwd, (0, 1, 0)))
    up = cross(right, fwd)
    ss = 4
    W = size * ss
    scale = (scale or size / 30.0) * ss
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    items = []
    for pts, normal, col, shaded in quads(model, vanilla):
        if dot(normal, fwd) >= 0:
            continue
        depth = sum(dot(p, fwd) for p in pts) / 4
        items.append((depth, pts, normal if shaded else None, col))
    items.sort(key=lambda it: -it[0])
    c = (8, 8, 8)
    for depth, pts, normal, col in items:
        sh = shade(normal) if normal else 1.0
        rgb = tuple(int(ch * sh) for ch in col[:3]) + (255,)
        poly = []
        for p in pts:
            d = tuple(a - b for a, b in zip(p, c))
            poly.append((W / 2 + dot(d, right) * scale, W / 2 - dot(d, up) * scale))
        draw.polygon(poly, fill=rgb)
    return img.resize((size, size), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("out")
    ap.add_argument("--vanilla", default="/tmp/vanilla")
    a = ap.parse_args()
    model = json.loads(Path(a.model).read_text())
    front = render(model, a.vanilla, 45)    # front (window, chute) on the left, cog on the right
    back = render(model, a.vanilla, 225)    # back corner
    sheet = Image.new("RGBA", (front.width * 2 + 32, front.height), (46, 50, 58, 255))
    sheet.alpha_composite(front, (0, 0))
    sheet.alpha_composite(back, (front.width + 32, 0))
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(a.out)
    print("wrote", a.out)


if __name__ == "__main__":
    main()

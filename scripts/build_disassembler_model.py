#!/usr/bin/env python3
"""Writes the copper Disassembly Table block model (models/block/disassembler.json).

Axes are for facing=north (front with window and chute toward -Z); the blockstate rotates it for the other facings.
Every face gets an explicit UV (Minecraft's own default for that face), so the preview renderer and the game agree.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/disassembly_delight/models/block/disassembler.json"

FACES = ("north", "south", "east", "west", "up", "down")


def auto_uv(face, f, t):
    if face == "down":
        return [f[0], 16 - t[2], t[0], 16 - f[2]]
    if face == "up":
        return [f[0], f[2], t[0], t[2]]
    if face == "north":
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if face == "south":
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if face == "west":
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]  # east


def clamp_uv(uv):
    # Parts that stick out of the block (cog, flaps) would get UVs outside 0..16; wrap them back in.
    out = []
    for v in uv:
        while v < 0:
            v += 16
        while v > 16:
            v -= 16
        out.append(round(v, 3))
    return out


def element(name, f, t, textures, faces=FACES, uv=None, rotation=None, cull=None):
    """textures: a texture variable for every face, or a dict face -> variable."""
    el = {"name": name, "from": list(f), "to": list(t), "faces": {}}
    for face in faces:
        tex = textures[face] if isinstance(textures, dict) else textures
        if tex is None:
            continue
        face_uv = uv[face] if isinstance(uv, dict) and face in uv else (uv if isinstance(uv, list) else auto_uv(face, f, t))
        entry = {"uv": clamp_uv(face_uv) if not isinstance(uv, list) else face_uv, "texture": "#" + tex}
        if cull and face in cull:
            entry["cullface"] = face
        el["faces"][face] = entry
    if rotation:
        el["rotation"] = rotation
    return el


def build():
    els = []
    # Body core, set back so the window and chute sit in recesses. Its west side is hidden by the stone plate.
    els.append(element("body", (1, 0, 2), (15, 12, 15), {"north": "dark", "south": "body", "east": "body", "west": "body", "up": "casing", "down": "casing"},
                       cull=["down"]))
    # Front panels around the window (x 4..12, y 4..10) and the chute (x 5..11, y 0..3).
    front = {"north": "body", "south": None, "east": "body", "west": "body", "up": "body", "down": "body"}
    els.append(element("front_left", (1, 0, 1), (4, 12, 2), front))
    els.append(element("front_right", (12, 0, 1), (15, 12, 2), front))
    els.append(element("front_top", (4, 10, 1), (12, 12, 2), front))
    els.append(element("front_mid", (4, 3, 1), (12, 4, 2), front))
    els.append(element("front_chute_l", (4, 0, 1), (5, 3, 2), front))
    els.append(element("front_chute_r", (11, 0, 1), (12, 3, 2), front))
    # Window: chamber scene, glass pane, copper frame.
    els.append(element("window_scene", (4, 4, 1.9), (12, 10, 1.9), {"north": "window"}, faces=("north",), uv=[0, 2, 16, 14]))
    els.append(element("window_glass", (4, 4, 1.4), (12, 10, 1.4), {"north": "glass"}, faces=("north",), uv=[4, 6, 12, 12]))
    frame = "casing"
    # The top trim band doubles as the top of the frame.
    els.append(element("window_frame_bottom", (3, 3, 0.5), (13, 4, 1), frame))
    els.append(element("window_frame_left", (3, 4, 0.5), (4, 10, 1), frame))
    els.append(element("window_frame_right", (12, 4, 0.5), (13, 10, 1), frame))
    # Chute mouth and its two flaps angled outward.
    els.append(element("chute_mouth", (5, 0, 1.9), (11, 3, 1.9), {"north": "chute"}, faces=("north",), uv=[0, 4, 16, 16]))
    els.append(element("chute_flap_left", (4.75, 0, -0.5), (5.25, 3, 1.5), "casing",
                       rotation={"angle": 22.5, "axis": "y", "origin": [5, 0, 1.5]}))
    els.append(element("chute_flap_right", (10.75, 0, -0.5), (11.25, 3, 1.5), "casing",
                       rotation={"angle": -22.5, "axis": "y", "origin": [11, 0, 1.5]}))
    # Corner posts and trim bands (copper casing), flush with the block edge.
    for name, (x0, z0) in {"post_nw": (0, 0), "post_ne": (14, 0), "post_sw": (0, 14), "post_se": (14, 14)}.items():
        els.append(element(name, (x0, 0, z0), (x0 + 2, 12, z0 + 2), "casing", cull=["down"]))
    for y0, y1, tag in ((0, 2, "bottom"), (10, 12, "top")):
        els.append(element(f"band_{tag}_south", (2, y0, 15), (14, y1, 16), "casing"))
        els.append(element(f"band_{tag}_east", (15, y0, 2), (16, y1, 14), "casing"))
    els.append(element("band_top_north", (2, 10, 0), (14, 12, 1), "casing"))
    els.append(element("band_bottom_north_l", (2, 0, 0), (4.5, 2, 1), "casing"))
    els.append(element("band_bottom_north_r", (11.5, 0, 0), (14, 2, 1), "casing"))
    # Hopper: collar, narrow ring, wide hollow rim with a dark inside.
    els.append(element("collar", (2, 12, 2), (14, 13, 14), "casing"))
    els.append(element("hopper_ring", (3, 13, 3), (13, 14, 13), {f: "hopper" for f in FACES} | {"up": "dark"}))
    hop = {"north": "hopper", "south": "hopper", "east": "hopper", "west": "hopper", "up": "hopper", "down": "hopper"}
    els.append(element("hopper_rim_north", (1, 14, 1), (15, 16, 3), hop, cull=["up"]))
    els.append(element("hopper_rim_south", (1, 14, 13), (15, 16, 15), hop, cull=["up"]))
    els.append(element("hopper_rim_west", (1, 14, 3), (3, 16, 13), hop, cull=["up"]))
    els.append(element("hopper_rim_east", (13, 14, 3), (15, 16, 13), hop, cull=["up"]))
    # Inner walls of the rim are darker.
    for el in els[-4:]:
        name = el["name"]
        inner = {"hopper_rim_north": "south", "hopper_rim_south": "north", "hopper_rim_west": "east", "hopper_rim_east": "west"}[name]
        el["faces"][inner]["texture"] = "#dark"
    # Stone backing plate on the west side (the viewer's right when looking at the front), rising above the body.
    els.append(element("cog_plate", (0, 0, 2), (1, 14, 14), "plate", cull=["down"]))
    # Static 12-tooth cog on the plate (decorative only). Stacked panes give it thickness.
    cog = element("cog", (-1.5, 1, 1), (-1.5, 15, 15), {"west": "cog", "east": "cog"}, faces=("west", "east"), uv=[0, 0, 16, 16])
    cog["shade"] = False  # keep the cog bright on the dim west side
    els.append(cog)
    els.append(element("cog_hub", (-2.5, 6.5, 6.5), (0, 9.5, 9.5), "casing", uv=[6, 6, 9, 9]))
    els.append(element("cog_axle", (-3, 7.25, 7.25), (-2.5, 8.75, 8.75), "dark", uv=[7, 7, 8.5, 8.5]))
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": True,
        "textures": {
            "particle": "disassembly_delight:block/disassembler_casing",
            "body": "disassembly_delight:block/disassembler_body",
            "casing": "disassembly_delight:block/disassembler_casing",
            "hopper": "disassembly_delight:block/disassembler_hopper",
            "dark": "disassembly_delight:block/disassembler_dark",
            "window": "disassembly_delight:block/disassembler_window",
            "chute": "disassembly_delight:block/disassembler_chute",
            "cog": "disassembly_delight:block/disassembler_cog",
            "glass": "minecraft:block/glass",
            "plate": "minecraft:block/smooth_stone",
        },
        "elements": els,
    }


if __name__ == "__main__":
    model = build()
    OUT.write_text(json.dumps(model, indent=2) + "\n")
    print("wrote", OUT, len(model["elements"]), "elements")

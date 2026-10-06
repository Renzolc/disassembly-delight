#!/usr/bin/env python3
"""Build disassembly_delight:full_uncraft recipes from original crafts.

The cutting-board recipes they start from live in Farmer's Delight Tweaks
(data/fd_storage_compat/recipe/cutting); point FDT_ROOT at that checkout
(default: ../fd-storage-compat next to this repo). Cutting-board JSON stays
partial. These recipes are only consumed by the Disassembler Upgrade.
"""
from __future__ import annotations

import collections
import json
import os
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FDT_ROOT = Path(os.environ.get("FDT_ROOT", ROOT.parent / "fd-storage-compat"))
CUT_ROOT = FDT_ROOT / "src/main/resources/data/fd_storage_compat/recipe/cutting"
OUT_ROOT = ROOT / "src/main/resources/data/disassembly_delight/recipe/full_uncraft"
# Hand-written recipes in OUT_ROOT that are not generated from a cutting recipe.
KEEP = {"disassembler_upgrade.json"}

JARS = [
    ROOT / "libs/sophisticatedbackpacks.jar",
    ROOT / "libs/sophisticatedcore.jar",
    ROOT / "libs/FarmersDelight.jar",
    Path("/tmp/modjars/create.jar"),
    Path("/workspace/renzo-mods-scan/jars/supplementaries-1.21.1-3.9.9-neoforge.jar"),
    Path("/workspace/renzo-mods-scan/jars/handcrafted-neoforge-1.21.1-4.0.3.jar"),
    Path("/workspace/renzo-mods-scan/jars/Quark-4.1-485.jar"),
    Path("/workspace/renzo-mods-scan/jars/culturaldelights-1.21.1-0.18.1.jar"),
    Path("/workspace/renzo-mods-scan/jars/minersdelight-1.21.1-1.4.5.jar"),
    Path("/workspace/renzo-mods-scan/jars/moredelight-26.05.20a-1.21-neoforge.jar"),
    Path("/workspace/ss-advanced-crafting/libs/sophisticatedstorage-1.21.1-1.5.91.2127.jar"),
]

VANILLA_TAG = {
    "c:ingots/iron": "minecraft:iron_ingot",
    "c:ingots/gold": "minecraft:gold_ingot",
    "c:ingots/copper": "minecraft:copper_ingot",
    "c:ingots/netherite": "minecraft:netherite_ingot",
    "c:nuggets/iron": "minecraft:iron_nugget",
    "c:nuggets/gold": "minecraft:gold_nugget",
    "c:nuggets/copper": "minecraft:copper_nugget",
    "c:storage_blocks/iron": "minecraft:iron_block",
    "c:storage_blocks/gold": "minecraft:gold_block",
    "c:storage_blocks/copper": "minecraft:copper_block",
    "c:storage_blocks/redstone": "minecraft:redstone_block",
    "c:gems/diamond": "minecraft:diamond",
    "c:gems/emerald": "minecraft:emerald",
    "c:gems/lapis": "minecraft:lapis_lazuli",
    "c:gems/quartz": "minecraft:quartz",
    "c:gems/amethyst": "minecraft:amethyst_shard",
    "c:dusts/redstone": "minecraft:redstone",
    "c:dusts/glowstone": "minecraft:glowstone_dust",
    "c:rods/wooden": "minecraft:stick",
    "c:rods/blaze": "minecraft:blaze_rod",
    "c:strings": "minecraft:string",
    "c:leathers": "minecraft:leather",
    "c:chests": "minecraft:chest",
    "c:chests/wooden": "minecraft:chest",
    "c:ender_pearls": "minecraft:ender_pearl",
    "c:slime_balls": "minecraft:slime_ball",
    "c:slimeballs": "minecraft:slime_ball",
    "c:feathers": "minecraft:feather",
    "c:eggs": "minecraft:egg",
    "c:glass_blocks": "minecraft:glass",
    "c:glass_panes": "minecraft:glass_pane",
    "c:glass_panes/colorless": "minecraft:glass_pane",
    "c:cobblestones": "minecraft:cobblestone",
    "c:stones": "minecraft:stone",
    "c:stripped_logs": "minecraft:stripped_oak_log",
    "c:stripped_woods": "minecraft:stripped_oak_wood",
    "c:logs": "minecraft:oak_log",
    "c:planks": "minecraft:oak_planks",
    "c:plates/gold": "create:golden_sheet",
    "c:plates/iron": "create:iron_sheet",
    "c:plates/copper": "create:copper_sheet",
    "c:plates/brass": "create:brass_sheet",
    "c:obsidians": "minecraft:obsidian",
    "c:sands": "minecraft:sand",
    "c:gravels": "minecraft:gravel",
    "c:crops/wheat": "minecraft:wheat",
    "c:gunpowders": "minecraft:gunpowder",
    "c:bones": "minecraft:bone",
    "c:dyes/white": "minecraft:white_dye",
    "c:dyes/orange": "minecraft:orange_dye",
    "c:dyes/magenta": "minecraft:magenta_dye",
    "c:dyes/light_blue": "minecraft:light_blue_dye",
    "c:dyes/yellow": "minecraft:yellow_dye",
    "c:dyes/lime": "minecraft:lime_dye",
    "c:dyes/pink": "minecraft:pink_dye",
    "c:dyes/gray": "minecraft:gray_dye",
    "c:dyes/light_gray": "minecraft:light_gray_dye",
    "c:dyes/cyan": "minecraft:cyan_dye",
    "c:dyes/purple": "minecraft:purple_dye",
    "c:dyes/blue": "minecraft:blue_dye",
    "c:dyes/brown": "minecraft:brown_dye",
    "c:dyes/green": "minecraft:green_dye",
    "c:dyes/red": "minecraft:red_dye",
    "c:dyes/black": "minecraft:black_dye",
    "c:nuggets/brass": "create:brass_nugget",
    "c:nuggets/zinc": "create:zinc_nugget",
    "c:netherracks": "minecraft:netherrack",
    "c:sands/red": "minecraft:red_sand",
    "c:sands/colorless": "minecraft:sand",
    "c:nether_stars": "minecraft:nether_star",
    "minecraft:wool": "minecraft:white_wool",
    "minecraft:stone_crafting_materials": "minecraft:cobblestone",
    "minecraft:planks": "minecraft:oak_planks",
    "minecraft:wooden_slabs": "minecraft:oak_slab",
    "minecraft:logs": "minecraft:oak_log",
}

raw_tags: dict[str, list[str]] = collections.defaultdict(list)


def add_tag_file(path: str, text: str) -> None:
    parts = path.split("/")
    if "tags" not in parts:
        return
    i = parts.index("tags")
    if i + 1 >= len(parts) or parts[i + 1] != "item" or i == 0:
        return
    ns = parts[i - 1]
    rel = "/".join(parts[i + 2 :]).removesuffix(".json")
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return
    for v in data.get("values", []):
        if isinstance(v, str):
            raw_tags[f"{ns}:{rel}"].append(v)
        elif isinstance(v, dict) and isinstance(v.get("id"), str):
            raw_tags[f"{ns}:{rel}"].append(v["id"])


resolved_cache: dict[str, set[str]] = {}


def resolve_tag(tag: str, stack: tuple[str, ...] = ()) -> set[str]:
    if tag in stack:
        return set()
    if not stack and tag in resolved_cache:
        return resolved_cache[tag]
    items: set[str] = set()
    for entry in raw_tags.get(tag, []):
        if entry.startswith("#"):
            items |= resolve_tag(entry[1:], stack + (tag,))
        else:
            items.add(entry)
    if not stack:
        resolved_cache[tag] = items
    return items


def tag_matches(tag: str, item_id: str) -> bool:
    if item_id in resolve_tag(tag):
        return True
    if ":" not in item_id or ":" not in tag:
        return False
    path = item_id.split(":", 1)[1]
    tpath = tag.split(":", 1)[1]
    if "/" in tpath:
        cat, name = tpath.split("/", 1)
        if cat == "ingots" and path == f"{name}_ingot":
            return True
        if cat == "nuggets" and path == f"{name}_nugget":
            return True
        if cat == "storage_blocks" and path == f"{name}_block":
            return True
        if cat == "gems" and path in {name, f"{name}_gem"}:
            return True
        if cat == "dusts" and path in {name, f"{name}_dust"}:
            return True
        if cat == "rods" and name == "wooden" and path == "stick":
            return True
        if cat == "rods" and path == f"{name}_rod":
            return True
        if cat == "dyes" and path == f"{name}_dye":
            return True
        if cat == "plates" and path == f"{name}_sheet":
            return True
        if cat == "chests" and path == "chest":
            return True
    mapping = {
        "strings": "string",
        "leathers": "leather",
        "slime_balls": "slime_ball",
        "slimeballs": "slime_ball",
        "ender_pearls": "ender_pearl",
        "feathers": "feather",
        "eggs": "egg",
        "gunpowders": "gunpowder",
        "bones": "bone",
    }
    if mapping.get(tpath) == path:
        return True
    if tpath in {"glass_blocks", "glass_blocks/colorless"} and path == "glass":
        return True
    if tpath.startswith("glass_panes") and path.endswith("glass_pane"):
        return True
    if tpath == "stripped_logs" and path.startswith("stripped_") and path.endswith("_log"):
        return True
    if tpath == "stripped_woods" and path.startswith("stripped_") and path.endswith("_wood"):
        return True
    if tpath in {"logs", "logs_that_burn"} and path.endswith("_log"):
        return True
    if tpath == "planks" and path.endswith("_planks"):
        return True
    if tpath == "cobblestones" and "cobblestone" in path:
        return True
    if tpath == "stones" and path == "stone":
        return True
    if tpath == "wool" and path.endswith("_wool"):
        return True
    if tpath == "stone_crafting_materials" and path in {"cobblestone", "cobbled_deepslate", "blackstone", "stone"}:
        return True
    if tpath == "planks" and path.endswith("_planks"):
        return True
    if VANILLA_TAG.get(tag) == item_id:
        return True
    return False


def ing_entry(obj) -> list[tuple]:
    """Slots as (kind, payload, count). kind is item|tag|alts."""
    if obj is None:
        return []
    if isinstance(obj, str):
        if obj.startswith("#"):
            return [("tag", obj[1:], 1)]
        return [("item", obj, 1)]
    if isinstance(obj, list):
        alts = []
        for entry in obj:
            alts.extend(ing_entry(entry))
        return [("alts", alts, 1)] if alts else []
    if isinstance(obj, dict):
        count = int(obj.get("count", 1) or 1)
        if isinstance(obj.get("item"), str):
            return [("item", obj["item"], count)]
        if isinstance(obj.get("tag"), str):
            return [("tag", obj["tag"], count)]
        if isinstance(obj.get("id"), str) and "tag" not in obj and "item" not in obj:
            return [("item", obj["id"], count)]
        if "ingredient" in obj:
            inner = ing_entry(obj["ingredient"])
            return [(k, p, c * count) for k, p, c in inner]
    return []


def result_of(recipe: dict) -> list[tuple[str, int]]:
    outs = []
    if "result" in recipe:
        result = recipe["result"]
        if isinstance(result, str):
            outs.append((result, 1))
        elif isinstance(result, dict):
            rid = result.get("id") or result.get("item")
            if isinstance(rid, dict):
                rid = rid.get("id") or rid.get("item")
            if isinstance(rid, str):
                outs.append((rid, int(result.get("count", 1) or 1)))
    results = recipe.get("results")
    if isinstance(results, list):
        for result in results:
            if not isinstance(result, dict):
                continue
            rid = result.get("id") or result.get("item")
            if isinstance(rid, dict):
                rid = rid.get("id") or rid.get("item")
            if isinstance(rid, str):
                outs.append((rid, int(result.get("count", 1) or 1)))
    return outs


def recipe_slots(recipe: dict) -> list[tuple] | None:
    rtype = recipe.get("type", "")
    if rtype == "create:sequenced_assembly":
        loops = int(recipe.get("loops", 1) or 1)
        slots = ing_entry(recipe.get("ingredient"))
        for step in recipe.get("sequence", []):
            ings = step.get("ingredients") or []
            if len(ings) >= 2:
                for kind, payload, count in ing_entry(ings[1]):
                    slots.append((kind, payload, count * loops))
        return slots or None
    if "smithing" in rtype:
        slots = []
        for key in ("template", "base", "addition"):
            if key in recipe:
                slots.extend(ing_entry(recipe[key]))
        return slots or None
    if "pattern" in recipe and "key" in recipe:
        slots = []
        key = recipe["key"]
        for row in recipe["pattern"]:
            if not isinstance(row, str):
                return None
            for ch in row:
                if ch == " ":
                    continue
                slots.extend(ing_entry(key.get(ch)))
        return slots or None
    ingredients = recipe.get("ingredients")
    if isinstance(ingredients, list) and ingredients:
        slots = []
        for ing in ingredients:
            slots.extend(ing_entry(ing))
        return slots or None
    return None


def slot_matches(slot, item_id: str) -> bool:
    kind, payload, _count = slot
    if kind == "item":
        return payload == item_id
    if kind == "tag":
        return tag_matches(payload, item_id)
    if kind == "alts":
        return any(slot_matches(sub, item_id) for sub in payload)
    return False


def slot_count(slot) -> int:
    return slot[2] if slot[0] != "alts" else 1


def choose_item(slot, cutting_counts: dict[str, int]) -> str | None:
    kind, payload, _count = slot
    if kind == "item":
        return payload
    if kind == "alts":
        for sub in payload:
            chosen = choose_item(sub, cutting_counts)
            if chosen and chosen in cutting_counts:
                return chosen
        for sub in payload:
            chosen = choose_item(sub, cutting_counts)
            if chosen:
                return chosen
        return None
    # tag
    for item_id in cutting_counts:
        if tag_matches(payload, item_id):
            return item_id
    if payload in VANILLA_TAG:
        return VANILLA_TAG[payload]
    resolved = sorted(resolve_tag(payload))
    minecraft = [i for i in resolved if i.startswith("minecraft:")]
    if minecraft:
        return minecraft[0]
    return resolved[0] if resolved else None


def materialize(recipe, cutting_counts: dict[str, int]) -> list[tuple[str, int]] | None:
    totals: dict[str, int] = collections.Counter()
    for slot in recipe["slots"]:
        item = choose_item(slot, cutting_counts)
        if not item:
            return None
        totals[item] += slot_count(slot)
    return sorted(totals.items())


def score_recipe(recipe, cutting_counts: dict[str, int]) -> int:
    score = 0
    for item_id, need in cutting_counts.items():
        have = 0
        for slot in recipe["slots"]:
            if slot_matches(slot, item_id):
                have += slot_count(slot)
        if have <= 0:
            continue
        score += 100
        if have >= need:
            score += 50
        score += min(have, need)
    return score


def synth(item: str, slots, out_count=1, source="synthetic"):
    return {"id": source, "type": "synthetic", "slots": slots, "out_count": out_count, "result": item}


def item_slot(item: str, count: int = 1):
    return ("item", item, count)


def tag_slot(tag: str, count: int = 1):
    return ("tag", tag, count)


def add_synth(by_result):
    # Sophisticated Storage pump upgrades mirror the backpack crafts but are not shipped as JSON.
    base = "sophisticatedstorage:upgrade_base"
    by_result["sophisticatedstorage:pump_upgrade"].append(synth(
        "sophisticatedstorage:pump_upgrade",
        [tag_slot("c:glass_blocks", 4), item_slot("minecraft:bucket", 2), item_slot("minecraft:piston"),
         item_slot("minecraft:sticky_piston"), item_slot(base)],
        source="ss-pump-mirror",
    ))
    by_result["sophisticatedstorage:advanced_pump_upgrade"].append(synth(
        "sophisticatedstorage:advanced_pump_upgrade",
        [tag_slot("c:gems/diamond", 2), item_slot("minecraft:dispenser"), tag_slot("c:ingots/gold", 2),
         item_slot("sophisticatedstorage:pump_upgrade"), tag_slot("c:dusts/redstone", 3)],
        source="ss-advanced-pump-mirror",
    ))
    by_result["sophisticatedstorage:xp_pump_upgrade"].append(synth(
        "sophisticatedstorage:xp_pump_upgrade",
        [tag_slot("c:dusts/redstone", 4), item_slot("minecraft:ender_eye", 2),
         item_slot("sophisticatedstorage:advanced_pump_upgrade"), item_slot("minecraft:experience_bottle", 2)],
        source="ss-xp-pump-mirror",
    ))


def load_index():
    by_result = collections.defaultdict(list)
    for jar_path in JARS:
        if not jar_path.exists():
            raise SystemExit(f"missing jar {jar_path}")
        with zipfile.ZipFile(jar_path) as jar:
            for name in jar.namelist():
                if name.endswith(".json") and "/tags/item/" in name:
                    add_tag_file(name, jar.read(name).decode("utf-8", "replace"))
            for name in jar.namelist():
                if "/recipe/" not in name or not name.endswith(".json"):
                    continue
                try:
                    data = json.loads(jar.read(name).decode("utf-8", "replace"))
                except json.JSONDecodeError:
                    continue
                if not isinstance(data, dict):
                    continue
                slots = recipe_slots(data)
                outs = result_of(data)
                if not slots or not outs:
                    continue
                for rid, count in outs:
                    by_result[rid].append({
                        "id": f"{jar_path.name}:{name}",
                        "type": data.get("type", ""),
                        "slots": slots,
                        "out_count": max(1, count),
                        "result": rid,
                    })
    add_synth(by_result)
    return by_result


def cutting_results(data: dict) -> list[tuple[str, int]]:
    results = []
    for entry in data.get("result", []):
        item = entry.get("item") or {}
        item_id = item.get("id")
        if item_id:
            results.append((item_id, int(item.get("count", 1) or 1)))
    return results


def pick(by_result, item: str, cutting_counts: dict[str, int]):
    cands = by_result.get(item, [])
    if not cands:
        return None
    ranked = []
    for recipe in cands:
        ranked.append((
            score_recipe(recipe, cutting_counts),
            0 if recipe["out_count"] == 1 else 1,
            sum(slot_count(s) for s in recipe["slots"]),
            recipe["id"],
            recipe,
        ))
    ranked.sort(key=lambda row: (-row[0], row[1], row[2], row[3]))
    return ranked[0]


def main() -> None:
    if not CUT_ROOT.is_dir():
        raise SystemExit(f"cutting recipes not found at {CUT_ROOT} (set FDT_ROOT)")
    by_result = load_index()
    if OUT_ROOT.exists():
        for old in OUT_ROOT.rglob("*.json"):
            if old.relative_to(OUT_ROOT).as_posix() not in KEEP:
                old.unlink()
    written = 0
    fallback = []
    unresolved = []
    consume_gt1 = 0
    increased = 0
    for path in sorted(CUT_ROOT.rglob("*.json")):
        data = json.loads(path.read_text())
        ingredient = data["ingredients"][0]
        item = ingredient.get("item")
        if not item:
            continue
        results = cutting_results(data)
        cutting_counts: dict[str, int] = collections.Counter()
        for item_id, count in results:
            cutting_counts[item_id] += count
        picked = pick(by_result, item, cutting_counts)
        rel = path.relative_to(CUT_ROOT)
        if picked is None:
            mats = results
            consume = 1
            source = "cutting-fallback"
            fallback.append(item)
        else:
            score, _one, _slots, source, recipe = picked
            mats = materialize(recipe, cutting_counts)
            if not mats:
                unresolved.append((item, source))
                mats = results
                consume = 1
                source = "unresolved-fallback"
            else:
                consume = recipe["out_count"]
                # Never return less of a shared ingredient than the cutting board
                # would over the same number of inputs. Craft-only ingredients stay.
                merged = dict(mats)
                for item_id, cutting_count in cutting_counts.items():
                    if item_id in merged:
                        merged[item_id] = max(merged[item_id], cutting_count * consume)
                mats = sorted(merged.items())
                if sum(cutting_counts.values()) < sum(merged.values()):
                    increased += 1
        if consume > 1:
            consume_gt1 += 1
        body = {
            "type": "disassembly_delight:full_uncraft",
            "input": {"item": item},
            "consume": consume,
            "results": [{"id": item_id, "count": count} for item_id, count in mats],
        }
        if data.get("neoforge:conditions"):
            body = {"neoforge:conditions": data["neoforge:conditions"], **body}
        out = OUT_ROOT / rel
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(body, indent=2) + "\n")
        written += 1
    print(f"wrote {written} full_uncraft recipes")
    print(f"fallback (no craft): {len(fallback)}")
    for item in fallback:
        print("  ", item)
    print(f"unresolved materialize: {len(unresolved)}")
    for row in unresolved:
        print("  ", row)
    print(f"consume>1: {consume_gt1}  likely fuller than cutting: {increased}")


if __name__ == "__main__":
    main()

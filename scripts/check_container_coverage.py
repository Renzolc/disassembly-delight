#!/usr/bin/env python3
"""Rule C coverage check: every item in the modpack that can hold items must be either emptied by a content
reader or forced to pass through. Nothing may be disassembled with its contents still inside.

It enumerates container candidates from the compatibility scan and the pack's jars:
  * proposals.csv rows whose notes/basis mention stored contents, plus item ids that look like containers
    (shulker boxes, bundles, backpacks, toolboxes, packages, quivers, sacks, safes, jars, chests, barrels,
    cabinets, baskets, block-entity blocks, contraptions, sandwiches, ...);
  * block loot tables that copy an item-holding component (copy_components / set_contents / copy_nbt /
    dynamic contents) into the dropped item;
  * container item tags (shulker boxes, bundles, toolboxes, packages, backpacks, chests, barrels, sacks...).

Then it asserts, against src/main/resources/disassembly_delight/container_rules.json:
  1. every candidate id matches an entry in "items" (the audited manifest; wildcards allowed);
  2. every reader kind is known and every component id it names exists in some jar (catches typos);
  3. every component that a loot table copies onto an item is classified (reader, ghost, pass-through, neutral);
  4. every exact manifest id is a real item (in proposals.csv or a jar's assets), unless marked as an alias.

Usage: python3 scripts/check_container_coverage.py [--scan /workspace/fd-disassemble-scan]
Exit code 0 = all covered, 1 = problems found, 2 = scan data missing.
"""
import argparse
import csv
import fnmatch
import glob
import json
import os
import re
import sys
import zipfile
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(ROOT, "src/main/resources/disassembly_delight/container_rules.json")
KNOWN_KINDS = {"item_container", "bundle", "charged_projectiles", "item_handler", "single_stack", "stack_list", "item_id_list"}

# Item-id paths that look like containers. Over-matching is fine: each hit must be audited in the manifest.
CONTAINER_ID = re.compile(
    r"(shulker_box|bundle$|backpack$|toolbox$|package(_\w+)?$|quiver$|(^|_)sack$|satchel|pouch|(^|_)bag$|(^|_)safe$|(^|:)jar$"
    r"|present(_\w+)?$|basket$|(^|_)urn$|chest$|barrel(_\d)?$|hopper$|dispenser$|dropper$|furnace$|smoker$|brewing_stand$"
    r"|crafter$|campfire$|chiseled_bookshelf$|lectern$|jukebox$|decorated_pot$|beehive$|bee_nest$|cooking_pot$|copper_pot$"
    r"|skillet$|keg$|blazier|(^|_)shelf$|pedestal$|cabinet$|vault$|depot$|basin$|minecart|chest_boat$|chest_raft$|wand$"
    r"|fishing_rod$|crossbow$|item_frame$|armor_stand$|contraption|^sandwich$|cage$|goblet$|hourglass$|notice_board$|_crate$|^crate$)"
)
CONTENT_TEXT = re.compile(r"stored contents|StoredContents|contents? (component|first)|holds? items|stores? items|Rule C", re.I)
CONTAINER_TAG = re.compile(
    r"^(minecraft:shulker_boxes|c:shulker_boxes|minecraft:bundles|c:bundles|create:toolboxes|create:packages|"
    r"sophisticatedstorage:all_storage|c:chests(/.*)?|c:barrels(/.*)?|supplementaries:sacks|chipped:barrel|chipped:chest|"
    r".*:backpacks|.*:quivers|minecraft:chest_boats)$"
)
LOOT_FUNCTIONS = {"set_contents", "copy_nbt", "copy_custom_data"}


def load_rules():
    with open(RULES, encoding="utf-8") as f:
        return json.load(f)


def manifest_matchers(items):
    """'ns:a|b|other:c' -> fnmatch patterns; an alternative without ':' reuses the previous namespace."""
    out = []
    for key in items:
        ns = None
        for alt in key.split("|"):
            if ":" in alt:
                ns = alt.split(":", 1)[0]
                out.append((alt, key))
            elif ns:
                out.append((f"{ns}:{alt}", key))
    return out


def covered(item_id, matchers):
    for pattern, key in matchers:
        if fnmatch.fnmatchcase(item_id, pattern):
            return key
    return None


def walk(node, fn):
    if isinstance(node, dict):
        fn(node)
        for v in node.values():
            walk(v, fn)
    elif isinstance(node, list):
        for v in node:
            walk(v, fn)


def scan_jars(scan, item_holding):
    """Returns (loot candidates, tag candidates, copied components, jar strings per namespace, asset item ids)."""
    loot = defaultdict(set)
    tags = defaultdict(set)
    copied = defaultdict(set)
    jar_text = defaultdict(bytearray)
    asset_items = set()
    jars = sorted(glob.glob(os.path.join(scan, "jars", "*.jar"))) + sorted(glob.glob(os.path.join(scan, "vanilla", "*.jar")))
    for jar in jars:
        try:
            z = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            print(f"warning: unreadable jar {jar}")
            continue
        namespaces = set()
        for name in z.namelist():
            m = re.match(r"(?:data|assets)/([a-z0-9_.-]+)/", name)
            if m:
                namespaces.add(m.group(1))
            m = re.match(r"assets/([a-z0-9_.-]+)/models/item/([a-z0-9_/.-]+)\.json$", name)
            if m:
                asset_items.add(f"{m.group(1)}:{m.group(2)}")
            m = re.match(r"data/([a-z0-9_.-]+)/loot_tables?/blocks/([a-z0-9_/.-]+)\.json$", name)
            if m:
                block = f"{m.group(1)}:{m.group(2)}"
                try:
                    table = json.loads(z.read(name))
                except ValueError:
                    continue

                def visit(node, block=block):
                    fn = str(node.get("function", "")).replace("minecraft:", "")
                    if fn == "copy_components":
                        for comp in node.get("include", []):
                            copied[comp].add(block)
                            if comp in item_holding:
                                loot[block].add(comp)
                    elif fn in LOOT_FUNCTIONS:
                        loot[block].add(fn)
                    if node.get("type") == "minecraft:dynamic" and str(node.get("name", "")).endswith("contents"):
                        loot[block].add("dynamic contents")

                walk(table, visit)
            m = re.match(r"data/([a-z0-9_.-]+)/tags/items?/([a-z0-9_/.-]+)\.json$", name)
            if m and CONTAINER_TAG.match(f"{m.group(1)}:{m.group(2)}"):
                try:
                    values = json.loads(z.read(name)).get("values", [])
                except ValueError:
                    continue
                for v in values:
                    v = v if isinstance(v, str) else v.get("id", "")
                    if v and not v.startswith("#"):
                        tags[v if ":" in v else "minecraft:" + v].add(f"{m.group(1)}:{m.group(2)}")
        # Class constant pools for component-id existence checks.
        blob = bytearray()
        for name in z.namelist():
            if name.endswith(".class"):
                blob += z.read(name)
        for ns in namespaces:
            jar_text[ns] += blob
    return loot, tags, copied, jar_text, asset_items


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scan", default=os.environ.get("FD_DISASSEMBLE_SCAN", "/workspace/fd-disassemble-scan"))
    args = parser.parse_args()
    proposals_path = os.path.join(args.scan, "proposals.csv")
    if not os.path.exists(proposals_path) or not glob.glob(os.path.join(args.scan, "jars", "*.jar")):
        print(f"scan data not found under {args.scan} (needs proposals.csv and jars/)")
        return 2

    rules = load_rules()
    problems = []
    readers = rules.get("readers", {})
    ghost = set(rules.get("ghost_components", []))
    passing = set(rules.get("pass_through_if_present", []))
    neutral = set(rules.get("neutral_components", []))
    aliases = set(rules.get("scan_aliases", []))
    items = rules.get("items", {})
    matchers = manifest_matchers(items)
    special = {"minecraft:block_entity_data", "minecraft:custom_data"}

    for comp, reader in readers.items():
        kind = reader.split(":", 1)[0]
        if kind not in KNOWN_KINDS:
            problems.append(f"reader for {comp} has unknown kind '{kind}'")
    for comp in set(readers) & (ghost | passing):
        problems.append(f"{comp} is both read and ghost/pass-through")
    for item in rules.get("pass_through_items", []):
        if not covered(item, matchers):
            problems.append(f"pass_through_items entry {item} is not in the manifest")

    item_holding = set(readers) | passing | {"minecraft:container", "minecraft:bundle_contents", "minecraft:container_loot"}
    loot, tags, copied, jar_text, asset_items = scan_jars(args.scan, item_holding)

    # Component ids must exist in a jar of their namespace (vanilla ids are checked against the client jar).
    for comp in sorted(set(readers) | ghost | passing | neutral):
        ns, path = comp.split(":", 1)
        blob = jar_text.get(ns)
        if blob is None:
            problems.append(f"component {comp}: no jar provides namespace '{ns}'")
        elif path.encode() not in blob:
            problems.append(f"component {comp}: id '{path}' not found in any '{ns}' jar")

    for comp, blocks in sorted(copied.items()):
        if comp not in readers and comp not in ghost and comp not in passing and comp not in neutral and comp not in special:
            problems.append(f"loot tables copy unclassified component {comp} onto {sorted(blocks)[:4]}")

    with open(proposals_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    known_ids = {r["item_id"] for r in rows} | asset_items

    candidates = defaultdict(set)
    for r in rows:
        item = r["item_id"]
        path = item.split(":", 1)[-1]
        if CONTAINER_ID.search(path):
            candidates[item].add("id looks like a container")
        if CONTENT_TEXT.search(r.get("notes", "") + " " + r.get("basis", "")):
            candidates[item].add("scan notes mention contents")
    for block, why in loot.items():
        candidates[block].add("loot copies " + ",".join(sorted(why)))
    for item, why in tags.items():
        candidates[item].add("tag " + ",".join(sorted(why)))

    handled = defaultdict(list)
    for item in sorted(candidates):
        key = covered(item, matchers)
        if key is None:
            problems.append(f"container candidate {item} ({'; '.join(sorted(candidates[item]))}) is not handled in the manifest")
        else:
            handled[items[key]].append(item)

    for key in items:
        if "|" in key or "*" in key:
            continue
        if key not in known_ids and key not in aliases:
            problems.append(f"manifest id {key} is not a known item (typo?)")

    print(f"container candidates: {len(candidates)} (proposals rows: {len(rows)}, loot-copy blocks: {len(loot)}, tagged: {len(tags)})")
    print(f"manifest entries: {len(items)}; readers: {len(readers)}; ghost: {len(ghost)}; pass-through components: {len(passing)}")
    if problems:
        print(f"\nFAILED: {len(problems)} problem(s)")
        for p in problems:
            print("  - " + p)
        return 1
    print("OK: every container candidate is emptied by a reader or passes through unchanged.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

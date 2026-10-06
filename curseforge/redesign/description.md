![Disassembly Delight by Renzo](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/header-disassembly-delight.png)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

[![GitHub](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/badge-github-disassembly-delight.png)](https://github.com/Renzolc/disassembly-delight) [![CurseForge](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/badge-curseforge-disassembly-delight.png)](https://www.curseforge.com/minecraft/mc-mods/disassembly-delight) [![Issues](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/badge-issues-disassembly-delight.png)](https://github.com/Renzolc/disassembly-delight/issues)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

## 📖 ABOUT

**Disassembly Delight** is a Farmer's Delight add-on for NeoForge 1.21.1 that takes things apart. Its copper **Disassembly Table** works like an automatic cutting board with the knife, axe and pickaxe built in: hopper items in at the top, collect the pieces at the bottom. 🔧

With Sophisticated Backpacks installed, the **Disassembly Table Upgrade** puts the full crafting ingredients of an item straight into your backpack, and takes tiered backpacks down one tier at a time with everything inside moved along. 🎒

Coming from **Farmer's Delight Tweaks**? The table is the old **Decrafter** and the upgrade is the old **Decrafter Upgrade**. They moved here in FD Tweaks 1.1.0, and your existing machines carry over. 🔄

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

## 📚 FEATURES

- 🪵 **Wood and beds:** Planks become 2 matching slabs, slabs become a stick, and beds become 3 wool plus 3 oak planks.
- 💀 **Mob heads to spawn eggs:** Vanilla heads, and modded heads that have a matching spawn egg.
- 🔪 **Every cutting-board recipe:** Runs all Farmer's Delight cutting-board recipes and gives every listed result, with no chance rolls.
- 🔁 **Reverse crafting:** Anything else is uncrafted from its crafting recipe. Damaged tools and armor are skipped.
- 🚰 **Never jams:** Items it can't take apart pass straight through. One operation per second, with a comparator signal.
- 🎒 **Full returns in your backpack:** The upgrade gives full ingredient counts and previews the result in its tab.
- 💎 **The backpack chain:** Netherite → diamond → gold → iron → copper → backpack, each step returning that tier's materials, with the contents carried down.
- 📦 **Nothing inside gets lost:** Shulker boxes, bundles, Sophisticated storage, Create toolboxes, Supplementaries containers, cooking pots and more are emptied first. Anything that can't be emptied safely passes through whole.
- 🧩 **Datapack friendly:** Full returns come from `disassembly_delight:full_uncraft` recipes that you can add to or change.

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

## 📷 MEDIA

![The Disassembly Table at work in a base](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/media-0-in-your-base.png)

![What the Disassembly Table takes apart](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/media-1-takes-anything-apart.png)

![Recipes for the Disassembly Table and the Disassembly Table Upgrade](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/media-2-crafting.png)

![The backpack chain](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/media-3-backpack-chain.png)

![Migrating from FD Tweaks](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/media-4-migration.png)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

## 📦 INSTALLATION

**⏩ INSTALL ON BOTH CLIENT AND SERVER (NEOFORGE 1.21.1)**

**⏩ REQUIRES [FARMER'S DELIGHT](https://www.curseforge.com/minecraft/mc-mods/farmers-delight) 1.2 OR NEWER**

**⏩ OPTIONAL: [SOPHISTICATED BACKPACKS](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) 3.20 OR NEWER, FOR THE UPGRADE**

**⏩ [FARMER'S DELIGHT TWEAKS](https://www.curseforge.com/minecraft/mc-mods/farmers-delight-tweaks) IS NOT REQUIRED**

Upgrading a world that has Decrafters? Back it up, then install Disassembly Delight **together with** Farmer's Delight Tweaks 1.1.0 or newer before you open it. Placed Decrafters, their items, and Decrafter Upgrades in backpacks become Disassembly Tables and Upgrades automatically. Without this mod, FD Tweaks 1.1.0 deletes them. Disassembly Delight won't start next to FD Tweaks 1.0.x.

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

## ⚙️ CONFIGURATION

There is no config file; nothing is created in `.minecraft/config/`. Behavior is data-driven: full returns and the backpack chain are `disassembly_delight:full_uncraft` recipes in `data/disassembly_delight/recipe/full_uncraft/`, which a datapack can add to or override. The container rules ship inside the jar at `disassembly_delight/container_rules.json`.

The recipe format, the full list of emptied containers, and how the migration works are in the [`README.md` on GitHub](https://github.com/Renzolc/disassembly-delight#readme).

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

Renzo is actively working on Disassembly Delight and welcomes feedback, bug reports and ideas in the comments. 💬

| [![Farmer's Delight Tweaks](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/card-farmers-delight-tweaks.png)](https://www.curseforge.com/minecraft/mc-mods/farmers-delight-tweaks) | [![Sophisticated Advanced Crafting](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/card-sophisticated-advanced-crafting.png)](https://www.curseforge.com/minecraft/mc-mods/sophisticated-advanced-crafting) |
|:---:|:---:|
| [![Create Cart Loot](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/card-create-cart-loot.png)](https://www.curseforge.com/minecraft/mc-mods/create-cart-loot) | [![Enhanced Mob Spawners NeoForge](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/card-enhanced-mob-spawners-neoforge.png)](https://www.curseforge.com/minecraft/mc-mods/enhanced-mob-spawners-neoforge) |
| [![Mo' Enchantments Loot Compat](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/card-mo-enchantments-loot-compat.png)](https://www.curseforge.com/minecraft/mc-mods/mo-enchantments-loot-compat) |   |

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

[![Made by Renzo](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/footer-renzo.png)](https://github.com/Renzolc)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/divider-disassembly-delight.png)

© 2026 Renzo. Released under the MIT License.

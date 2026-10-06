![Create Cart Loot by Renzo](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/header-create-cart-loot.png)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

[![CurseForge](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/badge-curseforge-create-cart-loot.png)](https://www.curseforge.com/minecraft/mc-mods/create-cart-loot)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

## 📖 ABOUT

**Create Cart Loot** hides a pre-built Create drill cart in chest loot on NeoForge 1.21.1. Open the right chest and you may find a complete minecart contraption, ready to place on a rail. ⛏️

It ships one template, the **7x7 Drill** by Thecpt from CreateMod: 42 mechanical drills on a furnace minecart, with a seat, barrel storage, deployers and glowstone lights, all Create and vanilla blocks. 🚂

Out of the box only **abandoned mineshaft** chests can drop it, at a **20%** chance, so it stays a rare find. Other chest families have their own switches in the config, ready for addons that add more carts. 🎲

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

## 📚 FEATURES

- 🚂 **A real contraption:** The loot is a Create furnace minecart contraption item, not a schematic. Place it on a rail to deploy the whole drill cart.
- ⛏️ **7x7 drill:** 42 mechanical drills plus a plough, deployers, a seat, a barrel and lights, built by Thecpt.
- 🗺️ **Mineshaft loot:** Abandoned mineshaft chests have a 20% chance to hold the cart.
- 🔧 **Per-chest settings:** Villages, dungeons, shipwrecks, buried treasure, temples, strongholds, the Nether, the End, ruined portals and rustic chests each have a toggle and a chance. They are off by default, and in this version they also need an addon that maps them to a cart.
- 🖥️ **In-game config:** Change the mineshaft chance or turn cart loot off from the Config button on the Mods screen. Cloth Config is used when installed.
- 🧩 **Addon API:** Other mods can add their own contraptions with `RegisterCartLootEvent` and `CartLootAPI`, using only the small API jar.

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

## 📷 MEDIA

![The 7x7 drill cart template](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/media-0-drill-cart.png)

![Explore, loot and deploy](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/media-1-how-it-works.png)

![Default drop chances per chest family](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/media-2-drop-chances.png)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

## 📦 INSTALLATION

**⏩ INSTALL ON BOTH CLIENT AND SERVER (NEOFORGE 1.21.1)**

**⏩ INSTALL BOTH JARS: CREATECARTLOOT-API AND CREATE-CART-LOOT-CORE**

**⏩ REQUIRES [CREATE](https://www.curseforge.com/minecraft/mc-mods/create) 6.0.10 OR NEWER**

**⏩ OPTIONAL: [CLOTH CONFIG](https://www.curseforge.com/minecraft/mc-mods/cloth-config) 15 OR NEWER, FOR A NICER CONFIG SCREEN**

The API jar and the core jar must be the same version. The example-addon jar is only for developers.

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

## ⚙️ CONFIGURATION

Settings live in `config/createcartloot-common.toml` and can also be changed in game from the Config button on the Mods screen:

- `general.enabled`: master switch for cart loot.
- `chests.*`: turn each chest family on or off. Only `mineshaft` is on by default.
- `chances.*`: drop chance per chest family, from 0.0 to 1.0. `mineshaft` is 0.20, everything else 0.0.
- `general.allowAddonOverrides`: let addons replace templates that are already registered (off by default).

In 0.1.3 only abandoned mineshaft chests are mapped to the drill cart. Turning on another chest family has no effect until an addon maps that chest with `CartLootAPI.mapChestToRarity`.

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

Renzo is actively working on Create Cart Loot and welcomes feedback, bug reports and ideas in the comments. 💬

| [![Disassembly Delight](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/card-disassembly-delight.png)](https://www.curseforge.com/minecraft/mc-mods/disassembly-delight) | [![Farmer's Delight Tweaks](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/card-farmers-delight-tweaks.png)](https://www.curseforge.com/minecraft/mc-mods/farmers-delight-tweaks) |
|:---:|:---:|
| [![Sophisticated Advanced Crafting](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/card-sophisticated-advanced-crafting.png)](https://www.curseforge.com/minecraft/mc-mods/sophisticated-advanced-crafting) | [![Enhanced Mob Spawners NeoForge](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/card-enhanced-mob-spawners-neoforge.png)](https://www.curseforge.com/minecraft/mc-mods/enhanced-mob-spawners-neoforge) |
| [![Mo' Enchantments Loot Compat](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/card-mo-enchantments-loot-compat.png)](https://www.curseforge.com/minecraft/mc-mods/mo-enchantments-loot-compat) |   |

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

[![Made by Renzo](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/footer-renzo.png)](https://github.com/Renzolc)

![divider](https://raw.githubusercontent.com/Renzolc/disassembly-delight/main/curseforge/redesign/create-cart-loot/divider-create-cart-loot.png)

© 2026 Renzo. Released under the MIT License.

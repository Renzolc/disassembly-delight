# 1.0.0

First release of Disassembly Delight, by Renzo. It takes over the Decrafter and the Decrafter Upgrade from Farmer's Delight Tweaks, renamed.

## Migrating from Farmer's Delight Tweaks (important)

- The **Decrafter** is now the **Disassembly Table** (`disassembly_delight:disassembler`) and the **Decrafter Upgrade** is now the **Disassembly Table Upgrade** (`disassembly_delight:disassembler_upgrade`).
- **Install this mod together with Farmer's Delight Tweaks 1.1.0 before opening an existing world.** Old ids are mapped with registry aliases: placed Decrafters load as Disassembly Tables with their inventory, Decrafter items become Disassembly Table items, and Decrafter Upgrades in backpacks keep their input slot. `fd_storage_compat:full_uncraft` recipes from datapacks keep working.
- **Without this mod, Farmer's Delight Tweaks 1.1.0 deletes every Decrafter and Decrafter Upgrade in a world when it loads.** Back up first.
- This mod does not load next to Farmer's Delight Tweaks 1.0.x. Update both together.

## Contents

- Disassembly Table: hopper-fed auto cutting board with planks/slabs/beds/mob heads, all Farmer's Delight cutting-board recipes, and reverse crafting. Items it cannot disassemble pass through.
- Disassembly Table Upgrade (Sophisticated Backpacks, optional): full ingredient counts of any crafting recipe into the backpack. Crafted in a plus shape: leather on the four sides, the Disassembly Table in the center.
- Containers are emptied before they are disassembled (shulker boxes, bundles, backpacks, Sophisticated storage, Create toolboxes and packages, Supplementaries containers, cooking pots...). Anything that cannot be emptied safely passes through whole.
- `disassembly_delight:full_uncraft` recipe type and the full set of bundled full_uncraft recipes.
- Farmer's Delight is required. Farmer's Delight Tweaks is not.
- Tests: JUnit (contents reader, migration aliases), GameTests (container emptying, legacy Decrafter loading and working), container coverage check.

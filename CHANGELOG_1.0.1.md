# 1.0.1

By Renzo.

## Backpacks come down one tier at a time

The Disassembly Table and the Disassembly Table Upgrade now take a Sophisticated backpack down one tier, and everything stored in it moves into the lower backpack:

- Emerald backpack (Sophisticated Emerald Upgrade) → netherite backpack + 1 block of emerald + 1 emerald upgrade template
- Netherite → diamond backpack + 1 netherite ingot (no smithing template)
- Diamond → gold backpack + 8 gold ingots
- Gold → iron backpack + 8 gold ingots
- Iron → copper backpack + 4 iron ingots (what the copper → iron recipe costs, so no free iron)
- Copper → backpack + 8 copper ingots
- Backpack → its contents and upgrades, then chest + 4 leather + 4 string

Inventory slots and upgrades that the smaller backpack cannot hold come out as overflow (stack upgrades and Inception are kept first). Settings, color and name carry over; the lower backpack gets a new storage id. The table buffers overflow that does not fit its outputs (saved with the block, dropped when broken) and waits until the buffer is empty. The upgrade puts everything into the host backpack and drops what does not fit; the host needs an Inception upgrade to hold the lower backpack. Before this, the upgrade did not accept backpacks at all and the table reverse-crafted them.

## Disassembly Table Upgrade

- New recipe (shaped): ` T ` / `HUH` / `RRR`, with T = Disassembly Table, H = hopper, U = Sophisticated Backpacks upgrade base, R = redstone. The leather plus-shape recipe is removed.
- A spare upgrade comes apart into exactly that recipe (1 Disassembly Table, 2 hoppers, 1 upgrade base, 3 redstone), in the upgrade and now also in the table block, which used to pass it through.
- The Disassembly Table item still passes through both unchanged and has no breakdown.

## Copper look

- New copper model and textures for the Disassembly Table: hopper on top, a window into the chamber and an output chute on the front, and a cog on the side. The front faces you when placed.
- Copper sound and map color. It still breaks by hand and drops itself; pickaxe or axe mine it faster.

## Fixes

- Bundled full_uncraft recipes that failed to load because they referenced items from mods that are not installed: Create doors, trapdoors, contraption controls, linked controller and track observer now give vanilla oak doors, trapdoors, buttons and pressure plates instead of Quark ones; the Create item vault and fluid tank give a vanilla barrel; Supplementaries candle holders that need Buzzier Bees, The Endergetic Expansion, Caverns & Chasms or Cave Enhancements only load when those mods are present.

## Tests

- New JUnit tests for the tier transfer, the recipe data and the block model; new GameTests for the backpack chain, the overflow buffer, the table and upgrade behaviors and the facing block.

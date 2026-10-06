# Disassembly Delight

A Farmer's Delight add-on for NeoForge 1.21.1 that takes things apart. By Renzo.

## Disassembly Table

A hopper-fed copper machine (hopper on top, window and chute on the front, a cog on the side) that works like an automatic cutting board with the tools built in. Feed it from the top or sides, pull results from the bottom, or right-click it for a GUI.

- Planks → slabs, slabs → sticks
- Beds → wool + planks
- Mob heads → spawn eggs
- Every Farmer's Delight cutting-board recipe, with all listed results
- Reverse crafting for everything else

Items it cannot disassemble pass straight through, so your hopper line never jams. The Disassembly Table item itself always passes through unchanged.

Recipe: cutting boards across the top and bottom rows, with a knife, an axe and a pickaxe in the middle row.

## Disassembly Table Upgrade (Sophisticated Backpacks)

Put an item in the upgrade's slot and the full ingredients of its crafting recipe go into your backpack. A spare upgrade comes apart into its recipe; the Disassembly Table moves in unchanged.

Recipe (shaped):

```
  T
H U H
R R R
```

T = Disassembly Table, H = hopper, U = Sophisticated Backpacks upgrade base, R = redstone dust.

## Backpacks come down one tier at a time

Both the table and the upgrade turn a backpack into the next-lower tier, with everything inside moved over:

- Emerald (Sophisticated Emerald Upgrade) → netherite backpack + block of emerald + emerald upgrade template
- Netherite → diamond backpack + 1 netherite ingot
- Diamond → gold backpack + 8 gold ingots
- Gold → iron backpack + 8 gold ingots
- Iron → copper backpack + 4 iron ingots
- Copper → backpack + 8 copper ingots
- Backpack → its contents and upgrades, then chest + 4 leather + 4 string

Items and upgrades that do not fit the smaller backpack come out separately; nothing is lost. The upgrade needs an Inception upgrade in your backpack to receive the lower-tier backpack.

## Nothing inside gets lost

Shulker boxes, bundles, backpacks, Sophisticated chests, Create toolboxes and packages, Supplementaries containers, cooking pots and more are emptied first. Anything that cannot be emptied safely (fluids, mobs, unknown data) passes through whole.

## Coming from Farmer's Delight Tweaks?

The Disassembly Table is the old **Decrafter**, and the Disassembly Table Upgrade is the old **Decrafter Upgrade**. **Install Disassembly Delight together with Farmer's Delight Tweaks 1.1.0 before opening your world.** Your Decrafters, their contents and your upgrades carry over automatically. Without this mod, Farmer's Delight Tweaks 1.1.0 removes them. Back up first.

## Requirements

- Farmer's Delight (required)
- Sophisticated Backpacks (optional, for the upgrade)
- Farmer's Delight Tweaks is **not** required

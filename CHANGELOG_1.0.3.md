# Disassembly Delight 1.0.3

By Renzo. Fixes the Disassembly Table Upgrade putting items back into the backpack whole instead of taking them apart.

## What was wrong

- **Shift-clicked items never reached the upgrade.** With the upgrade tab open, shift-clicking an item from your inventory put it straight into the backpack's storage. Sophisticated Backpacks only tries an open tab after the storage (unless "shift-click into open tab first" is turned on in the backpack settings), so the upgrade never saw the item and it landed in the backpack unchanged.
- **The upgrade only knew crafting recipes.** It reversed crafting and smithing recipes and this mod's `full_uncraft` recipes, but not the Disassembly Table's other rules. Logs, raw meat and fish, flowers, pies and cakes, single planks and slabs, mob heads and every other item that only the cutting board takes apart counted as "can't be disassembled" and went into the backpack unchanged.

## Fixes

- With the Disassembly Table Upgrade tab open, shift-clicking an item from your inventory now sends it into the upgrade, which takes it apart into the backpack. This works the same as Sophisticated Backpacks' "shift-click into open tab first" setting, but only for this tab. Upgrade items still install or go into storage as before, and every other tab is unchanged.
- The upgrade now uses the Disassembly Table's rules when an item has no full return: planks → 2 slabs, wooden slabs → 1 stick, beds → 3 wool + 3 oak planks, mob heads → their spawn egg, and every Farmer's Delight cutting-board recipe (all listed results, no chance rolls). Full returns still come first: `full_uncraft` recipes and the reverse of the crafting recipe (for example, 4 oak planks → 1 oak log, an iron pickaxe → 3 iron ingots + 2 sticks).
- Stored contents still come out first, backpacks still come down one tier (a diamond backpack → a gold backpack + 8 diamonds), and the Disassembly Table and items with no breakdown at all still go into the backpack unchanged.
- An item that goes into the backpack unchanged is now logged at debug level (`debug.log`), with its id, so a report can show which item had no breakdown.

## Unchanged

- The Disassembly Table block behaves exactly as in 1.0.2. Its rules moved into a shared class (`TableRules`) used by both the table and the upgrade.

## Tests

- New GameTests (`UpgradeBreakdownGameTests`) run the upgrade the way the game does: the upgrade is installed in a backpack, Sophisticated Core builds it, and an item is put in its slot, clicked in through the backpack menu, or shift-clicked from a player's inventory. They cover an iron pickaxe (new and worn), a chest (empty and filled), raw beef and an oak log (cutting board), single planks (slab rule), 4 planks (crafting), a zombie head, a diamond knife (`full_uncraft`), a diamond backpack (tier chain), dirt and the Disassembly Table (unchanged), shift-click with the tab open and closed, and shift-clicking an upgrade with the tab open.

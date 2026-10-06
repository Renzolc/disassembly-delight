package dev.renzo.disassemblydelight.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.renzo.disassemblydelight.DisassemblyDelight;
import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.blockentity.DisassemblerBlockEntity;
import dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeWrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;

/**
 * The Sophisticated Backpacks breakdown chain in a real world, in the Disassembly Table and the Disassembly Table
 * Upgrade: each tier comes down one step with its contents carried over, overflow comes out, nothing is lost.
 * Also the Disassembly Table passing through whole and the upgrade item coming apart into its recipe.
 * Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(DisassemblyDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public class BackpackChainGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) {
            throw new IllegalStateException("missing item " + id);
        }
        return item;
    }

    private static Item sb(String path) {
        return item("sophisticatedbackpacks:" + path);
    }

    private static DisassemblerBlockEntity placeTable(GameTestHelper helper, ItemStack input) {
        helper.setBlock(POS, ModBlocks.DISASSEMBLER.get());
        DisassemblerBlockEntity be = helper.getBlockEntity(POS);
        be.getItems().setStackInSlot(DisassemblerBlockEntity.INPUT_SLOT, input);
        return be;
    }

    private static List<ItemStack> outputs(ItemStackHandler items) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 1; i < DisassemblerBlockEntity.TOTAL_SLOTS; i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                out.add(items.getStackInSlot(i));
            }
        }
        return out;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemStack> contents(IItemHandler handler) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                out.add(handler.getStackInSlot(i).copy());
            }
        }
        return out;
    }

    private static ItemStack find(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).findFirst().orElse(ItemStack.EMPTY);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void netheriteBackpackComesDownToDiamondWithItsContents(GameTestHelper helper) {
        ItemStack netherite = new ItemStack(sb("netherite_backpack"));
        IBackpackWrapper source = BackpackWrapper.fromStack(netherite);
        source.getInventoryHandler().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        source.getInventoryHandler().setStackInSlot(40, new ItemStack(Items.COBBLESTONE, 64));
        source.getInventoryHandler().setStackInSlot(115, new ItemStack(Items.EMERALD, 9)); // past diamond's 108 slots
        String[] ups = {"pickup_upgrade", "magnet_upgrade", "feeding_upgrade", "compacting_upgrade", "void_upgrade", "restock_upgrade", "deposit_upgrade"};
        for (int i = 0; i < ups.length; i++) {
            source.getUpgradeHandler().setStackInSlot(i, new ItemStack(sb(ups[i])));
        }
        DisassemblerBlockEntity be = placeTable(helper, netherite);
        ItemStackHandler items = be.getItems();
        helper.succeedWhen(() -> {
            helper.assertTrue(items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty(), "input not consumed yet");
            List<ItemStack> out = outputs(items);
            helper.assertTrue(count(out, sb("diamond_backpack")) == 1, "expected the diamond backpack, got " + out);
            helper.assertTrue(count(out, Items.NETHERITE_INGOT) == 1, "expected 1 netherite ingot, got " + out);
            helper.assertTrue(count(out, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) == 0, "no template in the chain, got " + out);
            helper.assertTrue(count(out, Items.EMERALD) == 9, "slot 115 does not exist in a diamond backpack, so it comes out: " + out);
            helper.assertTrue(count(out, Items.DIAMOND) == 0 && count(out, Items.COBBLESTONE) == 0, "kept items must stay inside: " + out);
            helper.assertTrue(count(out, sb("restock_upgrade")) == 1 && count(out, sb("deposit_upgrade")) == 1,
                    "diamond has 5 upgrade slots, so the last 2 upgrades come out: " + out);
            helper.assertTrue(be.getPending().isEmpty(), "everything fit the outputs");

            ItemStack diamond = find(out, sb("diamond_backpack"));
            helper.assertTrue(diamond.has(ModCoreDataComponents.STORAGE_UUID.get()), "the lower tier must carry a storage");
            helper.assertTrue(!diamond.get(ModCoreDataComponents.STORAGE_UUID.get()).equals(netherite.get(ModCoreDataComponents.STORAGE_UUID.get())),
                    "the lower tier gets its own storage");
            IBackpackWrapper lower = BackpackWrapper.fromStack(diamond.copy());
            helper.assertTrue(lower.getInventoryHandler().getSlots() == 108, "diamond backpack slots: " + lower.getInventoryHandler().getSlots());
            helper.assertTrue(lower.getInventoryHandler().getStackInSlot(0).is(Items.DIAMOND)
                    && lower.getInventoryHandler().getStackInSlot(0).getCount() == 7, "slot 0 must carry over");
            helper.assertTrue(lower.getInventoryHandler().getStackInSlot(40).is(Items.COBBLESTONE)
                    && lower.getInventoryHandler().getStackInSlot(40).getCount() == 64, "slot 40 must carry over");
            List<ItemStack> lowerUpgrades = contents(lower.getUpgradeHandler());
            helper.assertTrue(lowerUpgrades.size() == 5 && count(lowerUpgrades, sb("pickup_upgrade")) == 1 && count(lowerUpgrades, sb("void_upgrade")) == 1,
                    "the first 5 upgrades carry over, got " + lowerUpgrades);
            helper.assertTrue(contents(source.getInventoryHandler()).isEmpty() && contents(source.getUpgradeHandler()).isEmpty(),
                    "the old backpack's storage is emptied");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tierStepsMatchTheChain(GameTestHelper helper) {
        DisassemblerBlockEntity be = placeTable(helper, new ItemStack(sb("diamond_backpack")));
        ItemStackHandler items = be.getItems();
        helper.succeedWhen(() -> {
            List<ItemStack> out = outputs(items);
            helper.assertTrue(count(out, sb("gold_backpack")) == 1 && count(out, Items.DIAMOND) == 8 && out.size() == 2,
                    "diamond backpack -> gold backpack + 8 diamonds, got " + out);
            helper.assertTrue(!out.get(0).has(ModCoreDataComponents.STORAGE_UUID.get()), "an empty backpack needs no storage");
        });
    }

    @GameTest(template = "empty")
    public static void upgradePreviewFollowsTheChain(GameTestHelper helper) {
        var level = helper.getLevel();
        String[][] chain = {
                {"netherite_backpack", "diamond_backpack", "minecraft:netherite_ingot", "1"},
                {"diamond_backpack", "gold_backpack", "minecraft:diamond", "8"},
                {"gold_backpack", "iron_backpack", "minecraft:gold_ingot", "8"},
                {"iron_backpack", "copper_backpack", "minecraft:iron_ingot", "4"},
                {"copper_backpack", "backpack", "minecraft:copper_ingot", "8"},
        };
        for (String[] step : chain) {
            List<ItemStack> out = DisassemblerUpgradeWrapper.previewFor(level, new ItemStack(sb(step[0]))).orElse(List.of());
            helper.assertTrue(out.size() == 2 && count(out, sb(step[1])) == 1 && count(out, item(step[2])) == Integer.parseInt(step[3]),
                    step[0] + " -> " + step[1] + " + " + step[3] + " " + step[2] + ", got " + out);
        }
        List<ItemStack> bottom = DisassemblerUpgradeWrapper.previewFor(level, new ItemStack(sb("backpack"))).orElse(List.of());
        helper.assertTrue(count(bottom, Items.CHEST) == 1 && count(bottom, Items.LEATHER) == 4 && count(bottom, Items.STRING) == 4,
                "the empty regular backpack breaks into its crafting ingredients, got " + bottom);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fullRegularBackpackDumpsEverythingThroughThePendingBuffer(GameTestHelper helper) {
        ItemStack backpack = new ItemStack(sb("backpack"));
        IBackpackWrapper source = BackpackWrapper.fromStack(backpack);
        for (int i = 0; i < 27; i++) {
            source.getInventoryHandler().setStackInSlot(i, new ItemStack(i % 2 == 0 ? Items.IRON_INGOT : Items.REDSTONE, 64));
        }
        source.getUpgradeHandler().setStackInSlot(0, new ItemStack(sb("magnet_upgrade")));
        DisassemblerBlockEntity be = placeTable(helper, backpack);
        ItemStackHandler items = be.getItems();
        List<ItemStack> collected = new ArrayList<>();
        // Keep emptying the outputs, like a hopper below would, until everything came through.
        helper.onEachTick(() -> {
            for (int i = 1; i < DisassemblerBlockEntity.TOTAL_SLOTS; i++) {
                if (!items.getStackInSlot(i).isEmpty()) {
                    collected.add(items.getStackInSlot(i).copy());
                    items.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty(), "input not consumed yet");
            helper.assertTrue(be.getPending().isEmpty(), "pending not drained yet: " + be.getPending().size());
            helper.assertTrue(count(collected, Items.IRON_INGOT) == 14 * 64 && count(collected, Items.REDSTONE) == 13 * 64,
                    "every stored stack must come back, got " + count(collected, Items.IRON_INGOT) + " iron / " + count(collected, Items.REDSTONE) + " redstone");
            helper.assertTrue(count(collected, sb("magnet_upgrade")) == 1, "the installed upgrade comes back");
            helper.assertTrue(count(collected, Items.CHEST) == 1 && count(collected, Items.LEATHER) == 4 && count(collected, Items.STRING) == 4,
                    "then the backpack's own ingredients");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pendingItemsSurviveASaveAndDropWithTheBlock(GameTestHelper helper) {
        ItemStack backpack = new ItemStack(sb("backpack"));
        IBackpackWrapper source = BackpackWrapper.fromStack(backpack);
        for (int i = 0; i < 27; i++) {
            source.getInventoryHandler().setStackInSlot(i, new ItemStack(Items.GOLD_INGOT, 64));
        }
        DisassemblerBlockEntity be = placeTable(helper, backpack);
        helper.succeedWhen(() -> {
            helper.assertTrue(be.getItems().getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty(), "input not consumed yet");
            int inOutputs = count(outputs(be.getItems()), Items.GOLD_INGOT);
            int pending = count(be.getPending(), Items.GOLD_INGOT);
            helper.assertTrue(inOutputs + pending == 27 * 64, "outputs " + inOutputs + " + pending " + pending + " must be all 27 stacks");
            helper.assertTrue(pending > 0, "with only 9 output slots some gold must be pending");
            var saved = be.saveWithoutMetadata(helper.getLevel().registryAccess());
            DisassemblerBlockEntity copy = new DisassemblerBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.assertTrue(count(copy.getPending(), Items.GOLD_INGOT) == pending, "pending items must be saved");
            helper.assertTrue(count(be.getDrops(), Items.GOLD_INGOT) == 27 * 64, "breaking the block drops the pending items too");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void upgradeMovesContentsIntoTheLowerTierInsideTheHostBackpack(GameTestHelper helper) {
        // Host backpack with an Inception upgrade, so it may hold the backpack that comes out.
        ItemStack host = new ItemStack(sb("netherite_backpack"));
        IBackpackWrapper hostWrapper = BackpackWrapper.fromStack(host);
        hostWrapper.getInventoryHandler(); // gives the host its storage, so it has upgrade slots
        hostWrapper.getUpgradeHandler().setStackInSlot(0, new ItemStack(sb("inception_upgrade")));
        ItemStack upgradeStack = new ItemStack(item("disassembly_delight:disassembler_upgrade"));
        DisassemblerUpgradeWrapper upgrade = new DisassemblerUpgradeWrapper(hostWrapper, upgradeStack, stack -> {
        });

        ItemStack gold = new ItemStack(sb("gold_backpack"));
        IBackpackWrapper goldWrapper = BackpackWrapper.fromStack(gold);
        goldWrapper.getInventoryHandler().setStackInSlot(3, new ItemStack(Items.APPLE, 12));
        goldWrapper.getInventoryHandler().setStackInSlot(70, new ItemStack(Items.BREAD, 5)); // past iron's 54 slots
        goldWrapper.getUpgradeHandler().setStackInSlot(0, new ItemStack(sb("pickup_upgrade")));
        goldWrapper.getUpgradeHandler().setStackInSlot(1, new ItemStack(sb("magnet_upgrade")));
        goldWrapper.getUpgradeHandler().setStackInSlot(2, new ItemStack(sb("feeding_upgrade"))); // iron has 2 upgrade slots
        upgrade.getInventory().setStackInSlot(DisassemblerUpgradeWrapper.INPUT_SLOT, gold);

        helper.assertTrue(upgrade.process(helper.getLevel(), helper.absolutePos(POS)), "the upgrade should process the gold backpack");
        helper.assertTrue(upgrade.getInventory().getStackInSlot(DisassemblerUpgradeWrapper.INPUT_SLOT).isEmpty(), "input slot must clear");
        List<ItemStack> inHost = contents(hostWrapper.getInventoryHandler());
        helper.assertTrue(count(inHost, Items.GOLD_INGOT) == 8, "8 gold ingots into the host, got " + inHost);
        helper.assertTrue(count(inHost, Items.BREAD) == 5, "slot 70 overflow into the host, got " + inHost);
        helper.assertTrue(count(inHost, sb("feeding_upgrade")) == 1, "third upgrade overflow into the host, got " + inHost);
        helper.assertTrue(count(inHost, Items.APPLE) == 0, "apples stay in the iron backpack");
        ItemStack iron = find(inHost, sb("iron_backpack"));
        helper.assertTrue(!iron.isEmpty(), "the iron backpack must be in the host, got " + inHost);
        IBackpackWrapper ironWrapper = BackpackWrapper.fromStack(iron.copy());
        helper.assertTrue(ironWrapper.getInventoryHandler().getStackInSlot(3).is(Items.APPLE)
                && ironWrapper.getInventoryHandler().getStackInSlot(3).getCount() == 12, "apples carried over");
        List<ItemStack> ironUpgrades = contents(ironWrapper.getUpgradeHandler());
        helper.assertTrue(count(ironUpgrades, sb("pickup_upgrade")) == 1 && count(ironUpgrades, sb("magnet_upgrade")) == 1,
                "two upgrades carried over, got " + ironUpgrades);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void disassemblyTablePassesThroughTheTable(GameTestHelper helper) {
        ItemStack table = new ItemStack(ModBlocks.DISASSEMBLER.get().asItem(), 3);
        DisassemblerBlockEntity be = placeTable(helper, table.copy());
        helper.succeedWhen(() -> {
            List<ItemStack> out = outputs(be.getItems());
            helper.assertTrue(be.getItems().getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty()
                    && out.size() == 1 && ItemStack.isSameItemSameComponents(out.get(0), table) && out.get(0).getCount() == 3,
                    "the Disassembly Table must come out unchanged and alone, got " + out);
        });
    }

    @GameTest(template = "empty")
    public static void disassemblyTablePassesThroughTheUpgrade(GameTestHelper helper) {
        Optional<List<ItemStack>> out = DisassemblerUpgradeWrapper.previewFor(helper.getLevel(), new ItemStack(ModBlocks.DISASSEMBLER.get().asItem()));
        helper.assertTrue(out.isEmpty(), "the upgrade must move the Disassembly Table into the backpack unchanged, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void upgradeItemComesApartIntoItsRecipe(GameTestHelper helper) {
        Item upgradeItem = item("disassembly_delight:disassembler_upgrade");
        List<ItemStack> viaUpgrade = DisassemblerUpgradeWrapper.previewFor(helper.getLevel(), new ItemStack(upgradeItem)).orElse(List.of());
        helper.assertTrue(count(viaUpgrade, ModBlocks.DISASSEMBLER.get().asItem()) == 1 && count(viaUpgrade, Items.HOPPER) == 2
                        && count(viaUpgrade, sb("upgrade_base")) == 1 && count(viaUpgrade, Items.REDSTONE) == 3 && count(viaUpgrade, Items.LEATHER) == 0,
                "upgrade -> table + 2 hoppers + upgrade base + 3 redstone, got " + viaUpgrade);
        DisassemblerBlockEntity be = placeTable(helper, new ItemStack(upgradeItem));
        helper.succeedWhen(() -> {
            List<ItemStack> out = outputs(be.getItems());
            helper.assertTrue(be.getItems().getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty(), "input not consumed yet");
            helper.assertTrue(count(out, ModBlocks.DISASSEMBLER.get().asItem()) == 1 && count(out, Items.HOPPER) == 2
                            && count(out, sb("upgrade_base")) == 1 && count(out, Items.REDSTONE) == 3 && count(out, upgradeItem) == 0,
                    "the table must take the upgrade apart the same way, got " + out);
        });
    }
}

package dev.renzo.disassemblydelight.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.renzo.disassemblydelight.DisassemblyDelight;
import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeContainer;
import dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeWrapper;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;

/**
 * The Disassembly Table Upgrade through the real Sophisticated Backpacks path: the upgrade is installed in a host
 * backpack's upgrade slots, Sophisticated Core builds the wrapper, an item goes into the upgrade's input slot and the
 * upgrade ticks. What lands in the host backpack is checked. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(DisassemblyDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public class UpgradeBreakdownGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) {
            throw new IllegalStateException("missing item " + id);
        }
        return item;
    }

    private record Host(IBackpackWrapper backpack, DisassemblerUpgradeWrapper upgrade) {
    }

    /** A netherite backpack with an Inception upgrade (so it may hold backpacks) and the Disassembly Table Upgrade. */
    private static Host host(GameTestHelper helper) {
        ItemStack hostStack = new ItemStack(item("sophisticatedbackpacks:netherite_backpack"));
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(hostStack);
        wrapper.getInventoryHandler(); // gives the host its storage, so it has upgrade slots
        wrapper.getUpgradeHandler().setStackInSlot(0, new ItemStack(item("sophisticatedbackpacks:inception_upgrade")));
        wrapper.getUpgradeHandler().setStackInSlot(1, new ItemStack(item("disassembly_delight:disassembler_upgrade")));
        List<DisassemblerUpgradeWrapper> found = new ArrayList<>();
        for (ITickableUpgrade tickable : wrapper.getUpgradeHandler().getWrappersThatImplement(ITickableUpgrade.class)) {
            if (tickable instanceof DisassemblerUpgradeWrapper upgrade) {
                found.add(upgrade);
            }
        }
        helper.assertTrue(found.size() == 1, "the host must report one installed Disassembly Table Upgrade, got " + found.size());
        return new Host(wrapper, found.getFirst());
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

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** Puts the stack into the upgrade's input like the GUI slot does, runs the upgrade, returns the host's contents. */
    private static List<ItemStack> run(GameTestHelper helper, ItemStack input) {
        Host host = host(helper);
        DisassemblerUpgradeWrapper upgrade = host.upgrade();
        ItemStack rest = upgrade.getInventory().insertItem(DisassemblerUpgradeWrapper.INPUT_SLOT, input.copy(), false);
        helper.assertTrue(rest.isEmpty(), "the input slot must accept " + input + ", left " + rest);
        // Tick it the way Sophisticated Core does (cooldown aside), a few times so a slow path still finishes.
        for (int i = 0; i < 4 && !upgrade.getInventory().getStackInSlot(DisassemblerUpgradeWrapper.INPUT_SLOT).isEmpty(); i++) {
            upgrade.process(helper.getLevel(), helper.absolutePos(POS));
        }
        helper.assertTrue(upgrade.getInventory().getStackInSlot(DisassemblerUpgradeWrapper.INPUT_SLOT).isEmpty(),
                "the input slot must clear for " + input + ", still holds " + upgrade.getInventory().getStackInSlot(DisassemblerUpgradeWrapper.INPUT_SLOT));
        return contents(host.backpack().getInventoryHandler());
    }

    @GameTest(template = "empty")
    public static void upgradeBreaksDownAnIronPickaxe(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(count(out, Items.IRON_PICKAXE) == 0, "the pickaxe must not pass through whole, got " + out);
        helper.assertTrue(count(out, Items.IRON_INGOT) == 3 && count(out, Items.STICK) == 2,
                "iron pickaxe -> 3 iron ingots + 2 sticks, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeBreaksDownADamagedIronPickaxe(GameTestHelper helper) {
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(100);
        List<ItemStack> out = run(helper, worn);
        helper.assertTrue(count(out, Items.IRON_INGOT) == 3 && count(out, Items.STICK) == 2 && count(out, Items.IRON_PICKAXE) == 0,
                "a worn pickaxe still gives full returns in the upgrade, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeBreaksDownAChest(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(Items.CHEST));
        int planks = out.stream().filter(s -> s.is(ItemTags.PLANKS)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(count(out, Items.CHEST) == 0 && planks == 8, "chest -> 8 planks, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeEmptiesAFilledChestFirst(GameTestHelper helper) {
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE, 5))));
        List<ItemStack> out = run(helper, chest);
        int planks = out.stream().filter(s -> s.is(ItemTags.PLANKS)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(count(out, Items.CHEST) == 0 && planks == 8 && count(out, Items.APPLE) == 5,
                "filled chest -> its 5 apples + 8 planks, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeUsesFarmersDelightCuttingRecipes(GameTestHelper helper) {
        // Raw beef has no crafting recipe; only the cutting board takes it apart.
        List<ItemStack> out = run(helper, new ItemStack(Items.BEEF));
        helper.assertTrue(count(out, Items.BEEF) == 0 && count(out, item("farmersdelight:minced_beef")) == 2,
                "beef -> 2 minced beef (cutting board), got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeCutsALog(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(Items.OAK_LOG));
        helper.assertTrue(count(out, Items.OAK_LOG) == 0 && count(out, Items.STRIPPED_OAK_LOG) == 1 && count(out, item("farmersdelight:tree_bark")) == 1,
                "oak log -> stripped oak log + tree bark (cutting board), got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeTurnsAMobHeadIntoItsSpawnEgg(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(Items.ZOMBIE_HEAD));
        helper.assertTrue(count(out, Items.ZOMBIE_HEAD) == 0 && count(out, Items.ZOMBIE_SPAWN_EGG) == 1, "zombie head -> zombie spawn egg, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeStillReverseCraftsAFullStackOfPlanks(GameTestHelper helper) {
        // Enough planks for the crafting recipe: full returns (4 planks -> 1 log), not the table's slab rule.
        List<ItemStack> out = run(helper, new ItemStack(Items.OAK_PLANKS, 4));
        helper.assertTrue(count(out, Items.OAK_PLANKS) == 0 && count(out, Items.OAK_SLAB) == 0 && out.stream().filter(s -> s.is(ItemTags.LOGS)).mapToInt(ItemStack::getCount).sum() == 1,
                "4 oak planks -> 1 log, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeUsesWoodRules(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(Items.OAK_PLANKS));
        helper.assertTrue(count(out, Items.OAK_PLANKS) == 0 && count(out, Items.OAK_SLAB) == 2, "oak planks -> 2 oak slabs, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeUsesFullUncraftRecipes(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(item("farmersdelight:diamond_knife")));
        helper.assertTrue(count(out, item("farmersdelight:diamond_knife")) == 0 && count(out, Items.DIAMOND) == 1 && count(out, Items.STICK) == 1,
                "diamond knife -> 1 diamond + 1 stick (full_uncraft), got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeTakesABackpackDownOneTier(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(item("sophisticatedbackpacks:diamond_backpack")));
        helper.assertTrue(count(out, item("sophisticatedbackpacks:diamond_backpack")) == 0
                        && count(out, item("sophisticatedbackpacks:gold_backpack")) == 1 && count(out, Items.DIAMOND) == 8,
                "diamond backpack -> gold backpack + 8 diamonds, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradePassesThroughWhatCannotComeApart(GameTestHelper helper) {
        List<ItemStack> out = run(helper, new ItemStack(Items.DIRT, 10));
        helper.assertTrue(out.size() == 1 && count(out, Items.DIRT) == 10, "dirt has no breakdown and moves in unchanged, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradePassesThroughTheDisassemblyTable(GameTestHelper helper) {
        Item table = ModBlocks.DISASSEMBLER.get().asItem();
        List<ItemStack> out = run(helper, new ItemStack(table));
        helper.assertTrue(out.size() == 1 && count(out, table) == 1, "the Disassembly Table moves in unchanged, got " + out);
        helper.succeed();
    }

    // ---- The backpack GUI: what a player actually does ----

    private record Gui(ServerPlayer player, BackpackContainer menu, DisassemblerUpgradeContainer tab) {
    }

    /** A fake player holding an iron backpack with the upgrade installed, with the backpack's menu open. */
    private static Gui openBackpack(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(java.util.UUID.randomUUID(), "dd_upgrade_test"));
        ItemStack hostStack = new ItemStack(item("sophisticatedbackpacks:iron_backpack"));
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(hostStack);
        wrapper.getInventoryHandler();
        wrapper.getUpgradeHandler().setStackInSlot(0, new ItemStack(item("disassembly_delight:disassembler_upgrade")));
        player.getInventory().items.set(0, hostStack);
        BackpackContainer menu = new BackpackContainer(1, player, new BackpackContext.Item("main", 0));
        player.containerMenu = menu;
        DisassemblerUpgradeContainer tab = null;
        for (UpgradeContainerBase<?, ?> container : menu.getUpgradeContainers().values()) {
            if (container instanceof DisassemblerUpgradeContainer found) {
                tab = found;
            }
        }
        helper.assertTrue(tab != null, "the backpack menu must contain the Disassembly Table Upgrade tab");
        return new Gui(player, menu, tab);
    }

    private static void openTab(Gui gui) {
        gui.menu().getStorageWrapper().setOpenTabId(gui.tab().getUpgradeContainerId());
        gui.tab().setIsOpen(true);
    }

    private static int playerSlot(Gui gui, int inventorySlot) {
        for (Slot slot : gui.menu().slots) {
            if (slot.container == gui.player().getInventory() && slot.getContainerSlot() == inventorySlot) {
                return slot.index;
            }
        }
        throw new IllegalStateException("no menu slot for player inventory slot " + inventorySlot);
    }

    @GameTest(template = "empty")
    public static void clickingAnItemIntoTheUpgradeSlotBreaksItDown(GameTestHelper helper) {
        Gui gui = openBackpack(helper);
        openTab(gui);
        gui.menu().setCarried(new ItemStack(Items.IRON_PICKAXE));
        gui.menu().clicked(gui.tab().getSlots().getFirst().index, 0, net.minecraft.world.inventory.ClickType.PICKUP, gui.player());
        List<ItemStack> out = contents(gui.menu().getStorageWrapper().getInventoryHandler());
        helper.assertTrue(gui.menu().getCarried().isEmpty() && count(out, Items.IRON_PICKAXE) == 0
                        && count(out, Items.IRON_INGOT) == 3 && count(out, Items.STICK) == 2,
                "a pickaxe clicked into the upgrade slot -> 3 iron ingots + 2 sticks in the backpack, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickingWithTheUpgradeTabOpenBreaksItDown(GameTestHelper helper) {
        Gui gui = openBackpack(helper);
        openTab(gui);
        gui.player().getInventory().items.set(9, new ItemStack(Items.IRON_PICKAXE));
        gui.menu().quickMoveStack(gui.player(), playerSlot(gui, 9));
        List<ItemStack> out = contents(gui.menu().getStorageWrapper().getInventoryHandler());
        helper.assertTrue(gui.player().getInventory().items.get(9).isEmpty(), "the pickaxe must leave the player's inventory");
        helper.assertTrue(count(out, Items.IRON_PICKAXE) == 0 && count(out, Items.IRON_INGOT) == 3 && count(out, Items.STICK) == 2,
                "with the Disassembly Table Upgrade tab open, a shift-clicked pickaxe must be taken apart, got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickingWithTheTabClosedStoresTheItemWhole(GameTestHelper helper) {
        Gui gui = openBackpack(helper);
        gui.menu().getStorageWrapper().removeOpenTabId();
        gui.player().getInventory().items.set(9, new ItemStack(Items.IRON_PICKAXE));
        gui.menu().quickMoveStack(gui.player(), playerSlot(gui, 9));
        List<ItemStack> out = contents(gui.menu().getStorageWrapper().getInventoryHandler());
        helper.assertTrue(count(out, Items.IRON_PICKAXE) == 1 && count(out, Items.IRON_INGOT) == 0,
                "with no tab open, shift-click keeps Sophisticated Backpacks' normal behavior (stored whole), got " + out);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickingAnUpgradeWithTheTabOpenDoesNotTakeItApart(GameTestHelper helper) {
        Gui gui = openBackpack(helper);
        openTab(gui);
        Item magnet = item("sophisticatedbackpacks:magnet_upgrade");
        gui.player().getInventory().items.set(9, new ItemStack(magnet));
        gui.menu().quickMoveStack(gui.player(), playerSlot(gui, 9));
        List<ItemStack> stored = contents(gui.menu().getStorageWrapper().getInventoryHandler());
        List<ItemStack> installed = contents(gui.menu().getStorageWrapper().getUpgradeHandler());
        helper.assertTrue(count(installed, magnet) + count(stored, magnet) == 1,
                "an upgrade shift-clicked with the tab open is installed or stored, never disassembled; installed " + installed + ", stored " + stored);
        helper.succeed();
    }
}

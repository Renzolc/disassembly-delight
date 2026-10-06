package dev.renzo.disassemblydelight.contents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Backpack tier breakdown: the lower tier keeps what it can hold, the rest comes out, and the item count never
 * changes. Stand-in items play the special upgrades so the rules are tested without Sophisticated Backpacks.
 */
class TierTransferTest {
    /** Nether star = stack upgrade x4, ghast tear = stack upgrade x2, ender eye = inception, shulker box = a backpack. */
    private static final TierTransfer.UpgradeKinds KINDS = new TierTransfer.UpgradeKinds(
            stack -> stack.is(Items.NETHER_STAR) ? OptionalDouble.of(4) : stack.is(Items.GHAST_TEAR) ? OptionalDouble.of(2) : OptionalDouble.empty(),
            stack -> stack.is(Items.ENDER_EYE),
            stack -> stack.is(Items.SHULKER_BOX));

    private static List<ItemStack> slots(int size, Object... slotAndStack) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(ItemStack.EMPTY);
        }
        for (int i = 0; i < slotAndStack.length; i += 2) {
            list.set((Integer) slotAndStack[i], (ItemStack) slotAndStack[i + 1]);
        }
        return list;
    }

    private static int total(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s != null && s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int total(TierTransfer.Split split, Item item) {
        return total(split.inventory(), item) + total(split.upgrades(), item) + total(split.overflow(), item);
    }

    @Test
    void everythingFitsKeepsSlotsAndUpgrades() {
        List<ItemStack> inv = slots(120, 0, new ItemStack(Items.DIAMOND, 7), 50, new ItemStack(Items.STONE, 64));
        List<ItemStack> ups = slots(7, 0, new ItemStack(Items.FEATHER), 3, new ItemStack(Items.STRING));
        TierTransfer.Split split = TierTransfer.split(inv, ups, 108, 5, KINDS);
        assertTrue(split.overflow().isEmpty(), () -> "nothing should overflow, got " + split.overflow());
        assertEquals(108, split.inventory().size());
        assertTrue(split.inventory().get(0).is(Items.DIAMOND) && split.inventory().get(0).getCount() == 7);
        assertTrue(split.inventory().get(50).is(Items.STONE) && split.inventory().get(50).getCount() == 64);
        assertEquals(2, split.upgrades().size());
        assertTrue(split.upgrades().get(0).is(Items.FEATHER) && split.upgrades().get(1).is(Items.STRING), "kept upgrades keep their order");
        assertTrue(split.keepsAnything());
    }

    @Test
    void slotsPastTheLowerTierComeOut() {
        // Netherite (120) -> diamond (108): slots 108..119 have nowhere to go.
        List<ItemStack> inv = slots(120, 5, new ItemStack(Items.COAL, 10), 108, new ItemStack(Items.IRON_INGOT, 64), 119, new ItemStack(Items.GOLD_INGOT, 3));
        TierTransfer.Split split = TierTransfer.split(inv, slots(7), 108, 5, KINDS);
        assertEquals(10, total(split.inventory(), Items.COAL));
        assertEquals(64, total(split.overflow(), Items.IRON_INGOT));
        assertEquals(3, total(split.overflow(), Items.GOLD_INGOT));
        assertEquals(0, total(split.inventory(), Items.IRON_INGOT));
    }

    @Test
    void extraUpgradesComeOutAndStackUpgradesStay() {
        // 7 upgrades into 5 slots: the stack upgrade and inception stay even though they sit in the last slots.
        List<ItemStack> ups = slots(7,
                0, new ItemStack(Items.FEATHER), 1, new ItemStack(Items.STRING), 2, new ItemStack(Items.BONE),
                3, new ItemStack(Items.SLIME_BALL), 4, new ItemStack(Items.FLINT),
                5, new ItemStack(Items.ENDER_EYE), 6, new ItemStack(Items.NETHER_STAR));
        TierTransfer.Split split = TierTransfer.split(slots(120), ups, 108, 5, KINDS);
        assertEquals(5, split.upgrades().size());
        assertEquals(1, total(split.upgrades(), Items.NETHER_STAR));
        assertEquals(1, total(split.upgrades(), Items.ENDER_EYE));
        assertEquals(2, split.overflow().size(), () -> "two upgrades should come out, got " + split.overflow());
        assertEquals(1, total(split.overflow(), Items.SLIME_BALL));
        assertEquals(1, total(split.overflow(), Items.FLINT));
        // Kept upgrades stay in their original relative order.
        assertTrue(split.upgrades().get(0).is(Items.FEATHER) && split.upgrades().get(4).is(Items.NETHER_STAR));
    }

    @Test
    void losingAStackUpgradeCapsTheStacks() {
        // A x4 and a x2 stack upgrade (x8) into a tier with one upgrade slot: the x4 stays, so slots hold 256.
        List<ItemStack> ups = slots(2, 0, new ItemStack(Items.GHAST_TEAR), 1, new ItemStack(Items.NETHER_STAR));
        List<ItemStack> inv = slots(54, 0, new ItemStack(Items.COBBLESTONE, 512), 1, new ItemStack(Items.ENDER_PEARL, 100), 2, new ItemStack(Items.DIRT, 200));
        TierTransfer.Split split = TierTransfer.split(inv, ups, 45, 1, KINDS);
        assertEquals(1, split.upgrades().size());
        assertTrue(split.upgrades().get(0).is(Items.NETHER_STAR));
        assertEquals(256, split.inventory().get(0).getCount());
        assertEquals(64, split.inventory().get(1).getCount(), "ender pearls stack to 16, x4 = 64");
        assertEquals(200, split.inventory().get(2).getCount(), "already under the new limit");
        assertEquals(256, total(split.overflow(), Items.COBBLESTONE));
        assertEquals(36, total(split.overflow(), Items.ENDER_PEARL));
        assertTrue(split.overflow().stream().allMatch(s -> s.getCount() <= s.getMaxStackSize()), "overflow is split into normal stacks");
        assertEquals(512, total(split, Items.COBBLESTONE));
        assertEquals(100, total(split, Items.ENDER_PEARL));
        assertEquals(1, total(split, Items.GHAST_TEAR));
    }

    @Test
    void nestedBackpackNeedsInception() {
        ItemStack nested = new ItemStack(Items.SHULKER_BOX);
        List<ItemStack> withInception = slots(3, 0, new ItemStack(Items.ENDER_EYE));
        TierTransfer.Split kept = TierTransfer.split(slots(27, 4, nested), withInception, 27, 1, KINDS);
        assertEquals(1, total(kept.inventory(), Items.SHULKER_BOX), "inception stays, so the nested backpack stays");

        List<ItemStack> inceptionLost = slots(3, 0, new ItemStack(Items.NETHER_STAR), 1, new ItemStack(Items.ENDER_EYE));
        TierTransfer.Split moved = TierTransfer.split(slots(27, 4, nested), inceptionLost, 27, 1, KINDS);
        assertEquals(0, total(moved.inventory(), Items.SHULKER_BOX));
        assertEquals(1, total(moved.overflow(), Items.SHULKER_BOX), "the nested backpack comes out whole");
        assertEquals(1, total(moved.overflow(), Items.ENDER_EYE));
    }

    @Test
    void emptyBackpackKeepsNothing() {
        TierTransfer.Split split = TierTransfer.split(slots(81), slots(3), 54, 2, KINDS);
        assertFalse(split.keepsAnything());
        assertTrue(split.overflow().isEmpty());
    }

    @Test
    void nothingIsEverLost() {
        List<ItemStack> inv = new ArrayList<>();
        for (int i = 0; i < 132; i++) {
            inv.add(new ItemStack(i % 3 == 0 ? Items.IRON_INGOT : i % 3 == 1 ? Items.REDSTONE : Items.SHULKER_BOX, i % 3 == 2 ? 1 : 64));
        }
        List<ItemStack> ups = slots(8, 0, new ItemStack(Items.ENDER_EYE), 1, new ItemStack(Items.NETHER_STAR), 2, new ItemStack(Items.FEATHER),
                3, new ItemStack(Items.BONE), 4, new ItemStack(Items.FLINT), 5, new ItemStack(Items.STRING), 6, new ItemStack(Items.SLIME_BALL), 7, new ItemStack(Items.GHAST_TEAR));
        // Emerald (132 / 8) -> netherite (120 / 7) -> ... -> regular (27 / 1), feeding each kept result into the next.
        int[][] tiers = {{120, 7}, {108, 5}, {81, 3}, {54, 2}, {45, 1}, {27, 1}};
        List<ItemStack> out = new ArrayList<>();
        for (int[] tier : tiers) {
            TierTransfer.Split split = TierTransfer.split(inv, ups, tier[0], tier[1], KINDS);
            out.addAll(split.overflow());
            inv = split.inventory();
            ups = split.upgrades();
        }
        List<ItemStack> all = new ArrayList<>(out);
        all.addAll(inv);
        all.addAll(ups);
        assertEquals(44 * 64, total(all, Items.IRON_INGOT));
        assertEquals(44 * 64, total(all, Items.REDSTONE));
        assertEquals(44, total(all, Items.SHULKER_BOX));
        for (Item up : List.of(Items.ENDER_EYE, Items.NETHER_STAR, Items.FEATHER, Items.BONE, Items.FLINT, Items.STRING, Items.SLIME_BALL, Items.GHAST_TEAR)) {
            assertEquals(1, total(all, up), "upgrade " + up + " must survive the whole chain");
        }
        assertEquals(1, ups.size());
        assertTrue(ups.get(0).is(Items.GHAST_TEAR) || ups.get(0).is(Items.NETHER_STAR), "a stack upgrade is the last one kept");
    }
}

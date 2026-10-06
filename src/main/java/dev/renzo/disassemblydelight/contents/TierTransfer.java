package dev.renzo.disassemblydelight.contents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;

/**
 * Decides what a backpack's contents look like after it is taken down one tier (netherite to diamond, gold to iron...).
 * This is the reverse of a Sophisticated Backpacks tier upgrade: the contents and upgrades move into the lower-tier
 * backpack, and whatever the lower tier has no room for comes out as separate items. Nothing is ever dropped.
 *
 * <p>Pure logic with no Sophisticated Backpacks classes, so it is unit tested directly. The rules:
 * <ul>
 * <li>Upgrades: the lower tier keeps as many as it has upgrade slots. Stack upgrades are kept first, biggest first (they set how
 * much a slot holds), then the Inception upgrade (it is what lets backpacks sit inside a backpack), then the rest in slot
 * order. Kept upgrades stay in their original relative order. The others are overflow.</li>
 * <li>Inventory: slot {@code i} stays in slot {@code i} when the lower tier has that slot, otherwise it is overflow.</li>
 * <li>If a stack upgrade had to come out, every kept stack is capped to what a slot holds with the stack upgrades that
 * stayed (max stack size times their multiplier) and the rest is overflow.</li>
 * <li>A backpack inside stays only when the Inception upgrade stays.</li>
 * <li>Overflow is upgrades first, then items, each split into normal stack sizes.</li>
 * </ul>
 */
public final class TierTransfer {
    private TierTransfer() {
    }

    /**
     * @param inventory    one entry per lower-tier inventory slot, {@code null} or empty for an empty slot
     * @param upgrades     one entry per lower-tier upgrade slot (same order as the stacks were in the lower tier)
     * @param overflow     items that come out next to the lower-tier backpack
     */
    public record Split(List<ItemStack> inventory, List<ItemStack> upgrades, List<ItemStack> overflow) {
        public boolean keepsAnything() {
            return inventory.stream().anyMatch(s -> s != null && !s.isEmpty()) || upgrades.stream().anyMatch(s -> s != null && !s.isEmpty());
        }
    }

    /** How the caller recognises the special upgrades. */
    public record UpgradeKinds(Function<ItemStack, OptionalDouble> stackMultiplier, Predicate<ItemStack> isInception,
                               Predicate<ItemStack> isBackpack) {
    }

    /**
     * @param inventory    the higher tier's inventory, one entry per slot (empty stacks allowed)
     * @param upgrades     the higher tier's upgrade slots (empty stacks allowed)
     * @param targetSlots  inventory slots of the lower tier
     * @param targetUpgradeSlots upgrade slots of the lower tier
     */
    public static Split split(List<ItemStack> inventory, List<ItemStack> upgrades, int targetSlots, int targetUpgradeSlots, UpgradeKinds kinds) {
        List<ItemStack> overflowUpgrades = new ArrayList<>();
        List<ItemStack> overflowItems = new ArrayList<>();

        // Upgrades, with their original slot index so the kept ones keep their order.
        List<Integer> present = new ArrayList<>();
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack up = upgrades.get(i);
            if (up != null && !up.isEmpty()) {
                present.add(i);
            }
        }
        List<Integer> byPriority = new ArrayList<>(present);
        byPriority.sort((a, b) -> {
            int pa = priority(upgrades.get(a), kinds);
            int pb = priority(upgrades.get(b), kinds);
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
            if (pa == 0) {
                // Bigger stack upgrades first, so the lower tier keeps as much per-slot room as it can.
                int byMultiplier = Double.compare(kinds.stackMultiplier().apply(upgrades.get(b)).getAsDouble(),
                        kinds.stackMultiplier().apply(upgrades.get(a)).getAsDouble());
                if (byMultiplier != 0) {
                    return byMultiplier;
                }
            }
            return Integer.compare(a, b);
        });
        int keepCount = Math.max(0, Math.min(targetUpgradeSlots, byPriority.size()));
        List<Integer> kept = new ArrayList<>(byPriority.subList(0, keepCount));
        Collections.sort(kept);
        List<ItemStack> keptUpgrades = new ArrayList<>();
        for (int index : kept) {
            keptUpgrades.add(upgrades.get(index).copy());
        }
        for (int index : present) {
            if (!kept.contains(index)) {
                overflowUpgrades.add(upgrades.get(index).copy());
            }
        }

        double sourceMultiplier = multiplier(present.stream().map(upgrades::get).toList(), kinds);
        double keptMultiplier = multiplier(keptUpgrades, kinds);
        boolean capStacks = keptMultiplier < sourceMultiplier - 1e-9;
        boolean keptInception = keptUpgrades.stream().anyMatch(kinds.isInception());

        List<ItemStack> keptInventory = new ArrayList<>();
        for (int slot = 0; slot < Math.max(0, targetSlots); slot++) {
            keptInventory.add(ItemStack.EMPTY);
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            stack = stack.copy();
            if (slot >= targetSlots || (kinds.isBackpack().test(stack) && !keptInception)) {
                overflowItems.add(stack);
                continue;
            }
            if (capStacks) {
                int cap = (int) Math.max(1, Math.floor(stack.getMaxStackSize() * keptMultiplier));
                if (stack.getCount() > cap) {
                    overflowItems.add(stack.copyWithCount(stack.getCount() - cap));
                    stack.setCount(cap);
                }
            }
            keptInventory.set(slot, stack);
        }

        List<ItemStack> overflow = new ArrayList<>(ContainerDisassembly.splitToStackSize(overflowUpgrades));
        overflow.addAll(ContainerDisassembly.splitToStackSize(overflowItems));
        return new Split(keptInventory, keptUpgrades, overflow);
    }

    private static int priority(ItemStack upgrade, UpgradeKinds kinds) {
        if (kinds.stackMultiplier().apply(upgrade).isPresent()) {
            return 0;
        }
        if (kinds.isInception().test(upgrade)) {
            return 1;
        }
        return 2;
    }

    private static double multiplier(List<ItemStack> upgrades, UpgradeKinds kinds) {
        double product = 1;
        for (ItemStack up : upgrades) {
            if (up == null || up.isEmpty()) {
                continue;
            }
            OptionalDouble m = kinds.stackMultiplier().apply(up);
            if (m.isPresent()) {
                product *= m.getAsDouble();
            }
        }
        return product;
    }
}

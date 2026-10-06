package dev.renzo.disassemblydelight.compat.sb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import dev.renzo.disassemblydelight.DisassemblyDelight;
import dev.renzo.disassemblydelight.contents.ContainerDisassembly;
import dev.renzo.disassemblydelight.contents.TierTransfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModDataComponents;
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.inception.InceptionUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointStackState;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageStackLifecycle;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.stack.StackUpgradeItem;

/**
 * Sophisticated Backpacks side of {@link dev.renzo.disassemblydelight.contents.BackpackBreakdown}. Only loaded when
 * Sophisticated Backpacks is.
 *
 * <p>Taking a backpack down a tier is Sophisticated Backpacks' own tier-upgrade recipe in reverse. The upgrade recipe
 * copies every data component of the old backpack onto the new one (so it keeps the storage UUID, and with it the
 * contents) and then raises the slot counts. Slot counts never go down in Sophisticated Backpacks, so the reverse
 * cannot reuse the UUID: the lower-tier backpack gets the same components (name, colours, enchantments...) except
 * the storage UUID, slot counts and taken columns, a fresh storage UUID, and the contents, upgrades and settings
 * are copied into it through Sophisticated Backpacks' handlers when the breakdown commits. Whatever the lower tier
 * cannot hold is listed as extra outputs ({@link TierTransfer}). The old backpack's storage is emptied afterwards.
 */
public final class BackpackChain {
    private static final TierTransfer.UpgradeKinds KINDS = new TierTransfer.UpgradeKinds(
            stack -> stack.getItem() instanceof StackUpgradeItem stackUpgrade
                    ? OptionalDouble.of(stackUpgrade.getStackSizeMultiplier())
                    : OptionalDouble.empty(),
            stack -> stack.getItem() instanceof InceptionUpgradeItem,
            stack -> stack.getItem() instanceof BackpackItem);

    private BackpackChain() {
    }

    public static TierTransfer.UpgradeKinds upgradeKinds() {
        return KINDS;
    }

    /**
     * @param base the backpack's own breakdown (lower tier + materials, or crafting ingredients at the bottom)
     */
    public static Optional<ContainerDisassembly.Plan> plan(Level level, ItemStack input, List<ItemStack> base) {
        ItemStack lower = null;
        List<ItemStack> materials = new ArrayList<>();
        for (ItemStack stack : base) {
            if (stack.getItem() instanceof BackpackItem && lower == null && stack.getCount() == 1) {
                lower = stack;
            } else {
                materials.add(stack.copy());
            }
        }
        if (lower == null) {
            // Bottom of the chain: full contents + upgrades, then the crafting ingredients. Same readers and guards
            // as every other container, but a big dump goes to the table's pending buffer instead of passing through.
            return ContainerDisassembly.plan(level, input, new ContainerDisassembly.Base(1, base))
                    .map(ContainerDisassembly.Plan::withOverflowAllowed);
        }
        return step(input, (BackpackItem) lower.getItem(), materials);
    }

    private static Optional<ContainerDisassembly.Plan> step(ItemStack input, BackpackItem lowerItem, List<ItemStack> materials) {
        if (input.getCount() != 1) {
            return Optional.empty();
        }
        // A linked endpoint points at another backpack's storage; taking it apart would take that backpack's items.
        if (LinkedStorageStackLifecycle.classifyEndpoint(input) == LinkedStorageEndpointStackState.ENDPOINT) {
            return Optional.empty();
        }
        IBackpackWrapper source = BackpackWrapper.fromStack(input);
        List<ItemStack> inventory = slots(source.getInventoryHandler());
        List<ItemStack> upgrades = slots(source.getUpgradeHandler());
        TierTransfer.Split split = TierTransfer.split(inventory, upgrades, lowerItem.getNumberOfSlots(), lowerItem.getNumberOfUpgradeSlots(), KINDS);

        ItemStack lower = lowerTierStack(input, lowerItem);
        boolean transfer = split.keepsAnything();
        if (transfer) {
            lower.set(ModCoreDataComponents.STORAGE_UUID, UUID.randomUUID());
        }
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(lower);
        outputs.addAll(materials);
        outputs.addAll(split.overflow());

        ItemStack target = lower.copy();
        return Optional.of(new ContainerDisassembly.Plan(1, outputs, () -> {
            List<ItemStack> leftovers = new ArrayList<>();
            if (transfer) {
                fill(source, target, split, leftovers);
            }
            clear(source.getInventoryHandler());
            clear(source.getUpgradeHandler());
            return leftovers;
        }, true));
    }

    /** The lower-tier item with the old backpack's components, minus what ties it to the old storage and tier. */
    static ItemStack lowerTierStack(ItemStack input, BackpackItem lowerItem) {
        ItemStack lower = new ItemStack(lowerItem);
        lower.applyComponents(input.getComponentsPatch());
        lower.remove(ModCoreDataComponents.STORAGE_UUID.get());
        lower.remove(ModCoreDataComponents.NUMBER_OF_INVENTORY_SLOTS.get());
        lower.remove(ModCoreDataComponents.NUMBER_OF_UPGRADE_SLOTS.get());
        lower.remove(ModDataComponents.COLUMNS_TAKEN.get());
        lower.setCount(1);
        return lower;
    }

    private static void fill(IBackpackWrapper source, ItemStack targetStack, TierTransfer.Split split, List<ItemStack> leftovers) {
        IBackpackWrapper target = BackpackWrapper.fromStack(targetStack);
        // Upgrades first: stack upgrades set the slot limit the items are checked against.
        UpgradeHandler targetUpgrades = target.getUpgradeHandler();
        List<ItemStack> keptUpgrades = split.upgrades();
        for (int i = 0; i < keptUpgrades.size(); i++) {
            put(targetUpgrades, i, keptUpgrades.get(i), leftovers);
        }
        InventoryHandler targetInventory = target.getInventoryHandler();
        List<ItemStack> kept = split.inventory();
        for (int slot = 0; slot < kept.size(); slot++) {
            put(targetInventory, slot, kept.get(slot), leftovers);
        }
        try {
            source.getSettingsHandler().copyTo(target.getSettingsHandler());
        } catch (RuntimeException e) {
            // Settings (sorting, memory, display) are a convenience; the items are already safe.
            DisassemblyDelight.LOGGER.debug("Could not copy backpack settings to the lower tier", e);
        }
    }

    private static void put(IItemHandlerModifiable handler, int slot, ItemStack want, List<ItemStack> leftovers) {
        if (want == null || want.isEmpty()) {
            return;
        }
        if (slot >= handler.getSlots()) {
            leftovers.addAll(ContainerDisassembly.splitToStackSize(List.of(want.copy())));
            return;
        }
        try {
            handler.setStackInSlot(slot, want.copy());
        } catch (RuntimeException e) {
            leftovers.addAll(ContainerDisassembly.splitToStackSize(List.of(want.copy())));
            return;
        }
        ItemStack stored = handler.getStackInSlot(slot);
        if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, want)) {
            if (!stored.isEmpty()) {
                handler.setStackInSlot(slot, ItemStack.EMPTY);
            }
            leftovers.addAll(ContainerDisassembly.splitToStackSize(List.of(want.copy())));
        } else if (stored.getCount() < want.getCount()) {
            leftovers.addAll(ContainerDisassembly.splitToStackSize(List.of(want.copyWithCount(want.getCount() - stored.getCount()))));
        }
    }

    private static List<ItemStack> slots(IItemHandler handler) {
        List<ItemStack> out = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            out.add(handler.getStackInSlot(slot).copy());
        }
        return out;
    }

    private static void clear(IItemHandler handler) {
        if (!(handler instanceof IItemHandlerModifiable modifiable)) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}

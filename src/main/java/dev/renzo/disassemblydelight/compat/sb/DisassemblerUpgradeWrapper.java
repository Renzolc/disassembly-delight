package dev.renzo.disassemblydelight.compat.sb;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.contents.BackpackBreakdown;
import dev.renzo.disassemblydelight.contents.ContainerDisassembly;
import dev.renzo.disassemblydelight.recipe.CraftUncraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.StatefulComponentItemHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;

/**
 * One input slot. Any crafting recipe (or this mod's full_uncraft) is reversed into the backpack.
 * An item with no disassemble result is moved into the backpack unchanged.
 */
public class DisassemblerUpgradeWrapper extends UpgradeWrapperBase<DisassemblerUpgradeWrapper, DisassemblerUpgradeItem>
        implements ITickableUpgrade {
    public static final int INPUT_SLOT = 0;

    private final StatefulComponentItemHandler inventory;
    private boolean processing;

    public DisassemblerUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
        inventory = new StatefulComponentItemHandler(upgrade, ModCoreDataComponents.LENIENT_CONTAINER.get(), 1) {
            @Override
            protected void onContentsChanged(int slot, ItemStack oldStack, ItemStack newStack) {
                super.onContentsChanged(slot, oldStack, newStack);
                save();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // Sophisticated Core syncs every slot with setStackInSlot, including air to clear it.
                // setStackInSlot throws if isItemValid is false, so empty stacks must be accepted.
                // Backpacks cannot normally sit inside an item, but the input slot only holds one until the next
                // tick breaks it down a tier (its contents live in saved data, not on the stack).
                return stack.isEmpty() || super.isItemValid(slot, stack) || StoredContents.isBackpack(stack);
            }
        };
    }

    public StatefulComponentItemHandler getInventory() {
        return inventory;
    }

    @Override
    public boolean canBeDisabled() {
        return true;
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, BlockPos pos) {
        if (level.isClientSide || !isEnabled() || isInCooldown(level)) {
            return;
        }
        boolean worked = process(level, pos);
        setCooldown(level, worked ? 5 : 10);
    }

    public boolean process(Level level, @Nullable BlockPos pos) {
        if (processing || level == null || level.isClientSide || !isEnabled()) {
            return false;
        }
        processing = true;
        try {
            return depositIntoBackpack(level, pos);
        } catch (RuntimeException e) {
            // Runs inside the menu's broadcastChanges on the server tick; never let it take the world down.
            ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
            CraftUncraft.warnOnce("process:" + CraftUncraft.itemId(input),
                    "Disassembler Upgrade could not process " + CraftUncraft.itemId(input) + "; it was left in the input slot", e);
            return false;
        } finally {
            processing = false;
        }
    }

    private boolean depositIntoBackpack(Level level, @Nullable BlockPos pos) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            return false;
        }
        Optional<Resolved> resolved = resolve(level);
        if (resolved.isEmpty()) {
            // Nothing to disassemble: move the item into the backpack unchanged so the input slot clears.
            return passThrough(input);
        }
        Resolved op = resolved.get();
        if (input.getCount() < op.consume()) {
            return false;
        }
        IItemHandler backpack = storageWrapper.getInventoryForUpgradeProcessing();
        // Contents first, then craft ingredients. All of it fits, or the input stays.
        if (backpack == null || !insertAll(backpack, op.results())) {
            return false;
        }
        // Clears the disassembled container's storage and fills a lower-tier backpack. Anything it hands back
        // (normally nothing) goes into the backpack, and only what still does not fit is dropped. Never deleted.
        List<ItemStack> leftovers = op.commit().get();
        inventory.extractItem(INPUT_SLOT, op.consume(), false);
        storeOrDrop(level, pos, backpack, leftovers);
        return true;
    }

    private void storeOrDrop(Level level, @Nullable BlockPos pos, IItemHandler backpack, @Nullable List<ItemStack> leftovers) {
        if (leftovers == null) {
            return;
        }
        for (ItemStack stack : leftovers) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack rest = InventoryHelper.insertIntoInventory(stack.copy(), backpack, false);
            if (rest == null || rest.isEmpty()) {
                continue;
            }
            if (pos != null) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rest);
            } else {
                // No position to drop at: park it in the (now empty) input slot so it is not lost.
                ItemStack parked = inventory.insertItem(INPUT_SLOT, rest, false);
                if (!parked.isEmpty()) {
                    dev.renzo.disassemblydelight.DisassemblyDelight.LOGGER.error("Disassembly Table Upgrade could not place {}", parked);
                }
            }
        }
    }

    /**
     * Moves an item with no disassemble result into the backpack as is. Whatever does not fit stays in the input.
     */
    private boolean passThrough(ItemStack input) {
        IItemHandler backpack = storageWrapper.getInventoryForUpgradeProcessing();
        if (backpack == null) {
            return false;
        }
        ItemStack leftover = InventoryHelper.insertIntoInventory(input.copy(), backpack, false);
        int moved = input.getCount() - (leftover == null ? 0 : leftover.getCount());
        if (moved <= 0) {
            return false;
        }
        inventory.extractItem(INPUT_SLOT, moved, false);
        return true;
    }

    /**
     * Commits through the backpack's own inserter. If any result does not fit, the inventory is restored
     * and the input is left alone.
     */
    private static boolean insertAll(IItemHandler backpack, List<ItemStack> results) {
        if (!(backpack instanceof IItemHandlerModifiable modifiable)) {
            return false;
        }
        ItemStack[] before = new ItemStack[backpack.getSlots()];
        for (int i = 0; i < before.length; i++) {
            before[i] = backpack.getStackInSlot(i).copy();
        }
        boolean fitted = true;
        for (ItemStack stack : results) {
            ItemStack leftover = InventoryHelper.insertIntoInventory(stack.copy(), backpack, false);
            if (!leftover.isEmpty()) {
                fitted = false;
                break;
            }
        }
        if (!fitted) {
            for (int i = 0; i < before.length; i++) {
                modifiable.setStackInSlot(i, before[i]);
            }
        }
        return fitted;
    }

    private Optional<Resolved> resolve(Level level) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        return resolveStack(level, input);
    }

    /** Client preview of what the next uncraft will insert. Not a container slot. */
    public List<ItemStack> preview(Level level) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        try {
            return resolveStack(level, input).map(Resolved::results).orElse(List.of());
        } catch (RuntimeException e) {
            CraftUncraft.warnOnce("preview:" + CraftUncraft.itemId(input),
                    "Disassembler Upgrade preview failed for " + CraftUncraft.itemId(input), e);
            return List.of();
        }
    }

    /**
     * Disassemble result plus the input's stored contents (shared with the Disassembler block). An input whose contents
     * cannot be read resolves to nothing, so it is moved into the backpack unchanged.
     */
    private static Optional<Resolved> resolveStack(Level level, ItemStack input) {
        if (input.isEmpty() || level == null) {
            return Optional.empty();
        }
        // The Disassembly Table itself never comes apart: it moves into the backpack unchanged.
        if (input.is(ModBlocks.DISASSEMBLER.asItem())) {
            return Optional.empty();
        }
        if (BackpackBreakdown.isBackpack(input)) {
            return BackpackBreakdown.plan(level, input)
                    .map(plan -> new Resolved(plan.consume(), plan.outputs(), plan.commit()));
        }
        ContainerDisassembly.Base base = CraftUncraft.resolve(level, input)
                .map(crafted -> new ContainerDisassembly.Base(crafted.consume(), crafted.results()))
                .orElse(null);
        return ContainerDisassembly.plan(level, input, base)
                .map(plan -> new Resolved(plan.consume(), plan.outputs(), plan.commit()));
    }

    /** Exposed for tests: what the upgrade would put into the backpack for this input (empty = moved unchanged). */
    public static Optional<List<ItemStack>> previewFor(Level level, ItemStack input) {
        return resolveStack(level, input).map(Resolved::results);
    }

    private record Resolved(int consume, List<ItemStack> results, java.util.function.Supplier<List<ItemStack>> commit) {}
}
